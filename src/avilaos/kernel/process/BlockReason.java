package avilaos.kernel.process;

/**
 * Motivo por el que un proceso está BLOCKED. Responde al requisito de
 * "identificar y justificar los estados bloqueados".
 */
public enum BlockReason {
    NONE("-"),
    IO("Esperando E/S"),
    SEM_EMPTY("Semáforo 'vacíos' (e): buffer lleno"),
    SEM_FULL("Semáforo 'llenos' (n): buffer vacío"),
    SEM_MUTEX("Semáforo 'mutex' (s): región crítica ocupada"),
    NETWORK("Latencia de red (buffer remoto)");

    private final String label;

    BlockReason(String label) { this.label = label; }

    public String getLabel() { return label; }
}
