package avilaos.kernel.process;

/**
 * Paso de un mini-programa. Cada proceso es una secuencia de estos pasos;
 * el PCB guarda en qué paso va. Las operaciones de semáforo y buffer se
 * ejecutan en modo KERNEL (ver nota del enunciado).
 */
public enum InstructionType {
    NORMAL(false),
    IO_REQUEST(false),
    PRODUCE(false),
    CONSUME(false),
    SEM_WAIT(true),
    SEM_SIGNAL(true),
    APPEND(true),
    TAKE(true);

    private final boolean kernelMode;

    InstructionType(boolean kernelMode) { this.kernelMode = kernelMode; }

    /** true si esta instrucción se ejecuta en modo sistema operativo. */
    public boolean isKernelMode() { return kernelMode; }
}
