package avilaos.kernel.process;

/** Por qué terminó un proceso. Necesario para la tasa de cumplimiento de deadlines. */
public enum TerminationCause {
    NONE,
    COMPLETED,
    DEADLINE_MISSED
}
