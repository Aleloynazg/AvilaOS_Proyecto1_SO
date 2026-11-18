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

    private static void requirePositive(int value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException("El campo '" + field + "' debe ser mayor que 0");
        }
    }
}