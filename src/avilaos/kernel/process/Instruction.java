package avilaos.kernel.process;

/** Una instrucción concreta que ejecuta un proceso en un ciclo. */
public final class Instruction {

    private final InstructionType type;

    public Instruction(InstructionType type) {
        this.type = type;
    }

    public InstructionType getType() { return type; }

    /** true si se ejecuta en modo sistema operativo. */
    public boolean isKernelMode() { return type.isKernelMode(); }

    @Override
    public String toString() {
        return type.name();
    }
}
