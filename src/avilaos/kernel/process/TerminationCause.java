package avilaos.kernel.process;

/** Por qué terminó un proceso. Necesario para la tasa de cumplimiento de deadlines. */
public enum TerminationCause {
    NONE,
    COMPLETED,
    DEADLINE_MISSED,
    /** La operación sobre el sistema de archivos no se pudo hacer (no existe, no hay espacio, nombre repetido...). */
    IO_FAILED
}
