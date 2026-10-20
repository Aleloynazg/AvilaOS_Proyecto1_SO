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
    
    
    public void reportEvent(long cycle, String message) {
        log(cycle, message);
    }
    /**
     * Un proceso nuevo llega a este computador. Entra a la cola de nuevos.
     * Devuelve false si pide más memoria que la RAM total (nunca cabría).
     */
    public boolean submitProcess(PCB process, long cycle) {
        if (!memory.fitsInTotal(process.getMemoryRequired())) {
            log(cycle, "rechaza " + process.getTag() + ": pide "
                    + process.getMemoryRequired() + " MB y la RAM total es "
                    + memory.getTotalMemory() + " MB");
            return false;
        }
        process.setState(ProcessState.NEW);
        process.setArrivalCycle(cycle);
        newQueue.enqueue(process);
        log(cycle, process.getTag() + " llega a la cola de nuevos");
        return true;
    }
    
    /**
     * Pasa a Listos los procesos de Nuevos que quepan en memoria, en orden
     * de llegada. Se detiene en el primero que no cabe (para que un proceso
     * grande no espere para siempre mientras pasan los pequeños).
     * Devuelve cuántos admitió.
     */
    public int admitNewProcesses(long cycle) {
        int admitted = 0;
        PCB first = newQueue.peek();
        while (first != null && memory.allocate(first.getMemoryRequired())) {
            newQueue.dequeue();
            first.setState(ProcessState.READY);
            readyQueue.enqueue(first);
            admitted++;
            log(cycle, "admite " + first.getTag() + " (" + first.getMemoryRequired()
                    + " MB). RAM libre: " + memory.getFreeMemory() + " MB");
            first = newQueue.peek();
        }
        return admitted;
    }
    
    /**
     * Termina un proceso: lo saca de la cola donde esté, libera su memoria
     * (si la tenía) y revisa si ahora cabe alguno de Nuevos.
     * Si el proceso estaba en la CPU, la persona A lo saca de la CPU antes.
     */
    public void terminateProcess(PCB process, TerminationCause cause, long cycle) {
        boolean hadMemory = process.getState() != ProcessState.NEW;
        newQueue.remove(process);
        readyQueue.remove(process);
        blockedQueue.remove(process);
        if (hadMemory) {
            memory.free(process.getMemoryRequired());
        }
        process.terminate(cause, cycle);
        terminatedQueue.enqueue(process);
        if (cause == TerminationCause.DEADLINE_MISSED) {
            log(cycle, process.getTag() + " termina: se venció su deadline");
        } else {
            log(cycle, process.getTag() + " termina correctamente");
        }
        admitNewProcesses(cycle);
    }
    
    /** Un proceso se bloquea. cycles = cuánto dura (E/S, red); 0 si espera un semáforo. */
    public void blockProcess(PCB process, BlockReason reason, int cycles, long cycle) {
        process.block(reason, cycles);
        blockedQueue.enqueue(process);
        log(cycle, process.getTag() + " se bloquea: " + reason.getLabel());
    }

    /** Un proceso bloqueado vuelve a Listos. */
    public void unblockProcess(PCB process, long cycle) {
        if (blockedQueue.remove(process)) {
            process.unblock();
            readyQueue.enqueue(process);
            log(cycle, process.getTag() + " se desbloquea y vuelve a Listos");
        }
    }

    /** Resta un ciclo a los bloqueos con tiempo (E/S y red) y despierta a los que terminan. */
    public void tickTimedBlocks(long cycle) {
        Object[] blocked = blockedQueue.toArray();
        for (Object o : blocked) {
            PCB p = (PCB) o;
            BlockReason reason = p.getBlockReason();
            if (reason == BlockReason.IO || reason == BlockReason.NETWORK) {
                p.decrementBlockedCycles();
                if (p.getBlockedCyclesLeft() == 0) {
                    unblockProcess(p, cycle);
                }
            }
        }
    }
    
    
    /**
     * Resta un ciclo al deadline de los procesos en Nuevos, Listos y Bloqueados.
     * Los que llegan a 0 se terminan. (El proceso en CPU lo maneja la persona A.)
     */
    public void tickDeadlines(long cycle) {
        tickDeadlinesIn(newQueue, cycle);
        tickDeadlinesIn(readyQueue, cycle);
        tickDeadlinesIn(blockedQueue, cycle);
    }

    private void tickDeadlinesIn(LinkedQueue<PCB> queue, long cycle) {
        Object[] processes = queue.toArray();
        for (Object o : processes) {
            PCB p = (PCB) o;
            if (p.tickDeadline()) {
                terminateProcess(p, TerminationCause.DEADLINE_MISSED, cycle);
            }
        }
    }

    public int getComputerId() { return computerId; }
    public MemoryManager getMemory() { return memory; }
    public LinkedQueue<PCB> getNewQueue() { return newQueue; }
    public LinkedQueue<PCB> getReadyQueue() { return readyQueue; }
    public LinkedQueue<PCB> getBlockedQueue() { return blockedQueue; }
    public LinkedQueue<PCB> getTerminatedQueue() { return terminatedQueue; }

}