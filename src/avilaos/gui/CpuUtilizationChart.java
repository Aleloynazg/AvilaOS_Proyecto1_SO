package avilaos.gui;

import avilaos.hardware.Computer;
import avilaos.hardware.ComputerMetrics;
import java.awt.Dimension;
import javax.swing.JFrame;
import javax.swing.Timer;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

/**
 * Gráfica única con la utilización de la CPU de cada computador respecto al
 * tiempo (ciclos). Solo lee las métricas: no tiene lógica del SO.
 */
public class CpuUtilizationChart extends JFrame {

    /** Ciclos que abarca cada punto: utilización de los últimos N ciclos. */
    private static final int WINDOW = 10;
    /** Máximo de puntos por serie; si hay más, se muestrean. */
    private static final int MAX_POINTS = 1500;

    private final Computer[] computers;
    private final XYSeries[] series;
    private final Timer refreshTimer;

    public CpuUtilizationChart(Computer[] computers) {
        super("Utilización de CPU por computador");
        this.computers = computers;

        XYSeriesCollection dataset = new XYSeriesCollection();
        series = new XYSeries[computers.length];
        for (int i = 0; i < computers.length; i++) {
            series[i] = new XYSeries("Computador " + computers[i].getId());
            dataset.addSeries(series[i]);
        }

        JFreeChart chart = ChartFactory.createXYLineChart(
                "Utilización de CPU (últimos " + WINDOW + " ciclos)",
                "Ciclo de reloj",
                "Utilización (%)",
                dataset,
                PlotOrientation.VERTICAL,
                true, true, false);
        XYPlot plot = chart.getXYPlot();
        plot.getRangeAxis().setRange(0, 100);

        setContentPane(new ChartPanel(chart));
        setPreferredSize(new Dimension(850, 480));
        pack();
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        refresh();
        refreshTimer = new Timer(500, e -> refresh());
        refreshTimer.start();
    }

    @Override
    public void dispose() {
        refreshTimer.stop();
        super.dispose();
    }

    /** Vuelve a leer el historial de cada computador. */
    private void refresh() {
        for (int c = 0; c < computers.length; c++) {
            ComputerMetrics metrics = computers[c].getMetrics();
            int count = metrics.getCantidadRegistros();
            int step = Math.max(1, count / MAX_POINTS);

            series[c].setNotify(false);
            series[c].clear();
            for (int i = 0; i < count; i += step) {
                series[c].add(metrics.getCicloHistorial(i),
                        metrics.getUsoCPUVentana(i, WINDOW));
            }
            series[c].setNotify(true);
        }
    }
}
