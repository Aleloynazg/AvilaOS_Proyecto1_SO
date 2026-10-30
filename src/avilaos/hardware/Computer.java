/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.hardware;

/**
  * Representa un computador dentro del simulador.
 *
 * Cada computador administra su propia CPU, memoria RAM, Kernel,
 * política de planificación y métricas de ejecución.
 *
 * Coordina la ejecución de procesos en cada ciclo del reloj global,
 * incluyendo la planificación, ejecución de instrucciones,
 * gestión de bloqueos y sincronización.
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
 /**
     * Construye un computador e inicializa sus componentes.
     *
     * Configura la CPU, la memoria RAM, el Kernel, la política FCFS
     * predeterminada y el sistema de métricas. También conecta los
     * eventos de la CPU con el Kernel.
     *
     * @param id identificador del computador.
     * @param capacidadRAM capacidad total de memoria RAM.
     * @throws IllegalArgumentException si el identificador no es positivo.
     */
    
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
    /**
     * Obtiene el identificador del computador.
     *
     * @return identificador del computador.
     */
    public int getId() {
        return id;
    }
    /**
     * Obtiene la CPU asociada al computador.
     *
     * @return instancia de la CPU.
     */
    public CPU getCPU() {
        return cpu;
    }
    /**
     * Obtiene el administrador de memoria RAM.
     *
     * @return instancia del administrador de memoria.
     */
    public MemoryManager getMemoryManager() {
        return memory;
    }
    /**
     * Obtiene el Kernel asociado al computador.
     *
     * @return instancia del Kernel.
     */
    public Kernel getKernel() {
        return kernel;
    }
        /**
     * Configura el ejecutor de instrucciones de sincronización.
     *
     * @param syncExecutor ejecutor encargado de gestionar semáforos
     *                     y operaciones sobre buffers.
     */
    public void setSyncExecutor(SyncExecutor syncExecutor) {
        this.syncExecutor = syncExecutor;}

        /**
     * Muestra por consola el estado actual del computador,
     * incluyendo el estado de la CPU, su modo de ejecución
     * y la memoria RAM utilizada y disponible.
     */
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
    /**
     * Registra este computador como oyente del reloj global.
     *
     * @param reloj reloj global que notificará los ciclos de ejecución.
     */
    public void conectarReloj(GlobalClock reloj) {
        if (reloj != null) {
            reloj.addListener(this);
        }
    }

        /**
     * Recibe la notificación de un nuevo ciclo del reloj global.
     *
     * Reinicia el indicador de ejecución, procesa las actividades
     * correspondientes al ciclo y registra las métricas obtenidas.
     *
     * @param ciclo número del ciclo actual.
     */
    @Override
    public void onTick(long ciclo) {
        instruccionEjecutada = false;

        try {
            procesarCiclo(ciclo);
        } finally {
            metrics.registrarCiclo(ciclo, instruccionEjecutada);
        }
    }
/**
 * Procesa las actividades del computador durante un ciclo del reloj.
 *
 * Actualiza los bloqueos y deadlines, admite nuevos procesos,
 * aplica la política de planificación y ejecuta una instrucción
 * del proceso seleccionado.
 *
 * También gestiona las solicitudes de E/S, las instrucciones
 * de sincronización y la finalización de procesos.
 *
 * @param ciclo número del ciclo actual del reloj global.
 */
    private void procesarCiclo(long ciclo) {
        kernel.tickTimedBlocks(ciclo);
        kernel.tickDeadlines(ciclo);
        kernel.admitNewProcesses(ciclo);
        kernel.contarTiempoEsperaReady();

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
        /**
     * Establece la política de planificación del computador.
     *
     * Si la política recibida no es nula, sustituye la política actual.
     *
     * @param policy nueva política de planificación.
     */
    public void setPolicy(SchedulingPolicy policy) {
        if (policy != null) {
            this.policy = policy;
        }
    }
    /**
     * Obtiene la política de planificación configurada.
     *
     * @return política de planificación actual.
     */
    public SchedulingPolicy getPolicy() {
        return policy;
    }
        /**
     * Registra un oyente para recibir los eventos del sistema
     * generados por el Kernel de este computador.
     *
     * @param listener componente que recibirá los eventos.
     */
    public void addEventListener(
        avilaos.kernel.SystemEventListener listener) {
    kernel.addEventListener(listener);
}
        /**
     * Devuelve una representación del computador.
     *
     * @return nombre del computador seguido de su identificador.
     */
    @Override
    public String toString() {
        return "Computador " + id;
    }
        /**
     * Obtiene las métricas registradas para este computador.
     *
     * @return objeto con las métricas de ejecución.
     */
    public ComputerMetrics getMetrics() {
        return metrics;
    }
    }
