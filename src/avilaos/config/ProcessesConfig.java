package avilaos.config;

import avilaos.hardware.disk.Disk;
import avilaos.hardware.disk.HeadDirection;
import avilaos.kernel.disk.DiskPolicyType;
import avilaos.kernel.fs.NodeType;
import avilaos.kernel.fs.Permission;
import avilaos.kernel.process.IoOperation;
import avilaos.kernel.process.PCB;
import avilaos.kernel.process.ProcessFactory;
import avilaos.kernel.process.ProcessType;
import avilaos.kernel.scheduler.SchedulingPolicyType;

/**
 * Segundo JSON de la simulación: procesos y configuración (formato del enunciado).
 *
 * <pre>
 * {
 *   "globalConfig": { "cycleDurationMs": 300, "swapSizeBlocks": 100, "initialDiskPolicy": "FIFO" },
 *   "computer": { "ramCapacity": 1024, "policy": "FCFS", "quantum": 0 },
 *   "processes": [
 *     { "name": "Proc_95", "memory": 16, "deadline": 500, "instructions": 2,
 *       "targetPath": "/sistema/config.sys", "operation": "READ" }
 *   ]
 * }
 * </pre>
 *
 * Un proceso sin "type" es de E/S de archivos (como todos los del enunciado). Campos
 * opcionales de "globalConfig": "headSpeed" (posiciones por ciclo, por defecto 1) e
 * "initialDirection" (UP o DOWN). Campos opcionales de un proceso: "priority" (1 a 10,
 * por defecto 5), "newName" (UPDATE), "targetType",
 * "blocks", "owner" y "permissions" (CREATE) y, para procesos de otros tipos, "type"
 * CPU_BOUND o IO_BOUND con "ioEvery" e "ioDuration".
 *
 * Los campos son públicos para que Gson los lea y los escriba; se usan arreglos en lugar
 * de colecciones de java.util.
 */
public class ProcessesConfig {

    public static final int MIN_SWAP_BLOCKS = 0;
    public static final int MAX_SWAP_BLOCKS = 1000;
    /** Prioridad de los procesos que no la traen (el enunciado no la incluye). */
    public static final int DEFAULT_PRIORITY = 5;

    public GlobalConfig globalConfig;
    public ComputerSection computer;
    public ProcessEntry[] processes;

    public static class GlobalConfig {
        public int cycleDurationMs;
        public int swapSizeBlocks;
        /** FIFO, SSTF, SCAN, C-SCAN o LOOK. */
        public String initialDiskPolicy;
        /** Opcional: posiciones que recorre el cabezal por ciclo (1 a 100; por defecto 1). */
        public Integer headSpeed;
        /** Opcional: UP (hacia los bloques mayores, por defecto) o DOWN. */
        public String initialDirection;
    }

    public static class ComputerSection {
        public int ramCapacity;
        /** FCFS, ROUND_ROBIN, PRIORITY_PREEMPTIVE o EDF. */
        public String policy;
        /** Solo lo usa Round Robin; con las demás políticas puede ser 0. */
        public int quantum;
    }

    public static class ProcessEntry {
        public String name;
        /** FILE_IO (por defecto), CPU_BOUND o IO_BOUND. */
        public String type;
        public Integer memory;
        public Integer deadline;
        public Integer instructions;
        public Integer priority;

        // Procesos de E/S de archivos
        public String targetPath;
        /** CREATE, READ, UPDATE o DELETE. */
        public String operation;
        /** UPDATE: nombre nuevo; si falta, el kernel agrega "_v2". */
        public String newName;
        /** CREATE: FILE (por defecto) o DIRECTORY. */
        public String targetType;
        /** CREATE de archivo: tamaño en bloques. */
        public Integer blocks;
        /** CREATE: dueño del elemento nuevo (por defecto admin). */
        public String owner;
        /** CREATE: public (por defecto) o private. */
        public String permissions;

        // Procesos I/O bound
        public Integer ioEvery;
        public Integer ioDuration;
    }

    /** Configuración por defecto: ciclo de 500 ms, swap de 100 bloques, FIFO, 1024 de RAM, FCFS y sin procesos. */
    public static ProcessesConfig defaults() {
        ProcessesConfig config = new ProcessesConfig();
        config.globalConfig = new GlobalConfig();
        config.globalConfig.cycleDurationMs = 500;
        config.globalConfig.swapSizeBlocks = Disk.DEFAULT_SWAP_BLOCKS;
        config.globalConfig.initialDiskPolicy = DiskPolicyType.FIFO.getLabel();
        config.computer = new ComputerSection();
        config.computer.ramCapacity = 1024;
        config.computer.policy = SchedulingPolicyType.FCFS.name();
        config.computer.quantum = 4;
        config.processes = new ProcessEntry[0];
        return config;
    }

