package avilaos.kernel.process;

/**
 * Paso de un mini-programa. Cada proceso es una secuencia de estos pasos;
 * el PCB guarda en qué paso va. DISK_REQUEST (pedir una operación al
 * disco) se ejecuta en modo KERNEL (ver nota del enunciado).
 */
public enum InstructionType {
    NORMAL(false),
    IO_REQUEST(false),
    DISK_REQUEST(true);

    private final boolean kernelMode;

    InstructionType(boolean kernelMode) { this.kernelMode = kernelMode; }

    /** true si esta instrucción se ejecuta en modo sistema operativo. */
    public boolean isKernelMode() { return kernelMode; }
}
