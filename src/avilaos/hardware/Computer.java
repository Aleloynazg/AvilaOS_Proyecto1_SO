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



public class Computer implements ClockListener {

    private final int id;
    private final CPU cpu;
    private final MemoryManager memory;
    private final Kernel kernel;
    private SchedulingPolicy policy;

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
        System.out.println(
        "Computer " + id + " recibió el ciclo " + ciclo
    );
        kernel.tickTimedBlocks(ciclo);
        kernel.tickDeadlines(ciclo);
        kernel.admitNewProcesses(ciclo);

        PCB procesoActual = cpu.getCurrentProcess();

        if (procesoActual != null && procesoActual.tickDeadline()) {
            cpu.releaseProcess();
            kernel.terminateProcess(
                procesoActual,
                TerminationCause.DEADLINE_MISSED,
                ciclo
            );
            procesoActual = null;
        }

        if (cpu.isIdle()) {
            PCB siguiente = policy.selectNext(kernel.getReadyQueue());

            if (siguiente != null) {
                cpu.setMode(CpuMode.KERNEL);
                cpu.assignProcess(siguiente);
                return;
            }

            cpu.setMode(CpuMode.KERNEL);
            return;
        }

        cpu.setMode(CpuMode.USER);
        cpu.onTick(ciclo);

        procesoActual = cpu.getCurrentProcess();

        if (procesoActual != null && procesoActual.isFinished()) {
            cpu.releaseProcess();
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
}
