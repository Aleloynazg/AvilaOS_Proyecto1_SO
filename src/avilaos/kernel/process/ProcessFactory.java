package avilaos.kernel.process;

/**
 * Crea procesos de cada tipo con sus parámetros. La usa el formulario
 * "Crear proceso" de la GUI y la carga desde el archivo de configuración.
 */
public final class ProcessFactory {

    private ProcessFactory() { }

    public static PCB cpuBound(String name, int computerId, int priority, int memory,
                               int deadline, int instructions) {
        requirePositive(instructions, "instrucciones");
        return new PCB(name, computerId, ProcessType.CPU_BOUND, priority, memory, deadline, instructions);
    }

    /** ioEvery = cada cuántas instrucciones pide E/S; ioDuration = cuántos ciclos dura la E/S. */
    public static PCB ioBound(String name, int computerId, int priority, int memory,
                              int deadline, int instructions, int ioEvery, int ioDuration) {
        requirePositive(instructions, "instrucciones");
        requirePositive(ioEvery, "cada cuántas instrucciones pide E/S");
        requirePositive(ioDuration, "duración de la E/S");
        PCB p = new PCB(name, computerId, ProcessType.IO_BOUND, priority, memory, deadline, instructions);
        p.setIoParameters(ioEvery, ioDuration);
        return p;
    }

    /** El total de instrucciones se calcula: elementos × (ciclos por elemento + 5 de sincronización). */
    public static PCB producer(String name, int computerId, int priority, int memory, int deadline,
                               int bufferId, int cyclesPerItem, int items) {
        return bufferProcess(ProcessType.PRODUCER, name, computerId, priority, memory, deadline,
                bufferId, cyclesPerItem, items);
    }

    public static PCB consumer(String name, int computerId, int priority, int memory, int deadline,
                               int bufferId, int cyclesPerItem, int items) {
        return bufferProcess(ProcessType.CONSUMER, name, computerId, priority, memory, deadline,
                bufferId, cyclesPerItem, items);
    }

    private static PCB bufferProcess(ProcessType type, String name, int computerId, int priority,
                                     int memory, int deadline, int bufferId, int cyclesPerItem, int items) {
        requirePositive(cyclesPerItem, "ciclos por elemento");
        requirePositive(items, "elementos");
        int total = items * (cyclesPerItem + InstructionModel.SYNC_STEPS);
        PCB p = new PCB(name, computerId, type, priority, memory, deadline, total);
        p.setBufferParameters(bufferId, cyclesPerItem, items);
        return p;
    }

    private static void requirePositive(int value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException("El campo '" + field + "' debe ser mayor que 0");
        }
    }
}