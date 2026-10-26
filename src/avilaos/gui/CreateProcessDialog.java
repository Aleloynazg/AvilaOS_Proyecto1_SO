/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.gui;
import avilaos.hardware.Computer;
import avilaos.kernel.process.PCB;
import avilaos.kernel.process.ProcessFactory;
import avilaos.kernel.process.ProcessType;
import java.awt.GridLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import avilaos.hardware.GlobalClock;
/**
 *
 * @author alexandraloynaz
 */
public class CreateProcessDialog {
    
private final Computer[] computers;
    private final GlobalClock clock;

    public CreateProcessDialog(Computer[] computers, GlobalClock clock) {
    this.computers = computers;
    this.clock = clock;
}
    public void mostrar() {
        JTextField nombre = new JTextField();
        JComboBox<Computer> computador = new JComboBox<>(computers);
        JComboBox<ProcessType> tipo = new JComboBox<>(
            new ProcessType[] {
                ProcessType.CPU_BOUND,
                ProcessType.IO_BOUND
            }
        );

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

        tipo.addActionListener(e -> {
            boolean esIO = tipo.getSelectedItem() == ProcessType.IO_BOUND;
            cadaIO.setEnabled(esIO);
            duracionIO.setEnabled(esIO);
        });

        cadaIO.setEnabled(false);
        duracionIO.setEnabled(false);

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
                int instruccionesValor =
                    leerPositivo(instrucciones, "instrucciones");

                ProcessType tipoSeleccionado =
                    (ProcessType) tipo.getSelectedItem();

                PCB proceso;

                if (tipoSeleccionado == ProcessType.IO_BOUND) {
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

