package avilaos.kernel.process;

/**
 * Una instrucción concreta: su tipo y, si es una operación de semáforo,
 * sobre cuál de los 3 semáforos del buffer actúa.
 */
public final class Instruction {

    private final InstructionType type;
    private final BufferSemaphore semaphore;   // null si no es SEM_WAIT / SEM_SIGNAL

    public Instruction(InstructionType type) {
        this(type, null);
    }

    public Instruction(InstructionType type, BufferSemaphore semaphore) {
        this.type = type;
        this.semaphore = semaphore;
    }

    public InstructionType getType() { return type; }

    public BufferSemaphore getSemaphore() { return semaphore; }

    /** true si se ejecuta en modo sistema operativo. */
    public boolean isKernelMode() { return type.isKernelMode(); }

    @Override
    public String toString() {
        if (semaphore == null) {
            return type.name();
        }
        return type.name() + "(" + semaphore.getSymbol() + ")";
    }
}