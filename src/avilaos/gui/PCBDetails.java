package avilaos.gui;


import avilaos.kernel.process.PCB;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Timer;
/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 *
 * @author alexandraloynaz
 */

public class PCBDetails extends JDialog {

    private final PCB proceso;

    private final JLabel identificacion = new JLabel();
    private final JLabel nombre = new JLabel();
    private final JLabel computador = new JLabel();
    private final JLabel tipo = new JLabel();
    private final JLabel estado = new JLabel();
    private final JLabel bloqueo = new JLabel();
    private final JLabel terminacion = new JLabel();
    private final JLabel prioridad = new JLabel();
    private final JLabel deadline = new JLabel();
    private final JLabel memoria = new JLabel();
    private final JLabel instruccionesTotales = new JLabel();
    private final JLabel instruccionesRestantes = new JLabel();
    private final JLabel contadorPrograma = new JLabel();
    private final JLabel mar = new JLabel();
    private final JLabel pasoActual = new JLabel();
    private final JLabel cicloLlegada = new JLabel();
    private final JLabel primerCiclo = new JLabel();
    private final JLabel cicloFinalizacion = new JLabel();
    private final JLabel ciclosCPU = new JLabel();
    private final JLabel ciclosEspera = new JLabel();
    private final JLabel ciclosSemaforos = new JLabel();
    private final JLabel ciclosBloqueo = new JLabel();
    private final JLabel frecuenciaIO = new JLabel();
    private final JLabel duracionIO = new JLabel();
    private final JLabel buffer = new JLabel();
    private final JLabel ciclosElemento = new JLabel();
    private final JLabel elementosPendientes = new JLabel();
    private final JLabel elementosProcesados = new JLabel();

    private final Timer actualizador;

    public PCBDetails(Window propietario, PCB proceso) {
        super(propietario, "Detalles del PCB", ModalityType.MODELESS);

        this.proceso = proceso;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(520, 650);
        setLocationRelativeTo(propietario);
        setLayout(new BorderLayout(10, 10));

        JPanel contenido = new JPanel(new GridLayout(0, 2, 10, 7));
        contenido.setBorder(BorderFactory.createEmptyBorder(
                12, 12, 12, 12));

        agregarCampo(contenido, "Identificación:", identificacion);
        agregarCampo(contenido, "Nombre:", nombre);
        agregarCampo(contenido, "Computador:", computador);
        agregarCampo(contenido, "Tipo:", tipo);
        agregarCampo(contenido, "Estado:", estado);
        agregarCampo(contenido, "Motivo de bloqueo:", bloqueo);
        agregarCampo(contenido, "Causa de terminación:", terminacion);
        agregarCampo(contenido, "Prioridad:", prioridad);
        agregarCampo(contenido, "Deadline restante:", deadline);
        agregarCampo(contenido, "Memoria requerida:", memoria);
        agregarCampo(contenido, "Instrucciones totales:", instruccionesTotales);
        agregarCampo(contenido, "Instrucciones restantes:", instruccionesRestantes);
        agregarCampo(contenido, "Contador de programa (PC):", contadorPrograma);
        agregarCampo(contenido, "Registro MAR:", mar);
        agregarCampo(contenido, "Paso actual:", pasoActual);
        agregarCampo(contenido, "Ciclo de llegada:", cicloLlegada);
        agregarCampo(contenido, "Primer ciclo de ejecución:", primerCiclo);
        agregarCampo(contenido, "Ciclo de finalización:", cicloFinalizacion);
        agregarCampo(contenido, "Ciclos en CPU:", ciclosCPU);
        agregarCampo(contenido, "Ciclos esperando:", ciclosEspera);
        agregarCampo(contenido, "Ciclos bloqueado por semáforos:", ciclosSemaforos);
        agregarCampo(contenido, "Ciclos de bloqueo restantes:", ciclosBloqueo);
        agregarCampo(contenido, "Frecuencia de E/S:", frecuenciaIO);
        agregarCampo(contenido, "Duración de E/S:", duracionIO);
        agregarCampo(contenido, "ID del búfer:", buffer);
        agregarCampo(contenido, "Ciclos por elemento:", ciclosElemento);
        agregarCampo(contenido, "Elementos por procesar:", elementosPendientes);
        agregarCampo(contenido, "Elementos procesados:", elementosProcesados);

        add(new JScrollPane(contenido), BorderLayout.CENTER);

        // Mostrar los valores desde el momento en que se abre.
        actualizarDetalles();

        // Refrescar los datos cada segundo.
        actualizador = new Timer(1000, e -> actualizarDetalles());
        actualizador.start();

        // Detener el temporizador al cerrar la ventana.
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                actualizador.stop();
            }
        });
    }

    private void agregarCampo(
            JPanel panel, String etiqueta, JLabel valor) {

        panel.add(new JLabel(etiqueta));
        panel.add(valor);
    }

    private void actualizarDetalles() {
        identificacion.setText(proceso.getTag());
        nombre.setText(proceso.getName());
        computador.setText(String.valueOf(proceso.getComputerId()));
        tipo.setText(String.valueOf(proceso.getType()));
        estado.setText(proceso.getState().getLabel());
        bloqueo.setText(proceso.getBlockReason().getLabel());
        terminacion.setText(String.valueOf(proceso.getTerminationCause()));
        prioridad.setText(String.valueOf(proceso.getPriority()));
        deadline.setText(String.valueOf(proceso.getDeadline()));
        memoria.setText(proceso.getMemoryRequired() + " MB");

        instruccionesTotales.setText(
                String.valueOf(proceso.getTotalInstructions()));
        instruccionesRestantes.setText(
                String.valueOf(proceso.getRemainingInstructions()));
        contadorPrograma.setText(
                String.valueOf(proceso.getProgramCounter()));
        mar.setText(String.valueOf(proceso.getMar()));
        pasoActual.setText(String.valueOf(proceso.getCurrentStep()));

        cicloLlegada.setText(String.valueOf(proceso.getArrivalCycle()));
        primerCiclo.setText(String.valueOf(proceso.getFirstRunCycle()));
        cicloFinalizacion.setText(String.valueOf(proceso.getFinishCycle()));

        ciclosCPU.setText(String.valueOf(proceso.getCyclesOnCpu()));
        ciclosEspera.setText(
                String.valueOf(proceso.getCyclesWaitingReady()));
        ciclosSemaforos.setText(
                String.valueOf(proceso.getCyclesBlockedOnSemaphores()));
        ciclosBloqueo.setText(
                String.valueOf(proceso.getBlockedCyclesLeft()));

        frecuenciaIO.setText(
                "Cada " + proceso.getIoEveryCycles() + " ciclos");
        duracionIO.setText(
                proceso.getIoDurationCycles() + " ciclos");
        buffer.setText(String.valueOf(proceso.getBufferId()));
        ciclosElemento.setText(
                String.valueOf(proceso.getCyclesPerItem()));
        elementosPendientes.setText(
                String.valueOf(proceso.getItemsToProcess()));
        elementosProcesados.setText(
                String.valueOf(proceso.getItemsProcessed()));
    }
}