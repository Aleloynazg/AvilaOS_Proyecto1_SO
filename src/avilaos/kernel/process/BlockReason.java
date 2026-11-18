package avilaos.kernel.process;

/**
 * Motivo por el que un proceso está BLOCKED. Responde al requisito de
 * "identificar y justificar los estados bloqueados".
 */
public enum BlockReason {
    NONE("-"),
    IO("Esperando E/S"),
    DISK_IO("Esperando E/S del disco");
    

    private final String label;

    BlockReason(String label) { this.label = label; }

    public String getLabel() { return label; }
}
