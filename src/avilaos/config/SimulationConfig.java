package avilaos.config;

import avilaos.kernel.process.ProcessType;
import avilaos.kernel.scheduler.SchedulingPolicyType;

/**
 * Parámetros de una simulación de UN computador. Se guarda y se carga como
 * JSON (Gson), por eso los campos son públicos y los tipos son simples.
 * Los rangos son los de DECISIONES.md.
 * (En la F3.3 se reemplaza por los dos JSON del enunciado del Proyecto 2.)
 */
public class SimulationConfig {

    public static final int MIN_RAM = 128;
    public static final int MAX_RAM = 8192;
    public static final int MIN_CYCLE_MS = 50;
    public static final int MAX_CYCLE_MS = 5000;
    public static final int MIN_QUANTUM = 1;
    public static final int MAX_QUANTUM = 20;
    public static final int MIN_PRIORITY = 1;
    public static final int MAX_PRIORITY = 10;

    /** Duración de un ciclo de reloj en milisegundos. */
    public int cycleDurationMs = 500;
    public ComputerConfig computer = new ComputerConfig();
    public ProcessConfig[] processes = new ProcessConfig[0];

    public static class ComputerConfig {
        public int ramMb = 1024;
        /** Nombre de un {@link SchedulingPolicyType}, por ejemplo "ROUND_ROBIN". */
        public String policy = SchedulingPolicyType.FCFS.name();
        public int quantum = 4;
    }

    public static class ProcessConfig {
        public String name = "";
        /** Nombre de un {@link ProcessType}, por ejemplo "IO_BOUND". */
        public String type = ProcessType.CPU_BOUND.name();
        public int priority = 1;
        public int memory = 100;
        public int deadline = 100;
        public int instructions = 20;
        /** I/O bound: cada cuántas instrucciones pide E/S. */
        public int ioEvery = 5;
        /** I/O bound: cuántos ciclos dura la E/S. */
        public int ioDuration = 3;
    }

    /** Configuración por defecto: un computador con 1024 MB, FCFS y sin procesos. */
    public static SimulationConfig defaults() {
        return new SimulationConfig();
    }

    /** Revisa tipos y rangos. Lanza IllegalArgumentException con un mensaje legible. */
    public void validate() {
        if (computer == null) {
            throw new IllegalArgumentException("Falta la configuración del computador");
        }
        if (processes == null) {
            processes = new ProcessConfig[0];
        }

        range(cycleDurationMs, MIN_CYCLE_MS, MAX_CYCLE_MS, "duración del ciclo (ms)");
        range(computer.ramMb, MIN_RAM, MAX_RAM, "RAM (MB)");
        range(computer.quantum, MIN_QUANTUM, MAX_QUANTUM, "quantum");
        parsePolicy(computer.policy, "");

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
            range(p.priority, MIN_PRIORITY, MAX_PRIORITY, h + "prioridad");
            range(p.memory, 1, computer.ramMb, h + "memoria (MB)");
            range(p.deadline, 1, Integer.MAX_VALUE, h + "deadline");
            range(p.instructions, 1, Integer.MAX_VALUE, h + "instrucciones");
            if (parseType(p.type, h) == ProcessType.IO_BOUND) {
                range(p.ioEvery, 1, Integer.MAX_VALUE, h + "pedir E/S cada N instrucciones");
                range(p.ioDuration, 1, Integer.MAX_VALUE, h + "duración de la E/S");
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
