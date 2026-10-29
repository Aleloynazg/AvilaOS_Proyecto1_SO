package avilaos.kernel.scheduler;

/** Crea la política que corresponde a cada valor de {@link SchedulingPolicyType}. */
public final class PolicyFactory {

    private PolicyFactory() { }

    /** El quantum solo lo usa Round Robin. */
    public static SchedulingPolicy create(SchedulingPolicyType type, int quantum) {
        switch (type) {
            case FCFS:
                return new FCFSPolicy();
            case ROUND_ROBIN:
                return new RoundRobinPolicy(quantum);
            case PRIORITY_PREEMPTIVE:
                return new PriorityPolicy();
            case EDF:
                return new EDFPolicy();
            default:
                throw new IllegalArgumentException("Política desconocida: " + type);
        }
    }
}
