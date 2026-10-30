package avilaos.gui;

import avilaos.hardware.Cluster;
import avilaos.hardware.Computer;
import avilaos.hardware.GlobalClock;
import avilaos.kernel.sync.Buffer;
import java.awt.GridLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

/** Formulario para crear un buffer: capacidad y computador anfitrión. */
public class CreateBufferDialog {

    private final Cluster cluster;
    private final GlobalClock clock;

    public CreateBufferDialog(Cluster cluster, GlobalClock clock) {
        this.cluster = cluster;
        this.clock = clock;
    }

    public void mostrar() {
        JTextField capacidad = new JTextField("5");
        JComboBox<Computer> anfitrion = new JComboBox<>(cluster.getComputers());

        JPanel formulario = new JPanel(new GridLayout(0, 2, 8, 8));
        formulario.add(new JLabel("Capacidad (elementos):"));
        formulario.add(capacidad);
        formulario.add(new JLabel("Computador anfitrión:"));
        formulario.add(anfitrion);
        formulario.add(new JLabel("Memoria que ocupa:"));
        formulario.add(new JLabel(Buffer.MB_PER_SLOT + " MB por elemento"));

        while (true) {
            int opcion = JOptionPane.showConfirmDialog(null, formulario, "Crear buffer",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (opcion != JOptionPane.OK_OPTION) {
                return;
            }
            try {
                int capacidadValor = Integer.parseInt(capacidad.getText().trim());
                if (capacidadValor < 1 || capacidadValor > 50) {
                    throw new IllegalArgumentException(
                            "La capacidad debe estar entre 1 y 50 elementos.");
                }
                Computer host = (Computer) anfitrion.getSelectedItem();

                Buffer buffer = cluster.createBuffer(capacidadValor, host.getId());
                if (buffer == null) {
                    JOptionPane.showMessageDialog(null,
                            "H" + host.getId() + " no tiene RAM libre suficiente para un buffer de "
                            + capacidadValor + " elementos.",
                            "No se pudo crear el buffer", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                host.getKernel().reportEvent(clock.getCycle(), "se crea el buffer " + buffer.getTag()
                        + " (capacidad " + buffer.getCapacity() + ", " + buffer.getMemorySize() + " MB)");
                JOptionPane.showMessageDialog(null, "Buffer " + buffer.getTag() + " creado en H" + host.getId() + ".",
                        "Buffer creado", JOptionPane.INFORMATION_MESSAGE);
                return;
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(null, "La capacidad debe ser un número entero.",
                        "Dato inválido", JOptionPane.ERROR_MESSAGE);
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(null, ex.getMessage(), "Dato inválido", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}