    /**
     * Revisa secciones, tipos y rangos sin crear ningún proceso (crear un PCB gasta un
     * número de proceso).
     *
     * @throws IllegalArgumentException con el motivo del primer problema que encuentre
     */
    public void validate() {
        if (globalConfig == null) {
            throw new IllegalArgumentException("Falta la sección 'globalConfig' (¿es el archivo de procesos?)");
        }
        if (computer == null) {
            throw new IllegalArgumentException("Falta la sección 'computer'");
        }
        if (processes == null) {
            processes = new ProcessEntry[0];
        }
        range(globalConfig.cycleDurationMs, SimulationConfig.MIN_CYCLE_MS, SimulationConfig.MAX_CYCLE_MS,
                "cycleDurationMs");
        range(globalConfig.swapSizeBlocks, MIN_SWAP_BLOCKS, MAX_SWAP_BLOCKS, "swapSizeBlocks");
        parseDiskPolicy(globalConfig.initialDiskPolicy);
        if (globalConfig.headSpeed != null) {
            range(globalConfig.headSpeed, Disk.MIN_HEAD_SPEED, Disk.MAX_HEAD_SPEED, "headSpeed");
        }
        parseDirection(globalConfig.initialDirection);
        range(computer.ramCapacity, SimulationConfig.MIN_RAM, SimulationConfig.MAX_RAM, "ramCapacity");
        SchedulingPolicyType policy = parseSchedulingPolicy(computer.policy);
        if (policy == SchedulingPolicyType.ROUND_ROBIN) {
            range(computer.quantum, SimulationConfig.MIN_QUANTUM, SimulationConfig.MAX_QUANTUM, "quantum");
        } else if (computer.quantum < 0) {
            throw new IllegalArgumentException("El campo 'quantum' no puede ser negativo (valor: "
                    + computer.quantum + ")");
        }

        for (int i = 0; i < processes.length; i++) {
            validateProcess(processes[i], i);
        }
    }

    /**
     * Crea los procesos del archivo, en orden. Llamar solo después de {@link #validate()}.
     */
    public PCB[] createProcesses(int computerId) {
        validate();
        PCB[] result = new PCB[processes.length];
        for (int i = 0; i < processes.length; i++) {
            result[i] = createProcess(processes[i], computerId);
        }
        return result;
    }

    // ------------------------------------------------------------------

    private void validateProcess(ProcessEntry p, int index) {
        String h = "Proceso " + (index + 1) + ": ";
        if (p == null) {
            throw new IllegalArgumentException(h + "está vacío");
        }
        if (p.name == null || p.name.trim().isEmpty()) {
            throw new IllegalArgumentException(h + "falta el 'name'");
        }
        h = "Proceso '" + p.name + "': ";
        ProcessType type = parseProcessType(p.type, h);
        required(p.memory, h + "falta 'memory'");
        required(p.deadline, h + "falta 'deadline'");
        required(p.instructions, h + "falta 'instructions'");
        range(p.memory, 1, computer.ramCapacity, h + "memory");
        range(p.deadline, 1, Integer.MAX_VALUE, h + "deadline");
        range(p.instructions, 1, Integer.MAX_VALUE, h + "instructions");
        if (p.priority != null) {
            range(p.priority, SimulationConfig.MIN_PRIORITY, SimulationConfig.MAX_PRIORITY, h + "priority");
        }

        if (type == ProcessType.IO_BOUND) {
            required(p.ioEvery, h + "falta 'ioEvery'");
            required(p.ioDuration, h + "falta 'ioDuration'");
            range(p.ioEvery, 1, Integer.MAX_VALUE, h + "ioEvery");
            range(p.ioDuration, 1, Integer.MAX_VALUE, h + "ioDuration");
        }
        if (type == ProcessType.FILE_IO) {
            IoOperation operation = parseOperation(p.operation, h);
            if (p.targetPath == null || !p.targetPath.startsWith("/") || p.targetPath.length() < 2) {
                throw new IllegalArgumentException(h + "'targetPath' debe ser una ruta que empiece con / "
                        + "y no sea la raíz (valor: " + p.targetPath + ")");
            }
            if (p.newName != null && p.newName.trim().isEmpty()) {
                throw new IllegalArgumentException(h + "'newName' no puede estar vacío");
            }
            parsePermission(p.permissions, h);
            NodeType targetType = parseTargetType(p.targetType, h);
            if (operation == IoOperation.CREATE && targetType == NodeType.FILE) {
                required(p.blocks, h + "CREATE de un archivo necesita 'blocks' (su tamaño)");
                range(p.blocks, 1, Integer.MAX_VALUE, h + "blocks");
            }
        }
    }

