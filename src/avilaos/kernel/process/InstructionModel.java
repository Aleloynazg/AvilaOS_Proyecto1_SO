package avilaos.kernel.process;

/**
 * Dice qué instrucción ejecuta un proceso en este ciclo, según su tipo.
 * No guarda estado propio: la posición sale del program counter del PCB
 * (el PC cuenta cuántas instrucciones ya ejecutó el proceso).
 */
public final class InstructionModel {

    /** Instrucciones por elemento de un productor/consumidor, sin contar producir/consumir. */
    public static final int SYNC_STEPS = 5;

    private InstructionModel() { }

    /** La próxima instrucción que ejecutará el proceso. */
    public static Instruction next(PCB p) {
        int n = p.getProgramCounter();   // índice de la instrucción que toca (0 = la primera)
        switch (p.getType()) {
            case IO_BOUND:
                return nextIoBound(p, n);
            case PRODUCER:
                return nextProducer(p, n);
            case CONSUMER:
                return nextConsumer(p, n);
            default:
                return new Instruction(InstructionType.NORMAL);
        }
    }

    /** Cada K instrucciones pide E/S (nunca en la última instrucción). */
    private static Instruction nextIoBound(PCB p, int n) {
        int every = p.getIoEveryCycles();
        int number = n + 1;                       // 1 = primera instrucción
        boolean isLast = number >= p.getTotalInstructions();
        if (every > 0 && number % every == 0 && !isLast) {
            return new Instruction(InstructionType.IO_REQUEST);
        }
        return new Instruction(InstructionType.NORMAL);
    }

    /** Producir (k ciclos) → wait(e) → wait(s) → append → signal(s) → signal(n). */
    private static Instruction nextProducer(PCB p, int n) {
        int k = p.getCyclesPerItem();
        int step = n % (k + SYNC_STEPS);
        if (step < k) {
            return new Instruction(InstructionType.PRODUCE);
        }
        switch (step - k) {
            case 0: return new Instruction(InstructionType.SEM_WAIT, BufferSemaphore.EMPTY);
            case 1: return new Instruction(InstructionType.SEM_WAIT, BufferSemaphore.MUTEX);
            case 2: return new Instruction(InstructionType.APPEND);
            case 3: return new Instruction(InstructionType.SEM_SIGNAL, BufferSemaphore.MUTEX);
            default: return new Instruction(InstructionType.SEM_SIGNAL, BufferSemaphore.FULL);
        }
    }

    /** wait(n) → wait(s) → take → signal(s) → signal(e) → consumir (k ciclos). */
    private static Instruction nextConsumer(PCB p, int n) {
        int k = p.getCyclesPerItem();
        int step = n % (k + SYNC_STEPS);
        switch (step) {
            case 0: return new Instruction(InstructionType.SEM_WAIT, BufferSemaphore.FULL);
            case 1: return new Instruction(InstructionType.SEM_WAIT, BufferSemaphore.MUTEX);
            case 2: return new Instruction(InstructionType.TAKE);
            case 3: return new Instruction(InstructionType.SEM_SIGNAL, BufferSemaphore.MUTEX);
            case 4: return new Instruction(InstructionType.SEM_SIGNAL, BufferSemaphore.EMPTY);
            default: return new Instruction(InstructionType.CONSUME);
        }
    }
}