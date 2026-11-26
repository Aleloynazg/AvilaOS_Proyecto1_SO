package avilaos.gui;

import avilaos.hardware.Computer;
import avilaos.hardware.GlobalClock;
import avilaos.kernel.fs.NodeType;
import avilaos.kernel.fs.Permission;
import avilaos.kernel.process.IoOperation;
import avilaos.kernel.process.PCB;
import avilaos.kernel.process.ProcessFactory;
import java.awt.Component;
import java.awt.GridLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

/**
 * Pide una operación de archivos (crear, leer, renombrar o eliminar). La interfaz NO
 * toca el sistema de archivos: crea un proceso de E/S que hará la operación cuando
 * el disco lo atienda. Antes revisa el modo de uso: si no está permitido, explica por
 * qué y la simulación sigue corriendo.
 */
public class FileOperationDialog {

    private static int counter = 1;

    private final Component parent;
    private final Computer computer;
    private final GlobalClock clock;

    public FileOperationDialog(Component parent, Computer computer, GlobalClock clock) {
        this.parent = parent;
        this.computer = computer;
        this.clock = clock;
    }

    /**
     * @param selectedPath ruta del elemento seleccionado en el árbol (o null)
     * @param selectedDirectory directorio seleccionado (o el padre del archivo seleccionado)
     */
    public void mostrar(String selectedPath, String selectedDirectory) {
        String[] operations = {
            "CREATE – crear archivo o directorio",
            "READ – leer archivo",
            "UPDATE – renombrar",
            "DELETE – eliminar"
        };
        JComboBox<String> operationBox = new JComboBox<>(operations);
        JComboBox<String> typeBox = new JComboBox<>(new String[] { "Archivo", "Directorio" });
        String dir = selectedDirectory == null ? "/" : selectedDirectory;
        JTextField pathField = new JTextField(
                (dir.endsWith("/") ? dir : dir + "/") + "nuevo.txt", 26);
        JSpinner blocksSpinner = new JSpinner(new SpinnerNumberModel(5, 1, 500, 1));
        JComboBox<String> permissionBox = new JComboBox<>(new String[] { "public", "private" });
        JTextField newNameField = new JTextField(16);
        JTextField nameField = new JTextField("Op_" + counter, 12);
        JSpinner prioritySpinner = new JSpinner(new SpinnerNumberModel(5, 1, 10, 1));
        JSpinner memorySpinner = new JSpinner(new SpinnerNumberModel(16, 1, 4096, 1));
        JSpinner deadlineSpinner = new JSpinner(new SpinnerNumberModel(500, 1, 100000, 10));
        JSpinner instructionsSpinner = new JSpinner(new SpinnerNumberModel(2, 1, 1000, 1));

        // Al cambiar la operación se propone la ruta adecuada
        operationBox.addActionListener(e -> {
            boolean create = operationBox.getSelectedIndex() == 0;
            if (create) {
                pathField.setText((dir.endsWith("/") ? dir : dir + "/") + "nuevo.txt");
            } else if (selectedPath != null) {
                pathField.setText(selectedPath);
            }
            typeBox.setEnabled(create);
            blocksSpinner.setEnabled(create);
            permissionBox.setEnabled(create);
            newNameField.setEnabled(operationBox.getSelectedIndex() == 2);
        });
        newNameField.setEnabled(false);
        typeBox.addActionListener(e -> blocksSpinner.setEnabled(
                operationBox.getSelectedIndex() == 0 && typeBox.getSelectedIndex() == 0));

        JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
        form.add(new JLabel("Modo actual:"));
        form.add(new JLabel(computer.getAccessControl().describe()));
        form.add(new JLabel("Operación:"));
        form.add(operationBox);
        form.add(new JLabel("Ruta (CREATE: ruta completa nueva):"));
        form.add(pathField);
        form.add(new JLabel("Tipo (CREATE):"));
        form.add(typeBox);
        form.add(new JLabel("Tamaño en bloques (CREATE archivo):"));
        form.add(blocksSpinner);
        form.add(new JLabel("Permiso (CREATE):"));
        form.add(permissionBox);
        form.add(new JLabel("Nombre nuevo (UPDATE, vacío = _v2):"));
        form.add(newNameField);
        form.add(new JLabel("Nombre del proceso:"));
        form.add(nameField);
        form.add(new JLabel("Prioridad:"));
        form.add(prioritySpinner);
        form.add(new JLabel("Memoria (MB):"));
        form.add(memorySpinner);
        form.add(new JLabel("Deadline (ciclos):"));
        form.add(deadlineSpinner);
        form.add(new JLabel("Instrucciones:"));
        form.add(instructionsSpinner);

        if (selectedPath != null && !selectedPath.equals("/")) {
            operationBox.setSelectedIndex(1); // con algo seleccionado lo normal es leerlo
        }

        int answer = JOptionPane.showConfirmDialog(parent, form, "Operación de archivo",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) {
            return;
        }

        IoOperation operation = IoOperation.values()[operationBox.getSelectedIndex()];
        String path = pathField.getText().trim();
        NodeType type = typeBox.getSelectedIndex() == 0 ? NodeType.FILE : NodeType.DIRECTORY;
        long cycle = clock.getCycle();

        // 1. El modo de uso decide si se permite (sin detener la simulación)
        String rejection = computer.checkFileOperation(operation, path, type);
        if (rejection != null) {
            computer.getKernel().reportEvent(cycle, "Operación rechazada (" + operation + " " + path + "): " + rejection);
            JOptionPane.showMessageDialog(parent,
                    "No se permite la operación en " + computer.getAccessControl().describe() + ":\n" + rejection
                    + "\n\nLa simulación sigue corriendo.",
                    "Operación rechazada", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // 2. Se crea el proceso de E/S; el disco hará la operación cuando lo atienda
        try {
            String newName = newNameField.getText().trim();
            PCB process = ProcessFactory.fileIo(
                    nameField.getText().trim().isEmpty() ? "Op_" + counter : nameField.getText().trim(),
                    computer.getId(),
                    (Integer) prioritySpinner.getValue(),
                    (Integer) memorySpinner.getValue(),
                    (Integer) deadlineSpinner.getValue(),
                    (Integer) instructionsSpinner.getValue(),
                    operation, path,
                    newName.isEmpty() ? null : newName,
                    type,
                    type == NodeType.FILE ? (Integer) blocksSpinner.getValue() : 0,
                    computer.getAccessControl().getCurrentUser(),
                    permissionBox.getSelectedIndex() == 0 ? Permission.PUBLIC : Permission.PRIVATE);
            computer.getKernel().submitProcess(process, cycle);
            counter++;
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(parent, ex.getMessage(),
                    "Datos inválidos", JOptionPane.ERROR_MESSAGE);
        }
    }
}