package avilaos.gui;

import avilaos.config.ConfigStore;
import avilaos.config.SimulationConfig;
import avilaos.kernel.scheduler.SchedulingPolicyType;
import java.awt.GridLayout;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Vista de configuración inicial del computador: RAM, política, quantum y
 * duración del ciclo; o cargar todo desde un archivo JSON.
 * Los spinners solo aceptan números dentro de su rango.
 * (En la F5.1 se reemplaza por la carga obligatoria de los dos JSON.)
 */
public class StartupDialog {

    private static final String START = "Iniciar simulación";
    private static final String LOAD = "Cargar archivo JSON...";
    private static final String EXIT = "Salir";

    private final JSpinner cycleMs = spinner(500, SimulationConfig.MIN_CYCLE_MS,
            SimulationConfig.MAX_CYCLE_MS, 50);
    private final JSpinner ram = spinner(1024, SimulationConfig.MIN_RAM,
            SimulationConfig.MAX_RAM, 128);
    private final JComboBox<SchedulingPolicyType> policy = new JComboBox<>(SchedulingPolicyType.values());
    private final JSpinner quantum = spinner(4, SimulationConfig.MIN_QUANTUM,
            SimulationConfig.MAX_QUANTUM, 1);

    /**
     * Muestra la vista hasta que el usuario elige una configuración válida.
     * Devuelve null si decide salir.
     */
    public SimulationConfig mostrar() {
        JPanel form = buildForm();

        while (true) {
            int option = JOptionPane.showOptionDialog(null, form, "ÁvilaOS – Configuración",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null,
                    new Object[] { START, LOAD, EXIT }, START);

            if (option == 2 || option == JOptionPane.CLOSED_OPTION) {
                return null;
            }
            try {
                if (option == 1) {
                    SimulationConfig loaded = loadFromFile();
                    if (loaded != null) {
                        return loaded;
                    }
                } else {
                    return readForm();
                }
            } catch (IllegalArgumentException | IOException | ParseException ex) {
                JOptionPane.showMessageDialog(null, ex.getMessage(), "Configuración inválida",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private JPanel buildForm() {
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 6));
        form.add(new JLabel("Duración del ciclo (ms, 50-5000):"));
        form.add(cycleMs);
        form.add(new JLabel("RAM (MB, 128-8192):"));
        form.add(ram);
        form.add(new JLabel("Política de CPU inicial:"));
        form.add(policy);
        form.add(new JLabel("Quantum (1-20):"));
        form.add(quantum);
        return form;
    }

    private SimulationConfig readForm() throws ParseException {
        cycleMs.commitEdit();
        ram.commitEdit();
        quantum.commitEdit();

        SimulationConfig config = SimulationConfig.defaults();
        config.cycleDurationMs = (Integer) cycleMs.getValue();
        config.computer.ramMb = (Integer) ram.getValue();
        config.computer.policy = ((SchedulingPolicyType) policy.getSelectedItem()).name();
        config.computer.quantum = (Integer) quantum.getValue();
        config.validate();
        return config;
    }

    /** Devuelve null si el usuario cancela el selector de archivos. */
    private SimulationConfig loadFromFile() throws IOException {
        JFileChooser chooser = new JFileChooser(new File("."));
        chooser.setFileFilter(new FileNameExtensionFilter("Configuración JSON (*.json)", "json"));
        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        return ConfigStore.load(chooser.getSelectedFile());
    }

    private static JSpinner spinner(int value, int min, int max, int step) {
        return new JSpinner(new SpinnerNumberModel(value, min, max, step));
    }
}
