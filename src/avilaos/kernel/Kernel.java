package avilaos.kernel;

import avilaos.kernel.disk.DiskPolicyType;
import avilaos.kernel.disk.DiskRequest;
import avilaos.kernel.fs.FileIoManager;
import avilaos.kernel.fs.FileSystem;
import avilaos.kernel.fs.FileSystemException;
import avilaos.kernel.memory.MemoryManager;
import avilaos.kernel.process.BlockReason;
import avilaos.kernel.process.IoOperation;
import avilaos.kernel.process.PCB;
import avilaos.kernel.process.ProcessState;
import avilaos.kernel.process.TerminationCause;
import avilaos.structures.LinkedQueue;
import avilaos.hardware.ComputerMetrics;
import avilaos.hardware.disk.Disk;

/**
 * Núcleo del sistema operativo de UN computador. Maneja las colas de procesos (Nuevos, Listos,
 * Bloqueados, Terminados y, en el modelo de siete estados, Listos suspendidos y Bloqueados
 * suspendidos), la admisión por memoria (planificación a largo plazo) y la suspensión y
 * reactivación de procesos (planificación a mediano plazo).
 * La CPU y la política de planificación (corto plazo) son de la persona A.
 */
public class Kernel {
    
    private final int computerId;
    private final MemoryManager memory;
    private ComputerMetrics metrics;

    private final LinkedQueue<PCB> newQueue = new LinkedQueue<>();
    private final LinkedQueue<PCB> readyQueue = new LinkedQueue<>();
    private final LinkedQueue<PCB> blockedQueue = new LinkedQueue<>();
    private final LinkedQueue<PCB> terminatedQueue = new LinkedQueue<>();
    /** Procesos suspendidos: su memoria está en el área de swap del disco, no en la RAM. */
    private final LinkedQueue<PCB> readySuspendedQueue = new LinkedQueue<>();
    private final LinkedQueue<PCB> blockedSuspendedQueue = new LinkedQueue<>();

    /** Quienes escuchan los eventos (el log de la GUI, las métricas). */
    private final LinkedQueue<SystemEventListener> listeners = new LinkedQueue<>();
    
    /** Disco y sistema de archivos del computador (null si este kernel se usa sin disco). */
    private Disk disk;
    private FileSystem fileSystem;
    private FileIoManager fileIo;

    public Kernel(int computerId, MemoryManager memory) {
        this.computerId = computerId;
        this.memory = memory;
    }

    /** Conecta el kernel con el disco y el sistema de archivos que usan los procesos de E/S. */
    public void setStorage(Disk disk, FileSystem fileSystem) {
        this.disk = disk;
        this.fileSystem = fileSystem;
        this.fileIo = fileSystem == null ? null : new FileIoManager(fileSystem);
    }

