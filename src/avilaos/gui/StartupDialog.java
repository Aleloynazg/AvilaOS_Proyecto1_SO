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
 * Vista de configuración inicial: número de computadores, RAM, política,
 * quantum, latencia y duración del ciclo; o cargar todo desde un archivo JSON.
 * Los spinners solo aceptan números dentro de su rango.
 */
public class StartupDialog {

    private static final String START = "Iniciar simulación";
    private static final String LOAD = "Cargar archivo JSON...";
    private static final String EXIT = "Salir";

    private final JSpinner computerCount = spinner(2, SimulationConfig.MIN_COMPUTERS,
            SimulationConfig.MAX_COMPUTERS, 1);
    private final JSpinner cycleMs = spinner(500, SimulationConfig.MIN_CYCLE_MS,
            SimulationConfig.MAX_CYCLE_MS, 50);
    private final JSpinner latency = spinner(3, SimulationConfig.MIN_LATENCY,
            SimulationConfig.MAX_LATENCY, 1);

    private final JLabel[] rowLabels = new JLabel[SimulationConfig.MAX_COMPUTERS];
    private final JSpinner[] ram = new JSpinner[SimulationConfig.MAX_COMPUTERS];
    private final JComboBox<SchedulingPolicyType>[] policy = newCombos();
    private final JSpinner[] quantum = new JSpinner[SimulationConfig.MAX_COMPUTERS];

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
        JPanel form = new JPanel(new GridLayout(0, 4, 8, 6));

        JPanel general = new JPanel(new GridLayout(0, 2, 8, 6));
        general.add(new JLabel("Número de computadores (2-4):"));
        general.add(computerCount);
        general.add(new JLabel("Duración del ciclo (ms, 50-5000):"));
        general.add(cycleMs);
        general.add(new JLabel("Latencia de red (ciclos, 0-20):"));
        general.add(latency);

        form.add(new JLabel("Computador"));
        form.add(new JLabel("RAM (MB, 128-8192)"));
        form.add(new JLabel("Política inicial"));
        form.add(new JLabel("Quantum (1-20)"));

        for (int i = 0; i < SimulationConfig.MAX_COMPUTERS; i++) {
            rowLabels[i] = new JLabel("H" + (i + 1));
            ram[i] = spinner(1024, SimulationConfig.MIN_RAM, SimulationConfig.MAX_RAM, 128);
            policy[i] = new JComboBox<>(SchedulingPolicyType.values());
            quantum[i] = spinner(4, SimulationConfig.MIN_QUANTUM, SimulationConfig.MAX_QUANTUM, 1);
            form.add(rowLabels[i]);
            form.add(ram[i]);
            form.add(policy[i]);
            form.add(quantum[i]);
        }

        computerCount.addChangeListener(e -> showRows());
        showRows();

        JPanel all = new JPanel(new java.awt.BorderLayout(0, 10));
        all.add(general, java.awt.BorderLayout.NORTH);
        all.add(form, java.awt.BorderLayout.CENTER);
        return all;
    }

    /** Solo se ven las filas de los computadores que existirán. */
    private void showRows() {
        int count = (Integer) computerCount.getValue();
        for (int i = 0; i < SimulationConfig.MAX_COMPUTERS; i++) {
            boolean used = i < count;
            rowLabels[i].setEnabled(used);
            ram[i].setEnabled(used);
            policy[i].setEnabled(used);
            quantum[i].setEnabled(used);
        }
    }

    private SimulationConfig readForm() throws ParseException {
        computerCount.commitEdit();
        cycleMs.commitEdit();
        latency.commitEdit();

        SimulationConfig config = SimulationConfig.defaults((Integer) computerCount.getValue());
        config.cycleDurationMs = (Integer) cycleMs.getValue();
        config.networkLatency = (Integer) latency.getValue();
        for (int i = 0; i < config.computers.length; i++) {
            ram[i].commitEdit();
            quantum[i].commitEdit();
            config.computers[i].ramMb = (Integer) ram[i].getValue();
            config.computers[i].policy = ((SchedulingPolicyType) policy[i].getSelectedItem()).name();
            config.computers[i].quantum = (Integer) quantum[i].getValue();
        }
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

    @SuppressWarnings("unchecked")
    private static JComboBox<SchedulingPolicyType>[] newCombos() {
        return new JComboBox[SimulationConfig.MAX_COMPUTERS];
    }
}
