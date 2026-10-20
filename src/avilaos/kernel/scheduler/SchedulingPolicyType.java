package avilaos.kernel.scheduler;

/** Políticas de planificación disponibles. Se muestran en el selector de la GUI. */
public enum SchedulingPolicyType {
    FCFS("FCFS"),
    ROUND_ROBIN("Round Robin"),
    PRIORITY_PREEMPTIVE("Prioridades apropiativas"),
    EDF("EDF (Earliest Deadline First)");

    private final String label;

    SchedulingPolicyType(String label) { this.label = label; }

    public String getLabel() { return label; }

    @Override
    public String toString() { return label; }
}
