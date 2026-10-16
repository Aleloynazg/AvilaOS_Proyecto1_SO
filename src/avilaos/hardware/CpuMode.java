package avilaos.hardware;

/** Modo de ejecución del procesador: programa de usuario o sistema operativo. */
public enum CpuMode {
    USER("Usuario"),
    KERNEL("Sistema operativo");

    private final String label;

    CpuMode(String label) { this.label = label; }

    public String getLabel() { return label; }
}
