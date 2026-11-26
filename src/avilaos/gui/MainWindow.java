package avilaos.gui;

import avilaos.config.ConfigStore;
import avilaos.config.FileSystemConfig;
import avilaos.config.SimulationFactory;
import avilaos.hardware.Computer;
import avilaos.hardware.ComputerMetrics;
import avilaos.hardware.GlobalClock;
import avilaos.hardware.disk.Disk;
import avilaos.kernel.disk.DiskPolicyType;
import avilaos.kernel.fs.AccessControl;
import avilaos.kernel.fs.UserMode;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.File;
import java.io.IOException;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Ventana principal de ÁvilaOS (Proyecto 2). Observa el sistema; no tiene lógica del SO.
 * Izquierda: árbol del sistema de archivos. Centro: pestañas "Computador" y "Disco".
 * Abajo: log de eventos. Arriba: controles del reloj, de la política de disco y del modo.
 */
public class MainWindow extends JFrame {

    private final GlobalClock clock;
    private final Computer computer;
    private final ComputerPanel panel;
    private final DiskPanel diskPanel;
    private final FileTreePanel treePanel;
    private final HeadMovementChart headChart;

    private final JLabel cycleLabel = new JLabel("Ciclo: 0");
    private final JTextArea logArea = new JTextArea(8, 80);
    private final JButton startButton = new JButton("Iniciar");
    private final JButton pauseButton = new JButton("Pausar");
    private final JButton createProcessButton = new JButton("Crear proceso");
    private final JButton fileOperationButton = new JButton("Operación de archivo…");
    private final JButton metricsButton = new JButton("Ver métricas");
    private final JButton chartsButton = new JButton("Gráfica CPU");
    private final JButton headChartButton = new JButton("Gráfica del cabezal");
    private final JButton saveProcessesButton = new JButton("Guardar procesos (JSON)");
    private final JButton saveFileSystemButton = new JButton("Guardar sistema de archivos (JSON)");
    private final JSpinner durationSpinner = new JSpinner(new SpinnerNumberModel(500, 50, 5000, 50));

    private final JComboBox<DiskPolicyType> diskPolicyBox = new JComboBox<>(DiskPolicyType.values());
    private final JComboBox<String> modeBox = new JComboBox<>(new String[] { "Administrador", "Usuario" });
    private final JTextField userField = new JTextField("user1", 8);
    private final JLabel modeLabel = new JLabel();

    private boolean started = false;

    public MainWindow(GlobalClock clock, Computer computer) {
        super("ÁvilaOS – Simulador de sistema operativo con sistema de archivos");
        this.clock = clock;
        this.computer = computer;
        this.headChart = new HeadMovementChart(computer);
        this.panel = new ComputerPanel(computer);
        this.diskPanel = new DiskPanel(computer);
        this.treePanel = new FileTreePanel(computer);

        // --- Arriba, fila 1: reloj y ventanas ---
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        durationSpinner.setValue(clock.getCycleDuration());
        cycleLabel.setFont(cycleLabel.getFont().deriveFont(16f));
        row1.add(cycleLabel);
        row1.add(startButton);
        row1.add(pauseButton);
        row1.add(new JLabel("Duración del ciclo (ms):"));
        row1.add(durationSpinner);
        row1.add(createProcessButton);
        row1.add(fileOperationButton);
        row1.add(metricsButton);
        row1.add(chartsButton);
        row1.add(headChartButton);
        pauseButton.setEnabled(false);

        // --- Arriba, fila 2: política de disco, modo de uso y guardar ---
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        row2.add(new JLabel("Política de disco:"));
        diskPolicyBox.setSelectedItem(computer.getDiskPolicyType());
        row2.add(diskPolicyBox);
        row2.add(new JLabel("   Modo:"));
        row2.add(modeBox);
        row2.add(new JLabel("Usuario:"));
        row2.add(userField);
        JButton applyMode = new JButton("Aplicar modo");
        row2.add(applyMode);
        modeLabel.setFont(modeLabel.getFont().deriveFont(java.awt.Font.BOLD));
        row2.add(modeLabel);
        row2.add(saveProcessesButton);
        row2.add(saveFileSystemButton);
        userField.setEnabled(false);

        JPanel top = new JPanel(new GridLayout(2, 1));
        top.add(row1);
        top.add(row2);

        startButton.addActionListener(e -> start());
        pauseButton.addActionListener(e -> pause());
        metricsButton.addActionListener(e -> mostrarMetricas());
        createProcessButton.addActionListener(e -> new CreateProcessDialog(computer, clock).mostrar());
        fileOperationButton.addActionListener(e -> new FileOperationDialog(this, computer, clock)
                .mostrar(treePanel.getSelectedPath(), treePanel.getSelectedDirectoryPath()));
        saveProcessesButton.addActionListener(e -> guardarProcesos());
        saveFileSystemButton.addActionListener(e -> guardarSistemaArchivos());
        durationSpinner.addChangeListener(e -> clock.setCycleDuration((Integer) durationSpinner.getValue()));
        chartsButton.addActionListener(e -> mostrarGraficas());
        headChartButton.addActionListener(e -> headChart.mostrar(this));
        diskPolicyBox.addActionListener(e -> cambiarPoliticaDisco());
        modeBox.addActionListener(e -> userField.setEnabled(modeBox.getSelectedIndex() == 1));
        applyMode.addActionListener(e -> aplicarModo());

        // --- Centro: árbol a la izquierda, pestañas a la derecha ---
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Computador", panel);
        tabs.addTab("Disco", diskPanel);
        JSplitPane center = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treePanel, tabs);
        center.setResizeWeight(0.22);

