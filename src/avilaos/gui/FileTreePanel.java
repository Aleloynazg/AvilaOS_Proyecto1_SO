package avilaos.gui;

import avilaos.hardware.Computer;
import avilaos.hardware.disk.Disk;
import avilaos.kernel.fs.Directory;
import avilaos.kernel.fs.FileSystem;
import avilaos.kernel.fs.FsNode;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreePath;

/**
 * JTree con la estructura de directorios y archivos, y debajo la información
 * del elemento seleccionado (nombre, tamaño, dueño, permisos, primer bloque).
 * Solo observa el FileSystem.
 */
public class FileTreePanel extends JPanel {

    private final Computer computer;
    private final FsTreeModel model;
    private final JTree tree;
    private final JTextArea info = new JTextArea(9, 24);
    private String lastSignature = "";

    public FileTreePanel(Computer computer) {
        this.computer = computer;
        this.model = new FsTreeModel(computer.getFileSystem());
        this.tree = new JTree(model);

        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Sistema de archivos"));

        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new NodeRenderer());
        tree.addTreeSelectionListener(e -> showInfo());

        info.setEditable(false);
        info.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        info.setLineWrap(true);
        info.setWrapStyleWord(true);
        JScrollPane infoScroll = new JScrollPane(info);
        infoScroll.setBorder(BorderFactory.createTitledBorder("Elemento seleccionado"));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(tree), infoScroll);
        split.setResizeWeight(0.65);
        add(split, BorderLayout.CENTER);
        setPreferredSize(new Dimension(300, 400));

        refresh();
    }

    /** Ruta del elemento seleccionado, o null si no hay ninguno. */
    public String getSelectedPath() {
        FsNode node = getSelectedNode();
        return node == null ? null : model.pathOf(node);
    }

    /** Ruta del directorio donde crear: el seleccionado, o el padre del archivo seleccionado. */
    public String getSelectedDirectoryPath() {
        FsNode node = getSelectedNode();
        if (node == null) {
            return "/";
        }
        if (node instanceof Directory) {
            return model.pathOf(node);
        }
        return node.getParent() == null ? "/" : model.pathOf(node.getParent());
    }

    private FsNode getSelectedNode() {
        TreePath path = tree.getSelectionPath();
        if (path == null) {
            return null;
        }
        Object last = path.getLastPathComponent();
        return last instanceof FsNode ? (FsNode) last : null;
    }

    /** Se llama cada 200 ms: si el árbol cambió lo vuelve a dibujar y conserva la selección. */
    public void refresh() {
        try {
            String signature = signature(computer.getFileSystem().getRoot());
            if (!signature.equals(lastSignature)) {
                String selected = getSelectedPath();
                model.fireStructureChanged();
                for (int row = 0; row < tree.getRowCount(); row++) {
                    tree.expandRow(row);
                }
                if (selected != null) {
                    reselect(selected);
                }
                lastSignature = signature;
            }
            showInfo();
        } catch (RuntimeException ex) {
            // El reloj está cambiando el árbol en este instante: se intenta en el siguiente refresco.
        }
    }

    private void reselect(String path) {
        for (int row = 0; row < tree.getRowCount(); row++) {
            Object node = tree.getPathForRow(row).getLastPathComponent();
            if (node instanceof FsNode && path.equals(model.pathOf((FsNode) node))) {
                tree.setSelectionRow(row);
                return;
            }
        }
    }

    /** Texto que cambia si se agrega, borra, renombra o cambia de tamaño cualquier nodo. */
    private String signature(FsNode node) {
        String text = node.getName() + ":" + node.getBlockCount() + ":" + node.getFirstBlock() + "(";
        if (node instanceof Directory) {
            Directory dir = (Directory) node;
            for (int i = 0; i < dir.getChildCount(); i++) {
                text += signature(dir.getChildAt(i)) + ",";
            }
        }
        return text + ")";
    }

    private void showInfo() {
        FsNode node = getSelectedNode();
        if (node == null) {
            info.setText("Selecciona un archivo o directorio\npara ver su información.");
            return;
        }
        FileSystem fs = computer.getFileSystem();
        String text = "Nombre:       " + (node.getParent() == null ? "/" : node.getName()) + "\n"
                + "Ruta:         " + fs.pathOf(node) + "\n"
                + "Tipo:         " + (node.isDirectory() ? "Directorio" : "Archivo") + "\n"
                + "Tamaño:       " + node.getBlockCount() + " bloques\n"
                + "Dueño:        " + node.getOwner() + "\n"
                + "Permisos:     " + (node.getPermission() == null ? "-" : node.getPermission().name().toLowerCase()) + "\n"
                + "Primer bloque: " + (node.getFirstBlock() < 0 ? "-" : String.valueOf(node.getFirstBlock())) + "\n"
                + "Creado por:   " + node.getCreator();
        if (node.isFile() && node.getFirstBlock() >= 0) {
            Disk disk = fs.getDisk();
            int[] chain = disk.getChain(node.getFirstBlock());
            String blocks = "";
            for (int i = 0; i < chain.length && i < 30; i++) {
                blocks += (i == 0 ? "" : " -> ") + chain[i];
            }
            if (chain.length > 30) {
                blocks += " -> ...";
            }
            text += "\nCadena:       " + blocks;
        }
        if (!text.equals(info.getText())) {
            info.setText(text);
            info.setCaretPosition(0);
        }
    }

    /** Muestra el nombre, el tamaño y un cuadrito con el color del archivo en el disco. */
    private static class NodeRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected,
                boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
            if (value instanceof FsNode) {
                FsNode node = (FsNode) value;
                if (node.getParent() == null) {
                    setText("/ (raíz)");
                } else if (node.isFile()) {
                    setText(node.getName() + "  [" + node.getBlockCount() + " bl.]");
                    setIcon(new ColorIcon(FileColors.forIndex(node.getColorIndex())));
                } else {
                    setText(node.getName());
                }
            }
            return this;
        }
    }

    private static class ColorIcon implements Icon {
        private final Color color;

        ColorIcon(Color color) {
            this.color = color;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            g.setColor(color);
            g.fillRect(x, y + 1, 12, 12);
            g.setColor(Color.DARK_GRAY);
            g.drawRect(x, y + 1, 12, 12);
        }

        @Override
        public int getIconWidth() {
            return 14;
        }

        @Override
        public int getIconHeight() {
            return 14;
        }
    }
}