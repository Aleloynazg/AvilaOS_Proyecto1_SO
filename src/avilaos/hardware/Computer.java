/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.hardware;

/**
 *
 * @author alexandraloynaz
 */
import avilaos.kernel.Kernel;
import avilaos.kernel.memory.MemoryManager;
import avilaos.kernel.process.PCB;
import avilaos.kernel.process.TerminationCause;
import avilaos.kernel.scheduler.SchedulingPolicy;
import avilaos.kernel.scheduler.FCFSPolicy;
import avilaos.kernel.process.BlockReason;
import avilaos.kernel.process.Instruction;
import avilaos.kernel.process.InstructionModel;
import avilaos.kernel.process.InstructionType;
import avilaos.kernel.process.ProcessState;
import avilaos.kernel.sync.SyncExecutor;
import avilaos.kernel.process.InstructionType;



public class Computer implements ClockListener {

    private final int id;
    private final CPU cpu;
    private final MemoryManager memory;
    private final Kernel kernel;
    private SchedulingPolicy policy;
    private int cyclesCPU;
    private final ComputerMetrics metrics;
    private boolean instruccionEjecutada;
    private SyncExecutor syncExecutor;

    public Computer(int id, int capacidadRAM) {
        if (id <= 0) {
            throw new IllegalArgumentException(
                "El identificador debe ser mayor que cero."
            );
        }

        this.id = id;
        this.cpu = new CPU(id);
        this.memory = new MemoryManager(capacidadRAM);
        this.kernel = new Kernel(id, memory);
        this.cpu.setEventListener((cycle, message) -> {
            kernel.reportEvent(cycle, message);
            });
        this.policy = new FCFSPolicy();
        this.cyclesCPU = 0;
        this.metrics = new ComputerMetrics(id);
        this.kernel.setMetrics(this.metrics);

    }

    public int getId() {
        return id;
    }

    public CPU getCPU() {
        return cpu;
    }

    public MemoryManager getMemoryManager() {
        return memory;
    }

    public Kernel getKernel() {
        return kernel;
    }
    public void setSyncExecutor(SyncExecutor syncExecutor) {
        this.syncExecutor = syncExecutor;}

    public void mostrarEstado() {
        System.out.println("Computador: " + id);

        System.out.println(
            "Estado de CPU: "
            + (cpu.isIdle() ? "Libre" : "Ejecutando un proceso")
        );

        System.out.println(
            "Modo de CPU: " + cpu.getMode().getLabel()
        );

        System.out.println(
            "Memoria RAM utilizada: "
            + memory.getUsedMemory()
            + " / "
            + memory.getTotalMemory()
        );

        System.out.println(
            "Memoria RAM disponible: "
            + memory.getFreeMemory()
        );
    }