        // El log escucha al computador
        computer.addEventListener((cycle, message) -> appendLog(cycle, message));

        // --- Abajo: log de eventos ---
        logArea.setEditable(false);
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Log de eventos (CPU, disco y planificador a mediano plazo)"));
        logScroll.setPreferredSize(new Dimension(1300, 170));

        setLayout(new BorderLayout(6, 6));
        add(top, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);
        add(logScroll, BorderLayout.SOUTH);

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setPreferredSize(new Dimension(1400, 900));
        pack();
        setLocationRelativeTo(null);

        refresh();
        // --- Refresco: cada 200 ms la ventana vuelve a leer el sistema ---
        Timer refreshTimer = new Timer(200, e -> refresh());
        refreshTimer.start();
    }

    private void refresh() {
        cycleLabel.setText("Ciclo: " + clock.getCycle());
        AccessControl access = computer.getAccessControl();
        modeLabel.setText("Modo activo: " + access.describe());
        modeLabel.setForeground(access.getMode() == UserMode.ADMIN ? new Color(0, 110, 0) : new Color(160, 70, 0));
        if (diskPolicyBox.getSelectedItem() != computer.getDiskPolicyType() && !diskPolicyBox.isPopupVisible()) {
            diskPolicyBox.setSelectedItem(computer.getDiskPolicyType());
        }
        panel.refresh();
        diskPanel.refresh();
        treePanel.refresh();
        headChart.sample(clock);
    }

    /** Cambia la política de disco en tiempo de ejecución; el Computer lo anota en el log. */
    private void cambiarPoliticaDisco() {
        DiskPolicyType type = (DiskPolicyType) diskPolicyBox.getSelectedItem();
        if (type != null && type != computer.getDiskPolicyType()) {
            computer.setDiskPolicy(type);
        }
    }

    private void aplicarModo() {
        AccessControl access = computer.getAccessControl();
        try {
            if (modeBox.getSelectedIndex() == 0) {
                access.setAdminMode();
            } else {
                access.setUserMode(userField.getText().trim());
            }
            computer.getKernel().reportEvent(clock.getCycle(), "Cambio de modo: " + access.describe());
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Usuario inválido", JOptionPane.WARNING_MESSAGE);
        }
        refresh();
    }

    private File elegirDestino(String nombre) {
        JFileChooser chooser = new JFileChooser(new File("."));
        chooser.setFileFilter(new FileNameExtensionFilter("Archivo JSON (*.json)", "json"));
        chooser.setSelectedFile(new File(nombre));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".json")) {
            file = new File(file.getPath() + ".json");
        }
        return file;
    }

    /** Guarda el JSON de procesos y configuración (formato del enunciado). */
    private void guardarProcesos() {
        File file = elegirDestino("procesos_guardados.json");
        if (file == null) {
            return;
        }
        try {
            ConfigStore.saveProcesses(SimulationFactory.describeProcesses(computer, clock), file);
            JOptionPane.showMessageDialog(this, "Procesos guardados en:\n" + file.getAbsolutePath(),
                    "Guardado", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException | RuntimeException ex) {
            JOptionPane.showMessageDialog(this, "No se pudo guardar: " + ex.getMessage(),
                    "Error al guardar", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Guarda el estado actual del sistema de archivos (formato del enunciado). */
    private void guardarSistemaArchivos() {
        File file = elegirDestino("sistema_archivos_guardado.json");
        if (file == null) {
            return;
        }
        try {
            FileSystemConfig state = FileSystemConfig.fromState(computer.getFileSystem(), computer.getDisk());
            ConfigStore.saveFileSystem(state, file);
            JOptionPane.showMessageDialog(this, "Sistema de archivos guardado en:\n" + file.getAbsolutePath(),
                    "Guardado", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException | RuntimeException ex) {
            JOptionPane.showMessageDialog(this, "No se pudo guardar: " + ex.getMessage(),
                    "Error al guardar", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Los eventos llegan desde el hilo del reloj: se pasan al hilo de Swing. */
    private void appendLog(long cycle, String message) {
        if (message.contains("recibió el ciclo")) {
            return; // la CPU avisa esto en cada ciclo: no aporta al log
        }
        SwingUtilities.invokeLater(() -> {
            logArea.append("[" + cycle + "] " + message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void start() {
        if (!started) {
            clock.startClock();
            started = true;
        } else {
            clock.resumeClock();
        }
        startButton.setEnabled(false);
        pauseButton.setEnabled(true);
    }

    private void pause() {
        clock.pauseClock();
        startButton.setText("Reanudar");
        startButton.setEnabled(true);
        pauseButton.setEnabled(false);
    }

    private void mostrarMetricas() {
        ComputerMetrics metricas = computer.getMetrics();
        Disk disk = computer.getDisk();
        String reporte = "=== CPU Y PROCESOS (computador " + metricas.getComputerId() + ") ===\n";
        reporte += "Procesos terminados: " + metricas.getProcesosTerminados() + "\n";
        reporte += "Deadlines incumplidos: " + metricas.getDeadlinesIncumplidos() + "\n";
        reporte += "Ciclos de CPU ocupada: " + metricas.getCiclosCPUOcupada() + "\n";
        reporte += "Ciclos totales: " + metricas.getCiclosTotales() + "\n";
        reporte += "Uso de CPU: " + String.format("%.2f", metricas.getPorcentajeUsoCPU()) + "%\n";
        reporte += "Tiempo promedio de respuesta: " + metricas.getPromedioTiempoRespuesta() + "\n";
        reporte += "Tiempo promedio en cola READY: " + metricas.getPromedioTiempoEspera() + "\n";
        reporte += "Índice de equidad de Jain: " + String.format("%.4f", metricas.getIndiceEquidadJain()) + "\n";
        reporte += "Throughput: " + String.format("%.4f", metricas.getThroughput()) + " procesos/ciclo\n";
        reporte += "Deadlines cumplidos: "
                + String.format("%.2f", metricas.getPorcentajeDeadlinesCumplidos()) + "%\n\n";

        reporte += "=== DISCO ===\n";
        reporte += "Política actual: " + computer.getDiskPolicyType().getLabel() + "\n";
        reporte += "Movimiento total del cabezal: " + disk.getTotalHeadMovement() + " bloques\n";
        reporte += "Tiempo promedio de espera en disco: "
                + String.format("%.2f", disk.getAverageWaitingTime()) + " ciclos\n";
        reporte += "Solicitudes atendidas: " + disk.getCompletedRequests() + "\n";
        reporte += "   FIFO: " + disk.getFifoCompleted() + "\n";
        reporte += "   SSTF: " + disk.getSstfCompleted() + "\n";
        reporte += "   SCAN: " + disk.getScanCompleted() + "\n";
        reporte += "   C-SCAN: " + disk.getCscanCompleted() + "\n";
        reporte += "   LOOK: " + disk.getLookCompleted() + "\n\n";

        reporte += "=== SUSPENSIÓN (planificador a mediano plazo) ===\n";
        reporte += "Suspensiones: " + computer.getKernel().getSuspensionCount() + "\n";
        reporte += "Reactivaciones: " + computer.getKernel().getReactivationCount() + "\n";
        reporte += "Swap libre: " + disk.countFreeSwapBlocks() + " de " + disk.getSwapBlockCount() + " bloques\n\n";

        reporte += "=== SISTEMA DE ARCHIVOS ===\n";
        reporte += "Archivos: " + computer.getFileSystem().countFiles()
                + " | Directorios: " + computer.getFileSystem().countDirectories() + "\n";
        reporte += "Bloques usados: " + computer.getFileSystem().getUsedBlocks()
                + " | libres: " + disk.countFreeFileBlocks() + "\n";

        JTextArea area = new JTextArea(reporte, 30, 48);
        area.setEditable(false);
        JOptionPane.showMessageDialog(this, new JScrollPane(area),
                "Métricas de ÁvilaOS", JOptionPane.INFORMATION_MESSAGE);
    }

    private void mostrarGraficas() {
        CpuUtilizationChart ventana = new CpuUtilizationChart(new Computer[] { computer });
        ventana.setLocationRelativeTo(this);
        ventana.setVisible(true);
    }
}