    public void addEventListener(SystemEventListener listener) {
        listeners.enqueue(listener);
    }
    public void setMetrics(ComputerMetrics metrics) {
    this.metrics = metrics;
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
    
    public void contarTiempoEsperaReady() {
    Object[] procesos = readyQueue.toArray();

    for (Object objeto : procesos) {
        PCB proceso = (PCB) objeto;
        proceso.incrementCyclesWaitingReady();
    }
}
    
    /**
     * Termina un proceso: lo saca de la cola donde esté, libera su memoria
     * (si la tenía) y revisa si ahora cabe alguno de Nuevos.
     * Si el proceso estaba en la CPU, la persona A lo saca de la CPU antes.
     */
 public void terminateProcess(PCB process, TerminationCause cause, long cycle) {
    // Los Nuevos y los suspendidos no ocupan RAM (salvo los que están en tránsito hacia o desde el swap).
    boolean hadMemory = process.holdsMemory();

    // Si el proceso muere con una solicitud esperando en la cola del disco, se cancela:
    // no tiene sentido hacer una operación de archivos para un proceso que ya no existe.
    if (disk != null && disk.getScheduler() != null) {
        DiskRequest[] cancelled = disk.getScheduler().cancelRequestsOf(process);
        for (DiskRequest request : cancelled) {
            log(cycle, "se cancela la solicitud de " + process.getTag() + " en la cola del disco");
            releaseReservation(request, cycle);
        }
    }

    newQueue.remove(process);
    readyQueue.remove(process);
    blockedQueue.remove(process);
    readySuspendedQueue.remove(process);
    blockedSuspendedQueue.remove(process);

    if (hadMemory) {
        memory.free(process.getMemoryRequired());
    }
    // Si estaba suspendido (o a punto de estarlo) su espacio en el swap queda libre.
    releaseSwap(process);
    process.setSuspending(false);
    process.setResuming(false);

    process.terminate(cause, cycle);
    terminatedQueue.enqueue(process);

    if (metrics != null) {
        long tiempoRespuesta = -1;

        if (process.getArrivalCycle() >= 0
                && process.getFirstRunCycle() >= 0) {
            tiempoRespuesta = process.getFirstRunCycle()
                    - process.getArrivalCycle();
        }

        long tiempoEspera = process.getCyclesWaitingReady();

        metrics.registrarProcesoTerminado(
                cause == TerminationCause.DEADLINE_MISSED,
                tiempoRespuesta,
                tiempoEspera
        );
    }

    if (cause == TerminationCause.DEADLINE_MISSED) {
        log(cycle, process.getTag() + " termina: se venció su deadline");
    } else if (cause == TerminationCause.IO_FAILED) {
        log(cycle, process.getTag() + " termina con error de E/S");
    } else {
        log(cycle, process.getTag() + " termina correctamente");
    }

    admitNewProcesses(cycle);
}

    /** Un proceso se bloquea. cycles = cuánto dura (E/S); 0 si espera un evento sin tiempo fijo. */
    public void blockProcess(PCB process, BlockReason reason, int cycles, long cycle) {
        process.block(reason, cycles);
        process.setBlockedSinceCycle(cycle);
        blockedQueue.enqueue(process);
        log(cycle, process.getTag() + " se bloquea: " + reason.getLabel());
    }

    /**
     * Un proceso bloqueado vuelve a Listos. Si estaba suspendido (o se estaba suspendiendo)
     * pasa a Listo suspendido: sigue sin RAM hasta que el planificador a mediano plazo lo reactive.
     */
    public void unblockProcess(PCB process, long cycle) {
        if (blockedQueue.remove(process)) {
            process.unblock();
            if (process.isSuspending()) {
                // Su memoria ya viaja al swap: no vuelve a Listos, queda Listo suspendido.
                // Sigue ocupando la RAM hasta que el disco termine de escribirla.
                process.setState(ProcessState.READY_SUSPENDED);
                readySuspendedQueue.enqueue(process);
                log(cycle, process.getTag() + " se desbloquea mientras se suspende: queda Listo suspendido");
            } else {
                readyQueue.enqueue(process);
                log(cycle, process.getTag() + " se desbloquea y vuelve a Listos");
            }
        } else if (blockedSuspendedQueue.remove(process)) {
            process.unblock();
            process.setState(ProcessState.READY_SUSPENDED);
            readySuspendedQueue.enqueue(process);
            log(cycle, process.getTag() + " se desbloquea y pasa a Listo suspendido");
        }
    }

    // ------------------------------------------------------------------
    // Siete estados: suspender y reactivar
    // ------------------------------------------------------------------

    /**
     * Suspende un proceso: su memoria sale de la RAM y el proceso pasa a Bloqueado suspendido
     * (si estaba Bloqueado) o a Listo suspendido (si estaba Listo). La RAM que ocupaba queda libre.
     * También sirve para terminar de suspender a un proceso que ya estaba "en tránsito"
     * (su SUSPEND esperaba al disco y entretanto se desbloqueó): en ese caso ya era Listo
     * suspendido y solo falta liberar su RAM.
     *
     * No se puede suspender a un proceso Nuevo, Terminado, en Ejecución ni ya suspendido
     * (en esos casos no hace nada y devuelve false).
     *
     * @return true si el proceso quedó suspendido
     */
    public boolean suspendProcess(PCB process, long cycle) {
        ProcessState state = process.getState();
        if (state == ProcessState.READY_SUSPENDED && process.isSuspending()) {
            memory.free(process.getMemoryRequired());
            process.setSuspending(false);
            log(cycle, process.getTag() + " queda suspendido en el swap: libera "
                    + process.getMemoryRequired() + " MB. RAM libre: " + memory.getFreeMemory() + " MB");
            return true;
        }
        if (state == ProcessState.BLOCKED && blockedQueue.remove(process)) {
            process.setState(ProcessState.BLOCKED_SUSPENDED);
            blockedSuspendedQueue.enqueue(process);
        } else if (state == ProcessState.READY && readyQueue.remove(process)) {
            process.setState(ProcessState.READY_SUSPENDED);
            readySuspendedQueue.enqueue(process);
        } else {
            return false;
        }
        memory.free(process.getMemoryRequired());
        process.setSuspending(false);
        log(cycle, process.getTag() + " se suspende (" + process.getState().getLabel() + "): libera "
                + process.getMemoryRequired() + " MB. RAM libre: " + memory.getFreeMemory() + " MB");
        return true;
    }

    /**
     * Reactiva un proceso Listo suspendido: vuelve a la RAM y pasa a Listos, y su espacio en el
     * swap queda libre. Solo se reactivan los Listos suspendidos; un Bloqueado suspendido primero
     * debe desbloquearse (cuando termine su espera pasa a Listo suspendido).
     *
     * @param memoryAlreadyReserved true si la RAM ya se reservó al generar la solicitud de
     *                              reactivación (el caso normal del planificador a mediano plazo);
     *                              false para reservarla ahora
     * @return false si el proceso no es Listo suspendido o no hay RAM libre
     */
    public boolean resumeProcess(PCB process, long cycle, boolean memoryAlreadyReserved) {
        if (process.getState() != ProcessState.READY_SUSPENDED || process.isSuspending()
                || !readySuspendedQueue.remove(process)) {
            return false;
        }
        if (!memoryAlreadyReserved && !memory.allocate(process.getMemoryRequired())) {
            readySuspendedQueue.enqueue(process);   // sigue esperando
            return false;
        }
        releaseSwap(process);
        process.setResuming(false);
        process.setState(ProcessState.READY);
        readyQueue.enqueue(process);
        log(cycle, process.getTag() + " se reactiva y vuelve a Listos (" + process.getMemoryRequired()
                + " MB). RAM libre: " + memory.getFreeMemory() + " MB");
        return true;
    }

    /** Libera los bloques de swap del proceso (no hace nada si no tenía). */
    private void releaseSwap(PCB process) {
        if (disk != null && process.getSwapStartBlock() >= 0) {
            disk.freeBlocks(process);
        }
        process.setSwapStartBlock(-1);
    }

    // ------------------------------------------------------------------
    // E/S de archivos: solicitudes al disco
    // ------------------------------------------------------------------

    /**
     * Ejecuta la instrucción DISK_REQUEST de un proceso de E/S de archivos: revisa la
     * operación, manda la solicitud a la cola del disco y bloquea al proceso (motivo
     * "E/S del disco") hasta que el disco la atienda. El proceso ya debe haber salido de la CPU.
     *
     * Si la operación no se puede hacer (la ruta no existe, no hay espacio, nombre repetido...)
     * el proceso termina con {@link TerminationCause#IO_FAILED} y se explica el motivo en el log.
     *
     * @return true si el proceso quedó bloqueado esperando al disco; false si terminó por falla
     */
    public boolean submitFileRequest(PCB process, long cycle) {
        if (fileIo == null || disk == null || disk.getScheduler() == null) {
            log(cycle, process.getTag() + " no puede hacer " + process.describeOperation()
                    + ": el computador no tiene disco");
            terminateProcess(process, TerminationCause.IO_FAILED, cycle);
            return false;
        }
        DiskRequest request;
        try {
            request = fileIo.buildRequest(process, cycle);
        } catch (FileSystemException e) {
            log(cycle, process.getTag() + " no puede hacer " + process.describeOperation() + ": " + e.getMessage());
            terminateProcess(process, TerminationCause.IO_FAILED, cycle);
            return false;
        }
        blockProcess(process, BlockReason.DISK_IO, 0, cycle);
        disk.getScheduler().addRequest(request);
        log(cycle, process.getTag() + " pide al disco " + process.describeOperation() + " (bloque "
                + request.getTargetBlock() + ", " + request.getBlockCount()
                + (request.getBlockCount() == 1 ? " bloque)" : " bloques)"));
        return true;
    }

    /** El disco eligió una solicitud de la cola: se deja en el log con la política que la eligió. */
    public void onDiskRequestStarted(DiskRequest request, DiskPolicyType policy, long cycle) {
        String what = request.getOperation().name();
        if (request.getTargetPath() != null) {
            what += " " + request.getTargetPath();
        }
        log(cycle, "El disco atiende la solicitud de " + request.getProcess().getTag() + " (" + what
                + ") en el bloque " + request.getTargetBlock()
                + (policy != null ? " (" + policy.getLabel() + ")" : ""));
    }

    /**
     * El disco terminó una solicitud. Si es de un proceso de E/S de archivos, se aplica la
     * operación sobre el sistema de archivos (revisándola otra vez, porque el estado pudo
     * cambiar mientras esperaba) y se despierta al proceso; si ya no se puede hacer, el
     * proceso termina con IO_FAILED. Si el proceso ya había terminado (por ejemplo, por
     * deadline), la solicitud se descarta sin tocar el sistema de archivos.
     */
    public void onDiskRequestCompleted(DiskRequest request, long cycle) {
        PCB process = request.getProcess();
        IoOperation op = request.getOperation();
        boolean fileOperation = fileIo != null && process.getIoOperation() != null
                && (op == IoOperation.CREATE || op == IoOperation.READ
                    || op == IoOperation.UPDATE || op == IoOperation.DELETE);

        if (process.getState() == ProcessState.TERMINATED) {
            log(cycle, "El disco descarta la solicitud de " + process.getTag()
                    + ": el proceso ya había terminado");
            releaseReservation(request, cycle);
            return;
        }
        if (fileOperation) {
            try {
                log(cycle, "El disco termina la solicitud de " + process.getTag() + ": "
                        + fileIo.apply(request));
            } catch (FileSystemException e) {
                log(cycle, "La operación de " + process.getTag() + " (" + process.describeOperation()
                        + ") falla: " + e.getMessage());
                releaseReservation(request, cycle);
                terminateProcess(process, TerminationCause.IO_FAILED, cycle);
                return;
            }
        }
        unblockProcess(process, cycle);
        // Un proceso de una sola instrucción ya la ejecutó (la solicitud): termina al despertar.
        if (fileOperation && process.isFinished()
                && (process.getState() == ProcessState.READY
                    || process.getState() == ProcessState.READY_SUSPENDED)) {
            terminateProcess(process, TerminationCause.COMPLETED, cycle);
        }
    }

    /** Libera lo que reservó una solicitud que no se completa (el archivo de un CREATE y sus bloques). */
    private void releaseReservation(DiskRequest request, long cycle) {
        if (fileIo == null) {
            return;
        }
        int freed;
        try {
            freed = fileIo.release(request);
        } catch (FileSystemException e) {
            log(cycle, "no se pudo liberar lo reservado para " + request.getTargetPath() + ": " + e.getMessage());
            return;
        }
        if (freed > 0) {
            log(cycle, "se liberan los " + freed + " bloques reservados para " + request.getTargetPath());
        }
    }

    /** Resta un ciclo a los bloqueos con tiempo (E/S) y despierta a los que terminan. */
    public void tickTimedBlocks(long cycle) {
        tickTimedBlocksIn(blockedQueue, cycle);
        // La espera de un proceso suspendido sigue corriendo: el dispositivo no se detiene por estar en el swap.
        tickTimedBlocksIn(blockedSuspendedQueue, cycle);
    }

    private void tickTimedBlocksIn(LinkedQueue<PCB> queue, long cycle) {
        Object[] blocked = queue.toArray();
        for (Object o : blocked) {
            PCB p = (PCB) o;
            BlockReason reason = p.getBlockReason();
            if (reason == BlockReason.IO) {
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
        // El tiempo sigue corriendo para los suspendidos: si se les vence el deadline, se terminan.
        tickDeadlinesIn(readySuspendedQueue, cycle);
        tickDeadlinesIn(blockedSuspendedQueue, cycle);
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

    public Disk getDisk() { return disk; }
    public FileSystem getFileSystem() { return fileSystem; }
    public int getComputerId() { return computerId; }
    public MemoryManager getMemory() { return memory; }
    public LinkedQueue<PCB> getNewQueue() { return newQueue; }
    public LinkedQueue<PCB> getReadyQueue() { return readyQueue; }
    public LinkedQueue<PCB> getBlockedQueue() { return blockedQueue; }
    public LinkedQueue<PCB> getTerminatedQueue() { return terminatedQueue; }
    public LinkedQueue<PCB> getReadySuspendedQueue() { return readySuspendedQueue; }
    public LinkedQueue<PCB> getBlockedSuspendedQueue() { return blockedSuspendedQueue; }

}