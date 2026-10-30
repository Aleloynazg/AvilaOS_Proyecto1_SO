/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.hardware;

/**
 * * Representa la unidad central de procesamiento (CPU) de un computador
 * dentro del simulador AvilaOS.
 *
 * La CPU administra el proceso que se encuentra en ejecución y controla
 * el modo de funcionamiento del procesador (usuario o núcleo). También
 * permite sincronizar el acceso a sus recursos y registrar los eventos
 * producidos durante la ejecución de los procesos.
 *
 * Implementa ClockListener para recibir los ciclos del reloj global.
 *
 * @author alexandraloynaz
 */

import avilaos.kernel.SystemEventListener;
import avilaos.kernel.process.PCB;
import avilaos.kernel.process.ProcessState;
import java.util.concurrent.Semaphore;

public class CPU implements ClockListener {

    private final int computerId;
    private PCB currentProcess;
    private CpuMode mode;

    private final Semaphore semaforo = new Semaphore(1);
    private SystemEventListener eventListener;

     /**
     * Construye CPU asociada a un computador específico.
     *
     * Inicializa la CPU sin procesos asignados y en modo núcleo.
     *
     * @param computerId identificador del computador al que pertenece la CPU.
     */
    public CPU(int computerId) {
        this.computerId = computerId;
        this.currentProcess = null;
        this.mode = CpuMode.KERNEL;
        this.eventListener = null;
    }

        /**
     * Establece el componente encargado de recibir los eventos generados
     * por la CPU.
     *
     * Utiliza el semáforo para garantizar que la modificación del listener
     * se realice de manera segura.
     *
     * @param listener componente que recibirá los eventos de la CPU.
     */
    public void setEventListener(SystemEventListener listener) {
        semaforo.acquireUninterruptibly();

        try {
            this.eventListener = listener;
        } finally {
            semaforo.release();
        }
    }
    /**
     * Registra un evento relacionado con la ejecución de la CPU.
     *
     * Si existe un listener configurado, le envía el ciclo en el que
     * ocurrió el evento y el mensaje correspondiente.
     *
     * @param cycle ciclo del reloj global en el que ocurrió el evento.
     * @param message descripción del evento generado.
     */
    private void registrarEvento(long cycle, String message) {
        if (eventListener != null) {
            eventListener.onEvent(cycle, message);
        }
    }
    /**
     * Asigna un proceso a la CPU para comenzar su ejecución.
     *
     * La asignación solamente se realiza si el proceso no es null,
     * la CPU se encuentra libre y el proceso está en estado READY.
     * Cuando la asignación es exitosa, el proceso cambia a RUNNING
     * y la CPU pasa al modo USER.
     *
     * @param process proceso que se desea asignar a la CPU.
     * @return true si el proceso fue asignado correctamente;
     *         false si la CPU está ocupada, el proceso es null
     *         o no se encuentra en estado READY.
     */
    public boolean assignProcess(PCB process) {
        semaforo.acquireUninterruptibly();

        try {
            if (process == null || currentProcess != null) {
                return false;
            }

            if (process.getState() != ProcessState.READY) {
                return false;
            }

            currentProcess = process;
            currentProcess.setState(ProcessState.RUNNING);
            mode = CpuMode.USER;

            return true;
        } finally {
            semaforo.release();
        }
    }
    /**
     * Libera el proceso que se encuentra actualmente asignado a la CPU.
     *
     * La CPU deja de tener un proceso en ejecución y cambia al modo KERNEL.
     * El proceso liberado se devuelve para que otro componente pueda
     * gestionar su estado o ubicación en las colas del sistema.
     *
     * @return proceso que estaba asignado a la CPU, o null si estaba libre.
     */
    public PCB releaseProcess() {
        semaforo.acquireUninterruptibly();

        try {
            PCB process = currentProcess;

            currentProcess = null;
            mode = CpuMode.KERNEL;

            return process;
        } finally {
            semaforo.release();
        }
    }
    /**
     * Establece el modo de funcionamiento de la CPU.
     *
     * El cambio solamente se realiza cuando el modo recibido no es null.
     * El acceso al atributo se protege mediante el semáforo.
     *
     * @param mode nuevo modo de funcionamiento de la CPU.
     */
    public void setMode(CpuMode mode) {
        semaforo.acquireUninterruptibly();

        try {
            if (mode != null) {
                this.mode = mode;
            }
        } finally {
            semaforo.release();
        }
    }
    /**
     * Obtiene el proceso que se encuentra actualmente asignado a la CPU.
     *
     * @return proceso actual, o null si la CPU está libre.
     */
    public PCB getCurrentProcess() {
        semaforo.acquireUninterruptibly();

        try {
            return currentProcess;
        } finally {
            semaforo.release();
        }
    }
    /**
     * Obtiene el modo actual de funcionamiento de la CPU.
     *
     * @return modo actual, que puede ser USER o KERNEL.
     */
    public CpuMode getMode() {
        semaforo.acquireUninterruptibly();

        try {
            return mode;
        } finally {
            semaforo.release();
        }
    }
    
        /**
     * Obtiene el identificador del computador al que pertenece esta CPU.
     *
     * @return identificador del computador.
     */

    public int getComputerId() {
        return computerId;
    }
    /**
     * Comprueba si la CPU se encuentra libre.
     *
     * Una CPU está libre cuando no tiene ningún proceso asignado.
     *
     * @return true si no hay un proceso asignado; false en caso contrario.
     */
    public boolean isIdle() {
        semaforo.acquireUninterruptibly();

        try {
            return currentProcess == null;
        } finally {
            semaforo.release();
        }
    }
    /**
     * Ejecuta las operaciones correspondientes a un ciclo del reloj global.
     *
     * Si la CPU se encuentra en modo USER y tiene un proceso asignado
     * que todavía no ha finalizado, ejecuta una instrucción del proceso.
     * También actualiza los ciclos de CPU consumidos, registra el primer
     * ciclo de ejecución y notifica el evento generado.
     *
     * Si la CPU está en modo KERNEL, no tiene un proceso asignado
     * o el proceso ya finalizó, no realiza ninguna instrucción.
     *
     * El acceso a los recursos de la CPU se protege mediante el semáforo.
     *
     * @param cycle número del ciclo recibido del reloj global.
     */
    @Override
    public void onTick(long cycle) {
        semaforo.acquireUninterruptibly();

        try {

            if (mode != CpuMode.USER || currentProcess == null) {
                return;
            }

            if (currentProcess.isFinished()) {
                return;
            }

            currentProcess.advance();
            currentProcess.incrementCyclesOnCpu();
            currentProcess.markFirstRun(cycle);

            registrarEvento(
                cycle,
                "ejecuta " + currentProcess.getTag()
                + " | PC: " + currentProcess.getProgramCounter()
                + " | Instrucciones restantes: "
                + currentProcess.getRemainingInstructions()
            );
        } finally {
            semaforo.release();
        }
    }
}