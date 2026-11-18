package avilaos.config;

import avilaos.hardware.Computer;
import avilaos.hardware.GlobalClock;
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