    public void conectarReloj(GlobalClock reloj) {
        if (reloj != null) {
            reloj.addListener(this);
        }
    }

@Override
public void onTick(long ciclo) {
    instruccionEjecutada = false;

    try {
        procesarCiclo(ciclo);
    } finally {
        metrics.registrarCiclo(ciclo, instruccionEjecutada);
    }
}
private void procesarCiclo(long ciclo) {
    kernel.tickTimedBlocks(ciclo);
    kernel.tickDeadlines(ciclo);
    kernel.admitNewProcesses(ciclo);
    kernel.contarTiempoEsperaReady();
    kernel.contarTiempoBloqueoSemaforos();

    PCB procesoActual = cpu.getCurrentProcess();

    
    // 1. Revisar si el proceso que se ejecuta agotó su deadline.
    if (procesoActual != null && procesoActual.tickDeadline()) {
        cpu.releaseProcess();
        cyclesCPU = 0;
        kernel.terminateProcess(
            procesoActual,
            TerminationCause.DEADLINE_MISSED,
            ciclo);

        procesoActual = null;
    }

// 2. Revisar si la política exige ceder la CPU.
if (procesoActual != null
        && policy.shouldPreempt(
            procesoActual,
            kernel.getReadyQueue(),
            cyclesCPU)) {
        kernel.reportEvent(
        ciclo,
        "Expulsando " + procesoActual.getTag()
        + " de la CPU por la política "
        + policy.getClass().getSimpleName()
    );
    cpu.releaseProcess();

        procesoActual.setState(
            avilaos.kernel.process.ProcessState.READY
        );

        if (policy instanceof avilaos.kernel.scheduler.EDFPolicy) {
            kernel.getReadyQueue().insertSorted(
                procesoActual,
                new avilaos.structures.ItemComparator<PCB>() {
                    @Override
                    public int compare(PCB a, PCB b) {
                        if (a.getDeadline() < b.getDeadline()) {
                            return -1;
                        }

                        if (a.getDeadline() > b.getDeadline()) {
                            return 1;
                        }

                        return 0;
                    }
                }
            );
        } else {
            kernel.getReadyQueue().enqueue(procesoActual);
        }

        cyclesCPU = 0;
        procesoActual = null;
    }

    // 3. Si la CPU está libre, seleccionar el siguiente proceso.
    if (cpu.isIdle()) {
        PCB siguiente = policy.selectNext(kernel.getReadyQueue());

        if (siguiente != null) {
            cpu.setMode(CpuMode.KERNEL);

            if (cpu.assignProcess(siguiente)) {
                cyclesCPU = 0;
                kernel.reportEvent(
                ciclo,
                "Seleccionando " + siguiente.getTag()
                + " para ejecutar en la CPU mediante "
                + policy.getClass().getSimpleName()
            );
            }
        } else {
            cpu.setMode(CpuMode.KERNEL);
        }
        return;
    }

    // 4. Ejecutar una instrucción del proceso actual.
    procesoActual = cpu.getCurrentProcess();

    if (procesoActual != null) {
        Instruction instruccion = InstructionModel.next(procesoActual);

        // Si solicita E/S, bloquear el proceso.
        if (instruccion.getType() == InstructionType.IO_REQUEST) {
            procesoActual.advance();
            cpu.setMode(CpuMode.KERNEL);
            cpu.releaseProcess();

            kernel.blockProcess(
                procesoActual,
                BlockReason.IO,
                procesoActual.getIoDurationCycles(),
                ciclo
            );

            cyclesCPU = 0;
            return;
        }

        // Las instrucciones de semáforos y buffers se ejecutan
        // en modo KERNEL, sin liberar el proceso de la CPU.
        if (instruccion.isKernelMode()) {
            instruccionEjecutada = true;
            cpu.setMode(CpuMode.KERNEL);

            procesoActual.advance();
            procesoActual.incrementCyclesOnCpu();
            procesoActual.markFirstRun(ciclo);

            kernel.reportEvent(
                ciclo,
                "Modo KERNEL: " + procesoActual.getTag()
                + " ejecuta " + instruccion
                + " | PC: " + procesoActual.getProgramCounter()
            );

            boolean bloqueado = false;

            if (instruccion.getType() == InstructionType.SEM_WAIT
                    || instruccion.getType() == InstructionType.SEM_SIGNAL
                    || instruccion.getType() == InstructionType.APPEND
                    || instruccion.getType() == InstructionType.TAKE) {

                if (syncExecutor == null) {
                    throw new IllegalStateException(
                        "No se ha conectado SyncExecutor al computador H" + id
                    );
                }

                bloqueado = syncExecutor.execute(
                    procesoActual,
                    instruccion,
                    ciclo
                );
            }

            if (bloqueado) {
                cpu.releaseProcess();
                cyclesCPU = 0;
                return;
            }

            cpu.setMode(CpuMode.USER);
            cyclesCPU++;
        }
         else {
            // Las instrucciones normales se ejecutan en modo USER.
            instruccionEjecutada = true;
            cpu.setMode(CpuMode.USER);
            cpu.onTick(ciclo);
            cyclesCPU++;
        }
    }
    

    // 5. Comprobar si el proceso terminó.
    procesoActual = cpu.getCurrentProcess();

    if (procesoActual != null && procesoActual.isFinished()) {
        cpu.releaseProcess();
        cyclesCPU = 0;

        kernel.terminateProcess(
            procesoActual,
            TerminationCause.COMPLETED,
            ciclo
        );
    }
}
    public void setPolicy(SchedulingPolicy policy) {
        if (policy != null) {
            this.policy = policy;
        }
    }

    public SchedulingPolicy getPolicy() {
        return policy;
    }
    public void addEventListener(
        avilaos.kernel.SystemEventListener listener) {
    kernel.addEventListener(listener);
}
    @Override
public String toString() {
    return "Computador " + id;
}
public ComputerMetrics getMetrics() {
    return metrics;
}
}