    private PCB createProcess(ProcessEntry p, int computerId) {
        ProcessType type = parseProcessType(p.type, "");
        int priority = p.priority != null ? p.priority : DEFAULT_PRIORITY;
        switch (type) {
            case CPU_BOUND:
                return ProcessFactory.cpuBound(p.name, computerId, priority, p.memory, p.deadline, p.instructions);
            case IO_BOUND:
                return ProcessFactory.ioBound(p.name, computerId, priority, p.memory, p.deadline,
                        p.instructions, p.ioEvery, p.ioDuration);
            default:
                return ProcessFactory.fileIo(p.name, computerId, priority, p.memory, p.deadline, p.instructions,
                        parseOperation(p.operation, ""), p.targetPath, p.newName,
                        parseTargetType(p.targetType, ""), p.blocks != null ? p.blocks : 0,
                        p.owner, parsePermission(p.permissions, ""));
        }
    }

    // ---- Conversión de textos ----

    /** Acepta los nombres del enunciado y variantes comunes: "FCFS", "Round Robin", "RR", "EDF"... */
    public static SchedulingPolicyType parseSchedulingPolicy(String value) {
        if (value != null) {
            String clean = value.trim().toUpperCase().replace('-', '_').replace(' ', '_');
            if (clean.equals("RR")) {
                return SchedulingPolicyType.ROUND_ROBIN;
            }
            if (clean.equals("PRIORITY") || clean.equals("PRIORIDAD") || clean.equals("PRIORIDADES")) {
                return SchedulingPolicyType.PRIORITY_PREEMPTIVE;
            }
            for (SchedulingPolicyType type : SchedulingPolicyType.values()) {
                if (type.name().equals(clean)) {
                    return type;
                }
            }
        }
        throw new IllegalArgumentException("El campo 'policy' no es una política conocida: " + value
                + " (usa FCFS, ROUND_ROBIN, PRIORITY_PREEMPTIVE o EDF)");
    }

    public static DiskPolicyType parseDiskPolicy(String value) {
        try {
            return DiskPolicyType.fromText(value == null ? DiskPolicyType.FIFO.name() : value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("El campo 'initialDiskPolicy': " + e.getMessage());
        }
    }

    /** UP (hacia los bloques mayores) o DOWN; sin valor, UP. */
    public static HeadDirection parseDirection(String value) {
        if (value == null || value.trim().isEmpty() || value.trim().equalsIgnoreCase("UP")) {
            return HeadDirection.UP;
        }
        if (value.trim().equalsIgnoreCase("DOWN")) {
            return HeadDirection.DOWN;
        }
        throw new IllegalArgumentException("El campo 'initialDirection' debe ser UP o DOWN (valor: " + value + ")");
    }

    private static ProcessType parseProcessType(String value, String where) {
        if (value == null || value.trim().isEmpty()) {
            return ProcessType.FILE_IO;
        }
        try {
            return ProcessType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(where + "'type' debe ser FILE_IO, CPU_BOUND o IO_BOUND (valor: "
                    + value + ")");
        }
    }

    private static IoOperation parseOperation(String value, String where) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(where + "falta 'operation'");
        }
        String clean = value.trim().toUpperCase();
        if (clean.equals("CREATE") || clean.equals("READ") || clean.equals("UPDATE") || clean.equals("DELETE")) {
            return IoOperation.valueOf(clean);
        }
        throw new IllegalArgumentException(where + "'operation' debe ser CREATE, READ, UPDATE o DELETE (valor: "
                + value + ")");
    }

    private static NodeType parseTargetType(String value, String where) {
        if (value == null || value.trim().isEmpty()) {
            return NodeType.FILE;
        }
        String clean = value.trim().toUpperCase();
        if (clean.equals("FILE")) {
            return NodeType.FILE;
        }
        if (clean.equals("DIRECTORY")) {
            return NodeType.DIRECTORY;
        }
        throw new IllegalArgumentException(where + "'targetType' debe ser FILE o DIRECTORY (valor: " + value + ")");
    }

    private static Permission parsePermission(String value, String where) {
        if (value == null || value.trim().isEmpty()) {
            return Permission.PUBLIC;
        }
        String clean = value.trim().toUpperCase();
        if (clean.equals("PUBLIC")) {
            return Permission.PUBLIC;
        }
        if (clean.equals("PRIVATE")) {
            return Permission.PRIVATE;
        }
        throw new IllegalArgumentException(where + "'permissions' debe ser public o private (valor: " + value + ")");
    }

    private static void required(Object value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void range(int value, int min, int max, String field) {
        if (value < min || value > max) {
            throw new IllegalArgumentException("El campo '" + field + "' debe estar entre " + min + " y "
                    + max + " (valor: " + value + ")");
        }
    }
}
