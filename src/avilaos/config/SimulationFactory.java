package avilaos.config;

import avilaos.hardware.Computer;
import avilaos.hardware.GlobalClock;
import avilaos.hardware.disk.Disk;
import avilaos.hardware.disk.HeadDirection;
import avilaos.kernel.fs.NodeType;
import avilaos.kernel.fs.Permission;
import avilaos.kernel.process.IoOperation;
import avilaos.kernel.process.PCB;
import avilaos.kernel.process.ProcessFactory;
import avilaos.kernel.process.ProcessType;
import avilaos.kernel.scheduler.PolicyFactory;
import avilaos.kernel.scheduler.RoundRobinPolicy;

/**
 * Arma un sistema ÁvilaOS de un computador a partir de una
 * {@link SimulationConfig}, y hace el camino inverso: describe un sistema
 * en marcha como configuración.
 */
public final class SimulationFactory {

    /** Id del único computador del Proyecto 2. */
    public static final int COMPUTER_ID = 1;

    private SimulationFactory() { }

    /** El sistema listo para mostrarse: el computador y su reloj (todavía sin iniciar). */
    public static final class Simulation {
        public final Computer computer;
        public final GlobalClock clock;

        Simulation(Computer computer, GlobalClock clock) {
            this.computer = computer;
            this.clock = clock;
        }
    }

    public static Simulation create(SimulationConfig config) {
        config.validate();

        Computer computer = new Computer(COMPUTER_ID, config.computer.ramMb);
        GlobalClock clock = new GlobalClock(config.cycleDurationMs);
        computer.conectarReloj(clock);
        computer.setPolicy(PolicyFactory.create(
                SimulationConfig.parsePolicy(config.computer.policy, ""), config.computer.quantum));

        for (int i = 0; i < config.processes.length; i++) {
            PCB pcb = createProcess(config.processes[i]);
            computer.getKernel().submitProcess(pcb, 0);
        }
        return new Simulation(computer, clock);
    }

    /**
     * Arma el sistema del Proyecto 2 a partir de los DOS JSON del enunciado: el de procesos
     * y configuración, y el del sistema de archivos. Los dos son obligatorios.
     *
     * El disco tiene totalBlocks bloques para archivos (JSON del sistema de archivos) más
     * swapSizeBlocks de intercambio (JSON de procesos); el cabezal empieza en initialHead.
     *
     * @throws IllegalArgumentException si falta alguno de los dos o alguno es inválido
     */
    public static Simulation createP2(ProcessesConfig processes, FileSystemConfig fileSystem) {
        if (processes == null) {
            throw new IllegalArgumentException("Falta cargar el archivo de procesos y configuración");
        }
        if (fileSystem == null) {
            throw new IllegalArgumentException("Falta cargar el archivo del sistema de archivos");
        }
        processes.validate();
        fileSystem.validate();

        int swap = processes.globalConfig.swapSizeBlocks;
        int physicalBlocks = fileSystem.diskConfig.totalBlocks + swap;
        if (fileSystem.diskConfig.initialHead >= physicalBlocks) {
            throw new IllegalArgumentException("El campo 'initialHead' (" + fileSystem.diskConfig.initialHead
                    + ") queda fuera del disco, que tiene " + physicalBlocks + " bloques (0 a "
                    + (physicalBlocks - 1) + ")");
        }

        Disk disk = new Disk(fileSystem.diskConfig.totalBlocks, swap);
        disk.setCurrentHead(fileSystem.diskConfig.initialHead);
        if (processes.globalConfig.headSpeed != null) {
            disk.setHeadSpeed(processes.globalConfig.headSpeed);
        }
        disk.setHeadDirection(ProcessesConfig.parseDirection(processes.globalConfig.initialDirection));

        Computer computer = new Computer(COMPUTER_ID, processes.computer.ramCapacity, disk);
        GlobalClock clock = new GlobalClock(processes.globalConfig.cycleDurationMs);
        computer.conectarReloj(clock);
        computer.setPolicy(PolicyFactory.create(
                ProcessesConfig.parseSchedulingPolicy(processes.computer.policy), processes.computer.quantum));
        computer.setDiskPolicy(ProcessesConfig.parseDiskPolicy(processes.globalConfig.initialDiskPolicy));

        fileSystem.populate(computer.getFileSystem());

        PCB[] created = processes.createProcesses(COMPUTER_ID);
        for (int i = 0; i < created.length; i++) {
            computer.getKernel().submitProcess(created[i], 0);
        }
        return new Simulation(computer, clock);
    }

