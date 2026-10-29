package avilaos.gui;

import avilaos.hardware.Computer;
import avilaos.hardware.ComputerMetrics;
import avilaos.hardware.GlobalClock;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
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
    private final JButton chartsButton = new JButton("Ver gráficas");


    private final JSpinner durationSpinner = new JSpinner(new SpinnerNumberModel(500, 50, 5000, 50));

    public MainWindow(GlobalClock clock, Computer[] computers) {
        super("ÁvilaOS – Simulador de sistema operativo distribuido");
        this.clock = clock;
        this.computers = computers; 

        // --- Arriba: controles globales ---
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        cycleLabel.setFont(cycleLabel.getFont().deriveFont(16f));
        top.add(cycleLabel);
        top.add(startButton);
        top.add(pauseButton);
        top.add(createProcessButton);
        top.add(metricsButton);
        top.add(chartsButton);
        top.add(new JLabel("Duración del ciclo (ms):"));
        top.add(durationSpinner);
        pauseButton.setEnabled(false);

        startButton.addActionListener(e -> start());
        pauseButton.addActionListener(e -> pause());
        metricsButton.addActionListener(e -> mostrarMetricas());
        createProcessButton.addActionListener(e -> {
    new CreateProcessDialog(computers, clock).mostrar();});
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

    private void refresh() {
        long cicloActual = clock.getCycle();

        cycleLabel.setText("Ciclo: " + cicloActual);

        for (ComputerPanel panel : panels) {
            panel.refresh();
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

    JTextArea area = new JTextArea(reporte, 20, 40);
    area.setEditable(false);

    JOptionPane.showMessageDialog(
            this,
            new JScrollPane(area),
            "Métricas de ÁvilaOS",
            JOptionPane.INFORMATION_MESSAGE
    );
}
private void mostrarGraficas() {
    javax.swing.JFrame ventana =
            new javax.swing.JFrame("Gráficas de métricas");
    ventana.setLayout(
            new java.awt.GridLayout(computers.length, 2, 10, 10));
    for (int i = 0; i < computers.length; i++) {
        ComputerMetrics metricas =
                computers[i].getMetrics();

        ventana.add(
                new MetricsChartPanel(metricas, true));

        ventana.add(
                new MetricsChartPanel(metricas, false));
    }
    ventana.setSize(1100, 350 * computers.length);
    ventana.setLocationRelativeTo(this);
    ventana.setVisible(true);
}

}