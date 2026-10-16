package avilaos.kernel.process;

/** Tipos de proceso que el usuario puede crear. */
public enum ProcessType {
    CPU_BOUND("CPU bound"),
    IO_BOUND("I/O bound"),
    PRODUCER("Productor"),
    CONSUMER("Consumidor");

    private final String label;

    ProcessType(String label) { this.label = label; }

    public String getLabel() { return label; }
}
