package avilaos.gui;

import avilaos.hardware.Computer;
import avilaos.hardware.disk.Disk;
import avilaos.hardware.disk.HeadDirection;
import avilaos.kernel.disk.DiskRequest;
import avilaos.kernel.fs.FileSystem;
import avilaos.kernel.fs.FsNode;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * Pestaña "Disco": estado del cabezal, mapa de bloques, cola de E/S del disco y la
 * tabla de asignación de archivos. Solo observa; no cambia nada del sistema.
 */
public class DiskPanel extends JPanel {

    private final Computer computer;
    private final DiskMapPanel map;

    private final JLabel headLabel = new JLabel();
    private final JLabel directionLabel = new JLabel();
    private final JLabel speedLabel = new JLabel();
    private final JLabel policyLabel = new JLabel();
    private final JLabel currentLabel = new JLabel();
    private final JLabel freeLabel = new JLabel();

    private final DefaultListModel<String> queueModel = new DefaultListModel<>();
    private final AllocationModel allocationModel = new AllocationModel();
    private String lastQueueText = "";

    public DiskPanel(Computer computer) {
        this.computer = computer;
        Disk disk = computer.getDisk();
        FileSystem fs = computer.getFileSystem();
        this.map = new DiskMapPanel(disk, fs);

        setLayout(new BorderLayout(5, 5));

        // --- Arriba: estado del cabezal ---
        JPanel status = new JPanel(new GridLayout(2, 3, 10, 2));
        status.setBorder(BorderFactory.createTitledBorder("Cabezal del disco"));
        status.add(headLabel);
        status.add(directionLabel);
        status.add(speedLabel);
        status.add(policyLabel);
        status.add(currentLabel);
        status.add(freeLabel);

        // --- Centro: mapa + leyenda ---
        JPanel mapPanel = new JPanel(new BorderLayout());
        mapPanel.setBorder(BorderFactory.createTitledBorder("Mapa del disco ("
                + disk.getFileBlockCount() + " bloques de archivos: 0–" + (disk.getSwapStart() - 1)
                + " | swap: " + disk.getSwapStart() + "–" + (disk.getTotalBlocks() - 1) + ")"));
        mapPanel.add(new JScrollPane(map), BorderLayout.CENTER);
        mapPanel.add(legend(), BorderLayout.SOUTH);

        // --- Derecha: cola de E/S ---
        JList<String> queueList = new JList<>(queueModel);
        JScrollPane queueScroll = new JScrollPane(queueList);
        queueScroll.setBorder(BorderFactory.createTitledBorder("Cola de E/S del disco"));
        queueScroll.setPreferredSize(new Dimension(280, 200));

        // --- Abajo: tabla de asignación ---
        JTable table = new JTable(allocationModel);
        table.setDefaultRenderer(Object.class, new ColorRenderer());
        table.setAutoCreateRowSorter(false);
        table.getColumnModel().getColumn(0).setPreferredWidth(200);
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(BorderFactory.createTitledBorder("Tabla de asignación de archivos"));
        tableScroll.setPreferredSize(new Dimension(600, 170));

        JSplitPane top = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, mapPanel, queueScroll);
        top.setResizeWeight(0.75);
        JSplitPane all = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, tableScroll);
        all.setResizeWeight(0.65);

        add(status, BorderLayout.NORTH);
        add(all, BorderLayout.CENTER);
        refresh();
    }

    private JPanel legend() {
        JPanel legend = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 2));
        legend.add(swatch(FileColors.FREE, "Libre"));
        legend.add(swatch(FileColors.forIndex(1), "Archivo (un color por archivo)"));
        legend.add(swatch(FileColors.FREE_SWAP, "Swap libre"));
        legend.add(swatch(FileColors.SWAP_USED, "Swap: proceso suspendido"));
        JLabel head = new JLabel("Borde rojo = cabezal (▶ sube, ◀ baja)");
        head.setForeground(FileColors.HEAD);
        legend.add(head);
        return legend;
    }

    private JLabel swatch(Color color, String text) {
        JLabel label = new JLabel(text);
        label.setIcon(new javax.swing.Icon() {
            public void paintIcon(Component c, java.awt.Graphics g, int x, int y) {
                g.setColor(color);
                g.fillRect(x, y, 12, 12);
                g.setColor(Color.GRAY);
                g.drawRect(x, y, 12, 12);
            }
            public int getIconWidth() { return 14; }
            public int getIconHeight() { return 13; }
        });
        return label;
    }

    /** Se llama cada 200 ms desde la ventana principal. */
    public void refresh() {
        try {
            Disk disk = computer.getDisk();
            headLabel.setText("Posición: bloque " + disk.getCurrentHead());
            directionLabel.setText("Dirección: " + (disk.getHeadDirection() == HeadDirection.UP
                    ? "▶ hacia arriba (sube)" : "◀ hacia abajo (baja)"));
            speedLabel.setText("Velocidad: " + disk.getHeadSpeed() + " bloques/ciclo");
            policyLabel.setText("Política de disco: " + computer.getDiskPolicyType().getLabel());
            DiskRequest current = disk.getCurrentRequest();
            currentLabel.setText("Atendiendo: " + (current == null ? "(libre)" : describe(current)));
            freeLabel.setText("Libres: " + disk.countFreeFileBlocks() + " de archivos, "
                    + disk.countFreeSwapBlocks() + " de swap");

            refreshQueue(current);
            allocationModel.reload(computer.getFileSystem());
            map.repaint();
        } catch (RuntimeException ex) {
            // El reloj cambió el disco mientras se leía: se reintenta en el siguiente refresco.
        }
    }

    private void refreshQueue(DiskRequest current) {
        Object[] waiting = computer.getDiskScheduler().getRequests();
        String[] lines = new String[waiting.length + 1];
        lines[0] = current == null ? "▶ (el disco está libre)" : "▶ " + describe(current);
        for (int i = 0; i < waiting.length; i++) {
            lines[i + 1] = (i + 1) + ". " + describe((DiskRequest) waiting[i]);
        }
        String text = "";
        for (int i = 0; i < lines.length; i++) {
            text += lines[i] + "\n";
        }
        if (!text.equals(lastQueueText)) {
            queueModel.clear();
            for (int i = 0; i < lines.length; i++) {
                queueModel.addElement(lines[i]);
            }
            lastQueueText = text;
        }
    }

    private String describe(DiskRequest r) {
        String who = r.getProcess() == null ? "kernel" : r.getProcess().getTag() + " " + r.getProcess().getName();
        String what = r.getOperation() == null ? "?" : r.getOperation().name();
        String path = r.getTargetPath() == null ? "" : " " + r.getTargetPath();
        return who + " · " + what + path + " · bloque " + r.getTargetBlock()
                + " · llegó en ciclo " + r.getArrivalCycle();
    }

    /** Filas de la tabla: un archivo por fila, leído del FileSystem en cada refresco. */
    private static class AllocationModel extends AbstractTableModel {
        private static final String[] COLUMNS = {
            "Archivo", "Bloques", "Primer bloque", "Dueño", "Proceso creador", "Color"
        };
        private Object[][] rows = new Object[0][];
        private String signature = "";

        void reload(FileSystem fs) {
            FsNode[] nodes = fs.getAllNodes();
            int files = 0;
            for (int i = 0; i < nodes.length; i++) {
                if (nodes[i].isFile()) {
                    files++;
                }
            }
            Object[][] fresh = new Object[files][];
            String sig = "";
            int r = 0;
            for (int i = 0; i < nodes.length; i++) {
                FsNode n = nodes[i];
                if (!n.isFile()) {
                    continue;
                }
                fresh[r++] = new Object[] {
                    fs.pathOf(n), n.getBlockCount(), n.getFirstBlock(), n.getOwner(), n.getCreator(),
                    FileColors.forIndex(n.getColorIndex())
                };
                sig += fs.pathOf(n) + n.getBlockCount() + n.getFirstBlock() + n.getOwner() + n.getCreator() + ";";
            }
            if (!sig.equals(signature)) {
                rows = fresh;
                signature = sig;
                fireTableDataChanged();
            }
        }

        @Override public int getRowCount() { return rows.length; }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int c) { return COLUMNS[c]; }
        @Override public Object getValueAt(int r, int c) { return rows[r][c]; }
    }

    /** La columna "Color" se pinta con el mismo color que el archivo tiene en el mapa. */
    private static class ColorRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                boolean focus, int row, int column) {
            if (value instanceof Color) {
                super.getTableCellRendererComponent(table, "", selected, focus, row, column);
                setBackground((Color) value);
                return this;
            }
            super.getTableCellRendererComponent(table, value, selected, focus, row, column);
            if (!selected) {
                setBackground(Color.WHITE);
            }
            return this;
        }
    }
}