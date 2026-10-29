package avilaos.gui;

import avilaos.hardware.Cluster;
import avilaos.kernel.process.PCB;
import avilaos.kernel.sync.AvilaSemaphore;
import avilaos.kernel.sync.Buffer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

/**
 * Muestra el estado de todos los buffers del clúster: elementos, capacidad,
 * valores de sus semáforos y procesos esperando. Solo observa: no cambia nada.
 */
public class BufferPanel extends JPanel {

    private static final String[] COLUMNAS = {
        "Buffer", "Anfitrión", "Elementos", "s", "n", "e", "Esperando", "Prod.", "Cons."
    };

    private final Cluster cluster;
    private final DefaultTableModel modelo = new DefaultTableModel(COLUMNAS, 0) {
        @Override
        public boolean isCellEditable(int fila, int columna) {
            return false;
        }
    };

    public BufferPanel(Cluster cluster) {
        this.cluster = cluster;
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Buffers"));

        JTable tabla = new JTable(modelo);
        int[] anchos = {55, 70, 75, 30, 30, 30, 190, 45, 45};
        for (int i = 0; i < anchos.length; i++) {
            tabla.getColumnModel().getColumn(i).setPreferredWidth(anchos[i]);
        }
        JScrollPane scroll = new JScrollPane(tabla);
        scroll.setPreferredSize(new Dimension(580, 200));
        add(scroll, BorderLayout.CENTER);
    }

    /** Vuelve a leer los buffers. Lo llama el Timer de la ventana cada 200 ms. */
    public void refresh() {
        Object[] buffers = cluster.getBuffers();
        modelo.setRowCount(0);
        for (int i = 0; i < buffers.length; i++) {
            Buffer b = (Buffer) buffers[i];
            modelo.addRow(new Object[] {
                b.getTag(),
                "H" + b.getHostComputerId(),
                b.getCount() + " / " + b.getCapacity(),
                b.getMutex().getValue(),
                b.getFull().getValue(),
                b.getEmpty().getValue(),
                esperando(b),
                b.getProduced(),
                b.getConsumed()
            });
        }
    }

    /** Ejemplo: "n: P3-H2  e: P1-H1". Vacío si nadie espera. */
    private String esperando(Buffer b) {
        String texto = "";
        texto += cola("s", b.getMutex());
        texto += cola("n", b.getFull());
        texto += cola("e", b.getEmpty());
        return texto.trim();
    }

    private String cola(String simbolo, AvilaSemaphore sem) {
        Object[] procesos = sem.getWaitingProcesses();
        if (procesos.length == 0) {
            return "";
        }
        String texto = simbolo + ":";
        for (int i = 0; i < procesos.length; i++) {
            texto += " " + ((PCB) procesos[i]).getTag();
        }
        return texto + "  ";
    }
}