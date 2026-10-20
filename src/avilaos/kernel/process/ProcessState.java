package avilaos.kernel.process;

/** Modelo de cinco estados de un proceso en ÁvilaOS. */
public enum ProcessState {
    NEW("Nuevo"),
    READY("Listo"),
    RUNNING("Ejecución"),
    BLOCKED("Bloqueado"),
    TERMINATED("Terminado");

    private final String label;

    ProcessState(String label) { this.label = label; }

    public String getLabel() { return label; }
}
