package avilaos.gui;

import avilaos.config.ConfigStore;
import avilaos.config.SimulationFactory;
import avilaos.hardware.Computer;
import avilaos.hardware.Cluster;
import avilaos.hardware.ComputerMetrics;
import avilaos.hardware.GlobalClock;
import avilaos.hardware.SystemMetrics;
import avilaos.kernel.sync.Buffer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.File;
import java.io.IOException;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.JOptionPane;

/**
 * Ventana principal de ÁvilaOS. Observa el sistema; no tiene lógica del SO.
 */
public class MainWindow extends JFrame {

    private final GlobalClock clock;
    private final ComputerPanel[] panels;
    private final Computer [] computers; 

    private final JLabel cycleLabel = new JLabel("Ciclo: 0");
    private final JTextArea logArea = new JTextArea(8, 80);
    private final JButton startButton = new JButton("Iniciar");
    private final JButton pauseButton = new JButton("Pausar");
    private final JButton createProcessButton = new JButton("Crear proceso");
    private final JButton metricsButton = new JButton("Ver métricas");
    private final JButton createBufferButton = new JButton("Crear buffer");

    // Solo existen si la ventana se crea con el clúster (F3.8 y F3.10)
    private Cluster cluster;
    private BufferPanel bufferPanel;
    private final JButton chartsButton = new JButton("Ver gráficas");


    private final JPanel top;
    private final JButton saveConfigButton = new JButton("Guardar configuración");

    private final JSpinner durationSpinner =new JSpinner(new SpinnerNumberModel(500, 50, 5000, 50));

    public MainWindow(GlobalClock clock, Computer[] computers) {
        super("ÁvilaOS – Simulador de sistema operativo distribuido");
        this.clock = clock;
        this.computers = computers; 

        // --- Arriba: controles globales ---
        top = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        durationSpinner.setValue(clock.getCycleDuration());
        cycleLabel.setFont(cycleLabel.getFont().deriveFont(16f));
        top.add(cycleLabel);
        top.add(startButton);
        top.add(pauseButton);
        top.add(createProcessButton);
        top.add(createBufferButton);
        top.add(metricsButton);
        top.add(chartsButton);
        top.add(new JLabel("Duración del ciclo (ms):"));
        top.add(durationSpinner);
        pauseButton.setEnabled(false);

        startButton.addActionListener(e -> start());
        pauseButton.addActionListener(e -> pause());
        metricsButton.addActionListener(e -> mostrarMetricas());
        createProcessButton.addActionListener(e ->
                new CreateProcessDialog(computers, clock, cluster).mostrar());
        createBufferButton.addActionListener(e -> crearBuffer());
        durationSpinner.addChangeListener(e -> clock.setCycleDuration((Integer) durationSpinner.getValue()));
        chartsButton.addActionListener(e -> mostrarGraficas());

        // --- Centro: un panel por computador ---
        JPanel center = new JPanel(new GridLayout(1, computers.length, 10, 10));
        panels = new ComputerPanel[computers.length];
        for (int i = 0; i < computers.length; i++) {
            panels[i] = new ComputerPanel(computers[i]);
            center.add(panels[i]);
            // El log escucha a cada computador
            computers[i].addEventListener((cycle, message) -> appendLog(cycle, message));
        }

        // --- Abajo: log de eventos ---
        logArea.setEditable(false);
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Log de eventos"));

        setLayout(new BorderLayout(10, 10));
        add(top, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);
        add(logScroll, BorderLayout.SOUTH);

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setPreferredSize(new Dimension(1300, 750));
        pack();
        setLocationRelativeTo(null);

        // --- Refresco: cada 200 ms la ventana vuelve a leer el sistema ---
        Timer refreshTimer = new Timer(200, e -> refresh());
        refreshTimer.start();
    }
    
      /** Ventana con buffers: recibe el clúster completo. */
    public MainWindow(GlobalClock clock, Cluster cluster) {
        this(clock, cluster.getComputers());
        this.cluster = cluster;
        this.bufferPanel = new BufferPanel(cluster);
        add(bufferPanel, BorderLayout.EAST);
        top.add(saveConfigButton);
        saveConfigButton.addActionListener(e -> guardarConfiguracion());
        pack();
        setLocationRelativeTo(null);
    }