    /**
     * Describe el sistema actual en el formato del JSON de procesos del enunciado (ciclo,
     * swap, política de disco en uso, computador y todos los procesos creados). El estado
     * del sistema de archivos se guarda aparte con {@link FileSystemConfig#fromState}.
     */
    public static ProcessesConfig describeProcesses(Computer computer, GlobalClock clock) {
        ProcessesConfig config = ProcessesConfig.defaults();
        config.globalConfig.cycleDurationMs = clock.getCycleDuration();
        config.globalConfig.swapSizeBlocks = computer.getDisk().getSwapBlockCount();
        config.globalConfig.initialDiskPolicy = computer.getDiskPolicyType().getLabel();
        // Solo se escriben cuando no son los valores por defecto, para que el archivo siga
        // pareciéndose al del enunciado.
        if (computer.getDisk().getHeadSpeed() != Disk.MIN_HEAD_SPEED) {
            config.globalConfig.headSpeed = computer.getDisk().getHeadSpeed();
        }
        if (computer.getDisk().getHeadDirection() == HeadDirection.DOWN) {
            config.globalConfig.initialDirection = "DOWN";
        }
        config.computer.ramCapacity = computer.getMemoryManager().getTotalMemory();
        config.computer.policy = computer.getPolicy().getType().name();
        config.computer.quantum = computer.getPolicy() instanceof RoundRobinPolicy
                ? ((RoundRobinPolicy) computer.getPolicy()).getQuantum() : 0;

        PCB[] all = collectProcesses(computer);
        config.processes = new ProcessesConfig.ProcessEntry[all.length];
        for (int i = 0; i < all.length; i++) {
            PCB p = all[i];
            ProcessesConfig.ProcessEntry e = new ProcessesConfig.ProcessEntry();
            e.name = p.getName();
            e.memory = p.getMemoryRequired();
            e.deadline = p.getInitialDeadline();
            e.instructions = p.getTotalInstructions();
            e.priority = p.getPriority();
            if (p.getType() == ProcessType.FILE_IO) {
                // Sin "type": es el valor por defecto y así el archivo queda como el del enunciado.
                if (p.getIoOperation() != null) {
                    e.operation = p.getIoOperation().name();
                }
                e.targetPath = p.getTargetPath();
                e.newName = p.getNewName();
                if (p.getIoOperation() == IoOperation.CREATE) {
                    e.targetType = p.getTargetType().name();
                    if (p.getTargetType() == NodeType.FILE) {
                        e.blocks = p.getFileBlocks();
                    }
                    e.owner = p.getIoOwner();
                    e.permissions = p.getIoPermission() == Permission.PRIVATE ? "private" : "public";
                }
            } else {
                e.type = p.getType().name();
                if (p.getType() == ProcessType.IO_BOUND) {
                    e.ioEvery = p.getIoEveryCycles();
                    e.ioDuration = p.getIoDurationCycles();
                }
            }
            config.processes[i] = e;
        }
        return config;
    }

    private static PCB createProcess(SimulationConfig.ProcessConfig p) {
        ProcessType type = SimulationConfig.parseType(p.type, "");
        if (type == ProcessType.IO_BOUND) {
            return ProcessFactory.ioBound(p.name, COMPUTER_ID, p.priority, p.memory,
                    p.deadline, p.instructions, p.ioEvery, p.ioDuration);
        }
        return ProcessFactory.cpuBound(p.name, COMPUTER_ID, p.priority, p.memory,
                p.deadline, p.instructions);
    }

    /** Describe el sistema actual para guardarlo: el computador y todos los procesos creados. */
    public static SimulationConfig describe(Computer computer, GlobalClock clock) {
        SimulationConfig config = SimulationConfig.defaults();
        config.cycleDurationMs = clock.getCycleDuration();
        config.computer.ramMb = computer.getMemoryManager().getTotalMemory();
        config.computer.policy = computer.getPolicy().getType().name();
        if (computer.getPolicy() instanceof RoundRobinPolicy) {
            config.computer.quantum = ((RoundRobinPolicy) computer.getPolicy()).getQuantum();
        }

        PCB[] all = collectProcesses(computer);
        config.processes = new SimulationConfig.ProcessConfig[all.length];
        for (int i = 0; i < all.length; i++) {
            PCB p = all[i];
            SimulationConfig.ProcessConfig pc = new SimulationConfig.ProcessConfig();
            pc.name = p.getName();
            pc.type = p.getType().name();
            pc.priority = p.getPriority();
            pc.memory = p.getMemoryRequired();
            pc.deadline = p.getInitialDeadline();
            pc.instructions = p.getTotalInstructions();
            if (p.getType() == ProcessType.IO_BOUND) {
                pc.ioEvery = p.getIoEveryCycles();
                pc.ioDuration = p.getIoDurationCycles();
            }
            config.processes[i] = pc;
        }
        return config;
    }

    /** Todos los procesos del computador (cualquier cola o la CPU), ordenados por id. */
    private static PCB[] collectProcesses(Computer computer) {
        PCB[] result = new PCB[0];
        Object[][] groups = {
            computer.getKernel().getNewQueue().toArray(),
            computer.getKernel().getReadyQueue().toArray(),
            computer.getKernel().getBlockedQueue().toArray(),
            computer.getKernel().getReadySuspendedQueue().toArray(),
            computer.getKernel().getBlockedSuspendedQueue().toArray(),
            computer.getKernel().getTerminatedQueue().toArray()
        };
        for (int g = 0; g < groups.length; g++) {
            for (int j = 0; j < groups[g].length; j++) {
                result = append(result, (PCB) groups[g][j]);
            }
        }
        PCB running = computer.getCPU().getCurrentProcess();
        if (running != null) {
            result = append(result, running);
        }
        // Ordenamiento por inserción por id: así el archivo conserva el orden de creación.
        for (int i = 1; i < result.length; i++) {
            PCB key = result[i];
            int j = i - 1;
            while (j >= 0 && result[j].getId() > key.getId()) {
                result[j + 1] = result[j];
                j--;
            }
            result[j + 1] = key;
        }
        return result;
    }

    private static PCB[] append(PCB[] array, PCB item) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == item) {
                return array;   // un proceso puede verse en dos lugares durante un cambio de cola
            }
        }
        PCB[] bigger = new PCB[array.length + 1];
        for (int i = 0; i < array.length; i++) {
            bigger[i] = array[i];
        }
        bigger[array.length] = item;
        return bigger;
    }
}
