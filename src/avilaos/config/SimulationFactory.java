package avilaos.config;

import avilaos.hardware.Cluster;
import avilaos.hardware.Computer;
import avilaos.hardware.GlobalClock;
import avilaos.kernel.process.PCB;
import avilaos.kernel.process.ProcessFactory;
import avilaos.kernel.process.ProcessType;
import avilaos.kernel.scheduler.PolicyFactory;
import avilaos.kernel.scheduler.RoundRobinPolicy;
import avilaos.kernel.scheduler.SchedulingPolicyType;
import avilaos.kernel.sync.Buffer;
import avilaos.kernel.sync.SyncExecutor;

/**
 * Arma un sistema ÁvilaOS completo a partir de una {@link SimulationConfig},
 * y hace el camino inverso: describe un sistema en marcha como configuración.
 */
public final class SimulationFactory {

    private SimulationFactory() { }

    /** El sistema listo para mostrarse: el clúster y su reloj (todavía sin iniciar). */
    public static final class Simulation {
        public final Cluster cluster;
        public final GlobalClock clock;

        Simulation(Cluster cluster, GlobalClock clock) {
            this.cluster = cluster;
            this.clock = clock;
        }
    }

    public static Simulation create(SimulationConfig config) {
        config.validate();

        int[] ram = new int[config.computers.length];
        for (int i = 0; i < ram.length; i++) {
            ram[i] = config.computers[i].ramMb;
        }
        Cluster cluster = new Cluster(ram, config.networkLatency);
        Computer[] computers = cluster.getComputers();

        GlobalClock clock = new GlobalClock(config.cycleDurationMs);
        cluster.connectClock(clock);

        SyncExecutor syncExecutor = new SyncExecutor(cluster);
        for (int i = 0; i < computers.length; i++) {
            SimulationConfig.ComputerConfig c = config.computers[i];
            computers[i].setPolicy(PolicyFactory.create(
                    SimulationConfig.parsePolicy(c.policy, ""), c.quantum));
            computers[i].setSyncExecutor(syncExecutor);
        }

        for (int i = 0; i < config.buffers.length; i++) {
            SimulationConfig.BufferConfig b = config.buffers[i];
            if (cluster.createBuffer(b.capacity, b.host) == null) {
                throw new IllegalArgumentException("Buffer " + (i + 1) + ": el computador H"
                        + b.host + " no tiene RAM libre para " + b.capacity + " MB");
            }
        }
        Object[] buffers = cluster.getBuffers();

        for (int i = 0; i < config.processes.length; i++) {
            SimulationConfig.ProcessConfig p = config.processes[i];
            PCB pcb = createProcess(p, buffers);
            computers[p.computer - 1].getKernel().submitProcess(pcb, 0);
        }
        return new Simulation(cluster, clock);
    }

    private static PCB createProcess(SimulationConfig.ProcessConfig p, Object[] buffers) {
        ProcessType type = SimulationConfig.parseType(p.type, "");
        switch (type) {
            case IO_BOUND:
                return ProcessFactory.ioBound(p.name, p.computer, p.priority, p.memory,
                        p.deadline, p.instructions, p.ioEvery, p.ioDuration);
            case PRODUCER:
                return ProcessFactory.producer(p.name, p.computer, p.priority, p.memory,
                        p.deadline, ((Buffer) buffers[p.buffer - 1]).getId(), p.cyclesPerItem, p.items);
            case CONSUMER:
                return ProcessFactory.consumer(p.name, p.computer, p.priority, p.memory,
                        p.deadline, ((Buffer) buffers[p.buffer - 1]).getId(), p.cyclesPerItem, p.items);
            default:
                return ProcessFactory.cpuBound(p.name, p.computer, p.priority, p.memory,
                        p.deadline, p.instructions);
        }
    }

    /**
     * Describe el sistema actual para guardarlo: computadores con su política,
     * buffers y todos los procesos creados (con sus atributos iniciales).
     */
    public static SimulationConfig describe(Cluster cluster, GlobalClock clock) {
        Computer[] computers = cluster.getComputers();
        SimulationConfig config = SimulationConfig.defaults(computers.length);
        config.cycleDurationMs = clock.getCycleDuration();
        config.networkLatency = cluster.getNetworkLatency();

        for (int i = 0; i < computers.length; i++) {
            SimulationConfig.ComputerConfig c = config.computers[i];
            c.ramMb = computers[i].getMemoryManager().getTotalMemory();
            SchedulingPolicyType policy = computers[i].getPolicy().getType();
            c.policy = policy.name();
            if (computers[i].getPolicy() instanceof RoundRobinPolicy) {
                c.quantum = ((RoundRobinPolicy) computers[i].getPolicy()).getQuantum();
            }
        }

        Object[] buffers = cluster.getBuffers();
        config.buffers = new SimulationConfig.BufferConfig[buffers.length];
        for (int i = 0; i < buffers.length; i++) {
            Buffer b = (Buffer) buffers[i];
            config.buffers[i] = new SimulationConfig.BufferConfig();
            config.buffers[i].capacity = b.getCapacity();
            config.buffers[i].host = b.getHostComputerId();
        }

        PCB[] all = collectProcesses(computers);
        config.processes = new SimulationConfig.ProcessConfig[all.length];
        for (int i = 0; i < all.length; i++) {
            PCB p = all[i];
            SimulationConfig.ProcessConfig pc = new SimulationConfig.ProcessConfig();
            pc.name = p.getName();
            pc.computer = p.getComputerId();
            pc.type = p.getType().name();
            pc.priority = p.getPriority();
            pc.memory = p.getMemoryRequired();
            pc.deadline = p.getInitialDeadline();
            pc.instructions = p.getTotalInstructions();
            if (p.getType() == ProcessType.IO_BOUND) {
                pc.ioEvery = p.getIoEveryCycles();
                pc.ioDuration = p.getIoDurationCycles();
            }
            if (p.getType() == ProcessType.PRODUCER || p.getType() == ProcessType.CONSUMER) {
                pc.buffer = positionOfBuffer(buffers, p.getBufferId());
                pc.cyclesPerItem = p.getCyclesPerItem();
                pc.items = p.getItemsToProcess();
            }
            config.processes[i] = pc;
        }
        return config;
    }

    private static int positionOfBuffer(Object[] buffers, int bufferId) {
        for (int i = 0; i < buffers.length; i++) {
            if (((Buffer) buffers[i]).getId() == bufferId) {
                return i + 1;
            }
        }
        return 1;
    }

    /** Todos los procesos de todos los computadores (cualquier cola o la CPU), ordenados por id. */
    private static PCB[] collectProcesses(Computer[] computers) {
        PCB[] result = new PCB[0];
        for (int i = 0; i < computers.length; i++) {
            Object[][] groups = {
                computers[i].getKernel().getNewQueue().toArray(),
                computers[i].getKernel().getReadyQueue().toArray(),
                computers[i].getKernel().getBlockedQueue().toArray(),
                computers[i].getKernel().getTerminatedQueue().toArray()
            };
            for (int g = 0; g < groups.length; g++) {
                for (int j = 0; j < groups[g].length; j++) {
                    result = append(result, (PCB) groups[g][j]);
                }
            }
            PCB running = computers[i].getCPU().getCurrentProcess();
            if (running != null) {
                result = append(result, running);
            }
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