    /** Guarda computadores, buffers y procesos actuales para usarlos en otra simulación. */
    private void guardarConfiguracion() {
        JFileChooser chooser = new JFileChooser(new File("."));
        chooser.setFileFilter(new FileNameExtensionFilter("Configuración JSON (*.json)", "json"));
        chooser.setSelectedFile(new File("configuracion.json"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".json")) {
            file = new File(file.getPath() + ".json");
        }
        try {
            ConfigStore.save(SimulationFactory.describe(cluster, clock), file);
            JOptionPane.showMessageDialog(this, "Configuración guardada en:\n" + file.getAbsolutePath(),
                    "Configuración guardada", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException | IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, "No se pudo guardar: " + ex.getMessage(),
                    "Error al guardar", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void crearBuffer() {
        if (cluster == null) {
            JOptionPane.showMessageDialog(this,
                    "Esta ventana se abrió sin clúster: no se pueden crear buffers.",
                    "Sin clúster", JOptionPane.WARNING_MESSAGE);
            return;
        }
        new CreateBufferDialog(cluster, clock).mostrar();
    }

    private void refresh() {
        long cicloActual = clock.getCycle();

        cycleLabel.setText("Ciclo: " + cicloActual);

        for (ComputerPanel panel : panels) {
            panel.refresh();
        }
       
        if (bufferPanel != null) {
            bufferPanel.refresh();
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

    private boolean started = false;

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
    String reporte = "";

    for (int i = 0; i < computers.length; i++) {
        ComputerMetrics metricas = computers[i].getMetrics();

        reporte += "COMPUTADOR  "
                + metricas.getComputerId() + " \n";

        reporte += "Procesos terminados: "
                + metricas.getProcesosTerminados() + "\n";

        reporte += "Deadlines incumplidos: "
                + metricas.getDeadlinesIncumplidos() + "\n";

        reporte += "Ciclos de CPU ocupada: "
                + metricas.getCiclosCPUOcupada() + "\n";

        reporte += "Ciclos totales: "
                + metricas.getCiclosTotales() + "\n";

        reporte += "Uso de CPU: "
                + String.format("%.2f",
                        metricas.getPorcentajeUsoCPU()) + "%\n";

        reporte += "Tiempo promedio de respuesta: "
                + metricas.getPromedioTiempoRespuesta() + "\n";

        reporte += "Tiempo promedio en cola READY: "
                + metricas.getPromedioTiempoEspera() + "\n";

        reporte += "Índice de equidad de Jain: "
                + String.format("%.4f",
                        metricas.getIndiceEquidadJain()) + "\n";
        reporte += "Throughput: "
            + String.format("%.4f", metricas.getThroughput())
            + " procesos/ciclo\n";

        reporte += "Deadlines cumplidos: "
            + String.format("%.2f",
                metricas.getPorcentajeDeadlinesCumplidos())
        + "%\n\n";
    }

    if (cluster != null) {
        reporte += reporteSistema();
    }

    JTextArea area = new JTextArea(reporte, 24, 44);
    area.setEditable(false);

    JOptionPane.showMessageDialog(
            this,
            new JScrollPane(area),
            "Métricas de ÁvilaOS",
            JOptionPane.INFORMATION_MESSAGE
    );
}
/** Totales del clúster y métricas de productor-consumidor por buffer. */
private String reporteSistema() {
    SystemMetrics sistema = new SystemMetrics(cluster);
    String reporte = "SISTEMA COMPLETO\n";

    reporte += "Procesos terminados: " + sistema.getProcesosTerminados() + "\n";
    reporte += "Deadlines incumplidos: " + sistema.getDeadlinesIncumplidos() + "\n";
    reporte += "Throughput: " + String.format("%.4f", sistema.getThroughput())
            + " procesos/ciclo\n";
    reporte += "Uso de CPU: " + String.format("%.2f", sistema.getUtilizacionCPU()) + "%\n";
    reporte += "Tiempo promedio de respuesta: "
            + String.format("%.2f", sistema.getPromedioTiempoRespuesta()) + "\n";
    reporte += "Deadlines cumplidos: "
            + String.format("%.2f", sistema.getPorcentajeDeadlinesCumplidos()) + "%\n";
    reporte += "Índice de equidad de Jain: "
            + String.format("%.4f", sistema.getIndiceEquidadJain()) + "\n\n";

    reporte += "PRODUCTOR-CONSUMIDOR\n";
    reporte += "Elementos producidos: " + sistema.getElementosProducidos() + "\n";
    reporte += "Elementos consumidos: " + sistema.getElementosConsumidos() + "\n";
    reporte += "Promedio de ciclos bloqueados en semáforos: "
            + String.format("%.2f", sistema.getPromedioBloqueoSemaforos()) + "\n";

    Object[] buffers = cluster.getBuffers();
    for (int i = 0; i < buffers.length; i++) {
        Buffer b = (Buffer) buffers[i];
        reporte += "  " + b.getTag() + " (H" + b.getHostComputerId() + "): producidos "
                + b.getProduced() + ", consumidos " + b.getConsumed()
                + ", bloqueo promedio "
                + String.format("%.2f", sistema.getPromedioBloqueoSemaforos(b.getId()))
                + " ciclos\n";
    }
    return reporte;
}

private void mostrarGraficas() {
    CpuUtilizationChart ventana = new CpuUtilizationChart(computers);
    ventana.setLocationRelativeTo(this);
    ventana.setVisible(true);
}

}