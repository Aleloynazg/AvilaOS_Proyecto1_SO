package avilaos.gui;

import avilaos.hardware.Computer;
import avilaos.hardware.GlobalClock;
import avilaos.kernel.disk.DiskPolicyType;
import java.awt.BorderLayout;
import javax.swing.JFrame;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

/**
 * Gráfica del movimiento acumulado del cabezal contra el tiempo (ciclos). Cada vez que
 * se cambia la política de disco empieza una serie nueva, para comparar las políticas
 * en la misma gráfica. Los puntos los toma la ventana principal en cada refresco.
 */
public class HeadMovementChart {

    private final XYSeriesCollection dataset = new XYSeriesCollection();
    private final Computer computer;
    private XYSeries current;
    private DiskPolicyType currentPolicy;
    private long lastCycle = -1;
    private int segment = 1;

    public HeadMovementChart(Computer computer) {
        this.computer = computer;
    }

    /** Agrega un punto (ciclo, movimiento acumulado). Se llama desde el hilo de Swing. */
    public void sample(GlobalClock clock) {
        long cycle = clock.getCycle();
        if (cycle == lastCycle) {
            return;
        }
        DiskPolicyType policy = computer.getDiskPolicyType();
        if (current == null || policy != currentPolicy) {
            current = new XYSeries(segment + ". " + policy.getLabel(), true, true);
            dataset.addSeries(current);
            currentPolicy = policy;
            segment++;
        }
        current.addOrUpdate(cycle, computer.getDisk().getTotalHeadMovement());
        lastCycle = cycle;
    }

    /** Abre la ventana con la gráfica; se actualiza sola mientras está abierta. */
    public void mostrar(java.awt.Component parent) {
        JFreeChart chart = ChartFactory.createXYLineChart(
                "Movimiento acumulado del cabezal",
                "Ciclo", "Bloques recorridos (acumulado)",
                dataset, PlotOrientation.VERTICAL, true, true, false);
        JFrame frame = new JFrame("Gráfica del cabezal");
        frame.setLayout(new BorderLayout());
        frame.add(new ChartPanel(chart), BorderLayout.CENTER);
        frame.setSize(760, 480);
        frame.setLocationRelativeTo(parent);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setVisible(true);
    }
}