package avilaos.kernel.sync;

import avilaos.kernel.process.BlockReason;
import avilaos.kernel.process.BufferSemaphore;
import avilaos.kernel.process.PCB;
import avilaos.structures.LinkedQueue;
import java.util.concurrent.Semaphore;

/**
 * Semáforo de ÁvilaOS (simulado). Bloquea PROCESOS, no hilos de Java.
 * Sigue la definición de Stallings: si el valor queda negativo, su valor
 * absoluto es la cantidad de procesos esperando en la cola.
 */
public class AvilaSemaphore {
    
    private final String name;              // por ejemplo "B1.e"
    private final BufferSemaphore role;     // MUTEX (s), FULL (n) o EMPTY (e)
    private int value;
    private final LinkedQueue<PCB> waiting = new LinkedQueue<>();

    /** Protege value y la cola: varios hilos de Java pueden usar el mismo semáforo. */
    private final Semaphore mutex = new Semaphore(1);

    public AvilaSemaphore(String name, BufferSemaphore role, int initialValue) {
        if (initialValue < 0) {
            throw new IllegalArgumentException("El valor inicial no puede ser negativo");
        }
        this.name = name;
        this.role = role;
        this.value = initialValue;
    }
    
    /**
     * semWait: resta 1. Si el valor queda negativo, el proceso entra a la cola
     * del semáforo y devuelve true: quien llama debe bloquearlo en su Kernel.
     */
    public boolean semWait(PCB process) {
        mutex.acquireUninterruptibly();
        try {
            value--;
            if (value < 0) {
                waiting.enqueue(process);
                return true;
            }
            return false;
        } finally {
            mutex.release();
        }
    }

    /**
     * semSignal: suma 1. Si había procesos esperando, saca al primero (FIFO)
     * y lo devuelve: quien llama debe desbloquearlo en SU Kernel.
     * Devuelve null si nadie esperaba.
     */
    public PCB semSignal() {
        mutex.acquireUninterruptibly();
        try {
            value++;
            if (value <= 0) {
                return waiting.dequeue();
            }
            return null;
        } finally {
            mutex.release();
        }
    }
    
    
    /**
     * Saca de la cola a un proceso que ya no espera (por ejemplo, se le venció
     * el deadline estando bloqueado). Deshace su semWait. Devuelve true si estaba.
     */
    public boolean cancelWait(PCB process) {
        mutex.acquireUninterruptibly();
        try {
            if (waiting.remove(process)) {
                value++;
                return true;
            }
            return false;
        } finally {
            mutex.release();
        }
    }

    public int getValue() {
        mutex.acquireUninterruptibly();
        try {
            return value;
        } finally {
            mutex.release();
        }
    }

    /** Copia de los procesos que esperan (para la GUI). */
    public Object[] getWaitingProcesses() {
        return waiting.toArray();
    }

    public String getName() { return name; }

    public BufferSemaphore getRole() { return role; }

    /** Motivo con el que se bloquea un proceso que espera en este semáforo. */
    public BlockReason getBlockReason() { return role.getBlockReason(); }

    @Override
    public String toString() {
        return name + " = " + getValue();
    }

}