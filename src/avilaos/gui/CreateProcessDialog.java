package avilaos.gui;

import avilaos.hardware.Cluster;
import avilaos.hardware.Computer;
import avilaos.hardware.GlobalClock;
import avilaos.kernel.process.PCB;
import avilaos.kernel.process.ProcessFactory;
import avilaos.kernel.process.ProcessType;
import avilaos.kernel.sync.Buffer;
import java.awt.GridLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * Formulario para crear procesos: CPU bound, I/O bound y, si hay clúster,
 * productores y consumidores.
 * @author alexandraloynaz
 */
public class CreateProcessDialog {

    private final Computer[] computers;
    private final GlobalClock clock;
    private final Cluster cluster;   // null = sin buffers (solo CPU bound e I/O bound)

    public CreateProcessDialog(Computer[] computers, GlobalClock clock) {
        this(computers, clock, null);
    }

    public CreateProcessDialog(Computer[] computers, GlobalClock clock, Cluster cluster) {
        this.computers = computers;
        this.clock = clock;
        this.cluster = cluster;
    }

    public void mostrar() {
        JTextField nombre = new JTextField();
        JComboBox<Computer> computador = new JComboBox<>(computers);
        JComboBox<ProcessType> tipo = new JComboBox<>(
            cluster == null
                ? new ProcessType[] { ProcessType.CPU_BOUND, ProcessType.IO_BOUND }
                : ProcessType.values()
        );

        // Buffers que existen ahora (para productores y consumidores)
        Object[] buffers = cluster == null ? new Object[0] : cluster.getBuffers();
        String[] nombresBuffers = new String[buffers.length];
        for (int i = 0; i < buffers.length; i++) {
            Buffer b = (Buffer) buffers[i];
            nombresBuffers[i] = b.getTag() + " (en H" + b.getHostComputerId()
                    + ", capacidad " + b.getCapacity() + ")";
        }
        JComboBox<String> buffer = new JComboBox<>(nombresBuffers);
        JTextField ciclosPorElemento = new JTextField("2");
        JTextField elementos = new JTextField("5");

        JTextField prioridad = new JTextField("1");
        JTextField memoria = new JTextField("200");
        JTextField deadline = new JTextField("100");
        JTextField instrucciones = new JTextField("20");
        JTextField cadaIO = new JTextField("5");
        JTextField duracionIO = new JTextField("3");

        JPanel formulario = new JPanel(new GridLayout(0, 2, 8, 8));

        formulario.add(new JLabel("Nombre:"));
        formulario.add(nombre);

        formulario.add(new JLabel("Computador:"));
        formulario.add(computador);

        formulario.add(new JLabel("Tipo:"));
        formulario.add(tipo);

        formulario.add(new JLabel("Prioridad:"));
        formulario.add(prioridad);

        formulario.add(new JLabel("Memoria (MB):"));
        formulario.add(memoria);

        formulario.add(new JLabel("Deadline (ciclos):"));
        formulario.add(deadline);

        formulario.add(new JLabel("Instrucciones:"));
        formulario.add(instrucciones);

        formulario.add(new JLabel("Pedir E/S cada N instrucciones:"));
        formulario.add(cadaIO);

        formulario.add(new JLabel("Duración de E/S (ciclos):"));
        formulario.add(duracionIO);

        formulario.add(new JLabel("Buffer:"));
        formulario.add(buffer);

        formulario.add(new JLabel("Ciclos por elemento (k):"));
        formulario.add(ciclosPorElemento);

        formulario.add(new JLabel("Elementos a producir/consumir:"));
        formulario.add(elementos);

        // Activa solo los campos que usa el tipo elegido
        Runnable actualizarCampos = () -> {
            ProcessType t = (ProcessType) tipo.getSelectedItem();
            boolean esIO = t == ProcessType.IO_BOUND;
            boolean usaBuffer = t == ProcessType.PRODUCER || t == ProcessType.CONSUMER;
            cadaIO.setEnabled(esIO);
            duracionIO.setEnabled(esIO);
            instrucciones.setEnabled(!usaBuffer);   // se calculan solas: elementos x (k + 5)
            buffer.setEnabled(usaBuffer);
            ciclosPorElemento.setEnabled(usaBuffer);
            elementos.setEnabled(usaBuffer);
        };
        tipo.addActionListener(e -> actualizarCampos.run());
        actualizarCampos.run();

        while (true) {
            int opcion = JOptionPane.showConfirmDialog(
                null,
                formulario,
                "Crear proceso",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
            );

            if (opcion != JOptionPane.OK_OPTION) {
                return;
            }

            try {
                String nombreProceso = nombre.getText().trim();

                if (nombreProceso.isEmpty()) {
                    throw new IllegalArgumentException(
                        "Debes introducir el nombre del proceso."
                    );
                }

                Computer seleccionado = (Computer) computador.getSelectedItem();

                if (seleccionado == null) {
                    throw new IllegalArgumentException(
                        "Debes seleccionar un computador."
                    );
                }

                int prioridadValor = leerPositivo(prioridad, "prioridad");
                int memoriaValor = leerPositivo(memoria, "memoria");
                int deadlineValor = leerPositivo(deadline, "deadline");
                ProcessType tipoSeleccionado =
                    (ProcessType) tipo.getSelectedItem();

                PCB proceso;

                if (tipoSeleccionado == ProcessType.PRODUCER
                        || tipoSeleccionado == ProcessType.CONSUMER) {
                    if (buffers.length == 0) {
                        throw new IllegalArgumentException(
                            "Primero crea un buffer con el botón \"Crear buffer\"."
                        );
                    }
                    Buffer elegido = (Buffer) buffers[buffer.getSelectedIndex()];
                    int k = leerPositivo(ciclosPorElemento, "ciclos por elemento");
                    int cantidad = leerPositivo(elementos, "elementos");

                    if (tipoSeleccionado == ProcessType.PRODUCER) {
                        proceso = ProcessFactory.producer(nombreProceso, seleccionado.getId(),
                            prioridadValor, memoriaValor, deadlineValor, elegido.getId(), k, cantidad);
                    } else {
                        proceso = ProcessFactory.consumer(nombreProceso, seleccionado.getId(),
                            prioridadValor, memoriaValor, deadlineValor, elegido.getId(), k, cantidad);
                    }
                } else if (tipoSeleccionado == ProcessType.IO_BOUND) {
                    int instruccionesValor =
                        leerPositivo(instrucciones, "instrucciones");
                    int cadaIOValor = leerPositivo(cadaIO, "cada cuántas instrucciones");
                    int duracionIOValor = leerPositivo(duracionIO, "duración de E/S");

                    proceso = ProcessFactory.ioBound(
                        nombreProceso,
                        seleccionado.getId(),
                        prioridadValor,
                        memoriaValor,
                        deadlineValor,
                        instruccionesValor,
                        cadaIOValor,
                        duracionIOValor
                    );
                } else {
                    int instruccionesValor =
                        leerPositivo(instrucciones, "instrucciones");
                    proceso = ProcessFactory.cpuBound(
                        nombreProceso,
                        seleccionado.getId(),
                        prioridadValor,
                        memoriaValor,
                        deadlineValor,
                        instruccionesValor
                    );
                }

                boolean aceptado = seleccionado.getKernel().submitProcess(proceso, clock.getCycle());

                if (!aceptado) {
                    JOptionPane.showMessageDialog(
                        null,
                        "El proceso fue rechazado. La memoria solicitada "
                        + "supera la RAM total del computador.",
                        "No se pudo crear el proceso",
                        JOptionPane.WARNING_MESSAGE
                    );
                    return;
                }

                JOptionPane.showMessageDialog(
                    null,
                    "Proceso agregado correctamente a la cola de nuevos.",
                    "Proceso creado",
                    JOptionPane.INFORMATION_MESSAGE
                );

                return;

            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(
                    null,
                    "Los campos numéricos deben contener números enteros.",
                    "Dato inválido",
                    JOptionPane.ERROR_MESSAGE
                );
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(
                    null,
                    ex.getMessage(),
                    "Dato inválido",
                    JOptionPane.ERROR_MESSAGE
                );
            }
        }
    }

    private int leerPositivo(JTextField campo, String nombreCampo) {
        int valor = Integer.parseInt(campo.getText().trim());

        if (valor <= 0) {
            throw new IllegalArgumentException(
                "El campo '" + nombreCampo + "' debe ser mayor que cero."
            );
        }

        return valor;
    }
}