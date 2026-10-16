package avilaos.kernel.process;

/**
 * Bloque de control de proceso. ÁvilaOS es monohilo por proceso, así que el
 * PCB integra el TCB (relación 1:1).
 *
 * Campos mínimos pedidos por el enunciado: ID, nombre, computador, estado,
 * tipo, prioridad, memoria, deadline y tiempo restante.
 */
public class PCB {

    // ---- Identificación ----
    private final int id;
    private final String name;
    private final int computerId;
    private final ProcessType type;

    // ---- Estado y planificación ----
    private ProcessState state = ProcessState.NEW;
    private BlockReason blockReason = BlockReason.NONE;
    private int blockedCyclesLeft;          // para IO y NETWORK
    private TerminationCause terminationCause = TerminationCause.NONE;
    private final int priority;             // menor número = más prioridad
    private int deadline;                   // ciclos que le quedan antes de ser terminado

    // ---- Memoria ----
    private final int memoryRequired;

    // ---- Contexto de ejecución ----
    private final int totalInstructions;
    private int remainingInstructions;
    private int programCounter;
    private int mar;
    private int currentStep;                // índice del paso en el mini-programa

    // ---- Parámetros por tipo ----
    private int ioEveryCycles;              // I/O bound: cada cuántas instrucciones pide E/S
    private int ioDurationCycles;           // I/O bound: cuánto dura la E/S
    private int bufferId = -1;              // productor/consumidor: buffer con el que trabaja
    private int cyclesPerItem;              // productor/consumidor: ciclos para producir/consumir
    private int itemsToProcess;             // productor/consumidor: elementos para terminar
    private int itemsProcessed;

    // ---- Métricas ----
    private long arrivalCycle = -1;
    private long firstRunCycle = -1;
    private long finishCycle = -1;
    private long cyclesOnCpu;
    private long cyclesWaitingReady;
    private long cyclesBlockedOnSemaphores;

    public PCB(String name, int computerId, ProcessType type, int priority,
               int memoryRequired, int deadline, int totalInstructions) {
        this.id = IdGenerator.nextProcessId();
        this.name = name;
        this.computerId = computerId;
        this.type = type;
        this.priority = priority;
        this.memoryRequired = memoryRequired;
        this.deadline = deadline;
        this.totalInstructions = totalInstructions;
        this.remainingInstructions = totalInstructions;
    }

    /** Nombre corto para el log, por ejemplo "P3-H2". */
    public String getTag() {
        return "P" + id + "-H" + computerId;
    }

    // ---- Avance de ejecución ----

    /** Avanza PC y MAR una unidad (el enunciado pide ejecución lineal). */
    public void advance() {
        programCounter++;
        mar++;
        if (remainingInstructions > 0) {
            remainingInstructions--;
        }
    }

    /** Resta un ciclo al deadline. Devuelve true si llegó a 0. */
    public boolean tickDeadline() {
        if (deadline > 0) {
            deadline--;
        }
        return deadline == 0;
    }

    public boolean isFinished() {
        return remainingInstructions <= 0;
    }

    public void block(BlockReason reason, int cycles) {
        this.state = ProcessState.BLOCKED;
        this.blockReason = reason;
        this.blockedCyclesLeft = cycles;
    }

    public void unblock() {
        this.state = ProcessState.READY;
        this.blockReason = BlockReason.NONE;
        this.blockedCyclesLeft = 0;
    }

    public void terminate(TerminationCause cause, long cycle) {
        this.state = ProcessState.TERMINATED;
        this.blockReason = BlockReason.NONE;
        this.terminationCause = cause;
        this.finishCycle = cycle;
    }

    // ---- Getters ----

    public int getId() { return id; }
    public String getName() { return name; }
    public int getComputerId() { return computerId; }
    public ProcessType getType() { return type; }
    public ProcessState getState() { return state; }
    public BlockReason getBlockReason() { return blockReason; }
    public int getBlockedCyclesLeft() { return blockedCyclesLeft; }
    public TerminationCause getTerminationCause() { return terminationCause; }
    public int getPriority() { return priority; }
    public int getDeadline() { return deadline; }
    public int getMemoryRequired() { return memoryRequired; }
    public int getTotalInstructions() { return totalInstructions; }
    public int getRemainingInstructions() { return remainingInstructions; }
    public int getProgramCounter() { return programCounter; }
    public int getMar() { return mar; }
    public int getCurrentStep() { return currentStep; }
    public int getIoEveryCycles() { return ioEveryCycles; }
    public int getIoDurationCycles() { return ioDurationCycles; }
    public int getBufferId() { return bufferId; }
    public int getCyclesPerItem() { return cyclesPerItem; }
    public int getItemsToProcess() { return itemsToProcess; }
    public int getItemsProcessed() { return itemsProcessed; }
    public long getArrivalCycle() { return arrivalCycle; }
    public long getFirstRunCycle() { return firstRunCycle; }
    public long getFinishCycle() { return finishCycle; }
    public long getCyclesOnCpu() { return cyclesOnCpu; }
    public long getCyclesWaitingReady() { return cyclesWaitingReady; }
    public long getCyclesBlockedOnSemaphores() { return cyclesBlockedOnSemaphores; }

    // ---- Setters (solo lo que cambia durante la simulación) ----

    public void setState(ProcessState state) { this.state = state; }
    public void decrementBlockedCycles() { if (blockedCyclesLeft > 0) blockedCyclesLeft--; }
    public void setCurrentStep(int currentStep) { this.currentStep = currentStep; }
    public void setIoParameters(int everyCycles, int durationCycles) {
        this.ioEveryCycles = everyCycles;
        this.ioDurationCycles = durationCycles;
    }
    public void setBufferParameters(int bufferId, int cyclesPerItem, int itemsToProcess) {
        this.bufferId = bufferId;
        this.cyclesPerItem = cyclesPerItem;
        this.itemsToProcess = itemsToProcess;
    }
    public void incrementItemsProcessed() { itemsProcessed++; }
    public void setArrivalCycle(long cycle) { this.arrivalCycle = cycle; }
    public void markFirstRun(long cycle) { if (firstRunCycle < 0) firstRunCycle = cycle; }
    public void incrementCyclesOnCpu() { cyclesOnCpu++; }
    public void incrementCyclesWaitingReady() { cyclesWaitingReady++; }
    public void incrementCyclesBlockedOnSemaphores() { cyclesBlockedOnSemaphores++; }

    @Override
    public String toString() {
        return getTag() + " " + name + " [" + state.getLabel() + "]";
    }
}
