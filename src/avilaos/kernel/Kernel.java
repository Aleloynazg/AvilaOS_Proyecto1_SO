package avilaos.kernel;

import avilaos.kernel.memory.MemoryManager;
import avilaos.kernel.process.BlockReason;
import avilaos.kernel.process.PCB;
import avilaos.kernel.process.ProcessState;
import avilaos.kernel.process.TerminationCause;
import avilaos.structures.LinkedQueue;

/**
 * Núcleo del sistema operativo de UN computador. Maneja las 4 colas de
 * procesos y la admisión por memoria (planificación a largo plazo).
 * La CPU y la política de planificación (corto plazo) son de la persona A.
 */
public class Kernel {
    
    private final int computerId;
    private final MemoryManager memory;

    private final LinkedQueue<PCB> newQueue = new LinkedQueue<>();
    private final LinkedQueue<PCB> readyQueue = new LinkedQueue<>();
    private final LinkedQueue<PCB> blockedQueue = new LinkedQueue<>();
    private final LinkedQueue<PCB> terminatedQueue = new LinkedQueue<>();

    /** Quienes escuchan los eventos (el log de la GUI, las métricas). */
    private final LinkedQueue<SystemEventListener> listeners = new LinkedQueue<>();

    public Kernel(int computerId, MemoryManager memory) {
        this.computerId = computerId;
        this.memory = memory;
    }

    public void addEventListener(SystemEventListener listener) {
        listeners.enqueue(listener);
    }

    private void log(long cycle, String message) {
        Object[] all = listeners.toArray();
        for (Object o : all) {
            ((SystemEventListener) o).onEvent(cycle, "Computador " + computerId + ": " + message);
        }
    }

}