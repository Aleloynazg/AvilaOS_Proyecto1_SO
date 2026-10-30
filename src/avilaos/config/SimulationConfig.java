package avilaos.config;

import avilaos.kernel.process.ProcessType;
import avilaos.kernel.scheduler.SchedulingPolicyType;

/**
 * Parámetros de una simulación. Se guarda y se carga como JSON (Gson), por
 * eso los campos son públicos y los tipos son simples (números y textos).
 * Los rangos son los de DECISIONES.md.
 */
public class SimulationConfig {

    public static final int MIN_COMPUTERS = 2;
    public static final int MAX_COMPUTERS = 4;
    public static final int MIN_RAM = 128;
    public static final int MAX_RAM = 8192;
    public static final int MIN_CYCLE_MS = 50;
    public static final int MAX_CYCLE_MS = 5000;
    public static final int MIN_QUANTUM = 1;
    public static final int MAX_QUANTUM = 20;
    public static final int MIN_LATENCY = 0;
    public static final int MAX_LATENCY = 20;
    public static final int MIN_BUFFER_CAPACITY = 1;
    public static final int MAX_BUFFER_CAPACITY = 50;
    public static final int MIN_PRIORITY = 1;
    public static final int MAX_PRIORITY = 10;

    /** Duración de un ciclo de reloj en milisegundos. */
    public int cycleDurationMs = 500;
    /** Ciclos extra que cuesta cada acceso a un buffer de otro computador. */
    public int networkLatency = 3;
    public ComputerConfig[] computers = new ComputerConfig[0];
    public BufferConfig[] buffers = new BufferConfig[0];
    public ProcessConfig[] processes = new ProcessConfig[0];

    public static class ComputerConfig {
        public int ramMb = 1024;
        /** Nombre de un {@link SchedulingPolicyType}, por ejemplo "ROUND_ROBIN". */
        public String policy = SchedulingPolicyType.FCFS.name();
        public int quantum = 4;
    }

    public static class BufferConfig {
        public int capacity = 5;
        /** Id del computador anfitrión (H1 = 1). */
        public int host = 1;
    }

    public static class ProcessConfig {
        public String name = "";
        public int computer = 1;
        /** Nombre de un {@link ProcessType}, por ejemplo "PRODUCER". */
        public String type = ProcessType.CPU_BOUND.name();
        public int priority = 1;
        public int memory = 100;
        public int deadline = 100;
        /** CPU bound e I/O bound. En productor/consumidor se calcula solo. */
        public int instructions = 20;
        /** I/O bound: cada cuántas instrucciones pide E/S. */
        public int ioEvery = 5;
        /** I/O bound: cuántos ciclos dura la E/S. */
        public int ioDuration = 3;
        /** Productor/consumidor: posición (desde 1) del buffer en la lista de buffers. */
        public int buffer = 1;
        public int cyclesPerItem = 2;
        public int items = 5;
    }

    /** Configuración por defecto: n computadores con 1024 MB, FCFS y sin carga inicial. */
    public static SimulationConfig defaults(int computerCount) {
        SimulationConfig config = new SimulationConfig();
        config.computers = new ComputerConfig[computerCount];
        for (int i = 0; i < computerCount; i++) {
            config.computers[i] = new ComputerConfig();
        }
        return config;
    }

    /** Revisa tipos y rangos. Lanza IllegalArgumentException con un mensaje legible. */
    public void validate() {
        if (computers == null) {
            computers = new ComputerConfig[0];
        }
        if (buffers == null) {
            buffers = new BufferConfig[0];
        }
        if (processes == null) {
            processes = new ProcessConfig[0];
        }

        range(computers.length, MIN_COMPUTERS, MAX_COMPUTERS, "número de computadores");
        range(cycleDurationMs, MIN_CYCLE_MS, MAX_CYCLE_MS, "duración del ciclo (ms)");
        range(networkLatency, MIN_LATENCY, MAX_LATENCY, "latencia de red (ciclos)");

        for (int i = 0; i < computers.length; i++) {
            ComputerConfig c = computers[i];
            String h = "Computador " + (i + 1) + ": ";
            if (c == null) {
                throw new IllegalArgumentException(h + "falta su configuración");
            }
            range(c.ramMb, MIN_RAM, MAX_RAM, h + "RAM (MB)");
            range(c.quantum, MIN_QUANTUM, MAX_QUANTUM, h + "quantum");
            parsePolicy(c.policy, h);
        }

        for (int i = 0; i < buffers.length; i++) {
            BufferConfig b = buffers[i];
            String h = "Buffer " + (i + 1) + ": ";
            if (b == null) {
                throw new IllegalArgumentException(h + "falta su configuración");
            }
            range(b.capacity, MIN_BUFFER_CAPACITY, MAX_BUFFER_CAPACITY, h + "capacidad");
            range(b.host, 1, computers.length, h + "computador anfitrión");
        }

        for (int i = 0; i < processes.length; i++) {
            ProcessConfig p = processes[i];
            String h = "Proceso " + (i + 1) + ": ";
            if (p == null) {
                throw new IllegalArgumentException(h + "falta su configuración");
            }
            if (p.name == null || p.name.trim().isEmpty()) {
                throw new IllegalArgumentException(h + "falta el nombre");
            }
            h = "Proceso '" + p.name + "': ";
            range(p.computer, 1, computers.length, h + "computador");
            range(p.priority, MIN_PRIORITY, MAX_PRIORITY, h + "prioridad");
            range(p.memory, 1, computers[p.computer - 1].ramMb, h + "memoria (MB)");
            range(p.deadline, 1, Integer.MAX_VALUE, h + "deadline");

            ProcessType type = parseType(p.type, h);
            if (type == ProcessType.PRODUCER || type == ProcessType.CONSUMER) {
                range(p.buffer, 1, buffers.length, h + "buffer (posición en la lista de buffers)");
                range(p.cyclesPerItem, 1, Integer.MAX_VALUE, h + "ciclos por elemento");
                range(p.items, 1, Integer.MAX_VALUE, h + "elementos");
            } else {
                range(p.instructions, 1, Integer.MAX_VALUE, h + "instrucciones");
                if (type == ProcessType.IO_BOUND) {
                    range(p.ioEvery, 1, Integer.MAX_VALUE, h + "pedir E/S cada N instrucciones");
                    range(p.ioDuration, 1, Integer.MAX_VALUE, h + "duración de la E/S");
                }
            }
        }
    }

    public static SchedulingPolicyType parsePolicy(String value, String where) {
        try {
            return SchedulingPolicyType.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException(where + "política desconocida: " + value);
        }
    }

    public static ProcessType parseType(String value, String where) {
        try {
            return ProcessType.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException(where + "tipo de proceso desconocido: " + value);
        }
    }

    private static void range(int value, int min, int max, String field) {
        if (value < min || value > max) {
            throw new IllegalArgumentException("El campo '" + field + "' debe estar entre "
                    + min + " y " + max + " (valor: " + value + ")");
        }
    }
}
