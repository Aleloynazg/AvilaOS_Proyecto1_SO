package avilaos.config;

import avilaos.config.SimulationFactory.Simulation;
import avilaos.hardware.Computer;
import avilaos.kernel.disk.DiskPolicyType;
import avilaos.kernel.process.PCB;
import avilaos.kernel.process.ProcessState;
import avilaos.kernel.process.TerminationCause;
import java.io.File;
import java.io.IOException;

/**
 * Prueba integrada de la suspensión de procesos (F4.4) con config/p2/suspension.json: una RAM de
 * 128 MB donde dos procesos de E/S (50 MB cada uno) se quedan bloqueados mientras un proceso
 * urgente de 90 MB espera en la cola de Nuevos. El planificador a mediano plazo suspende a los
 * dos en el swap, deja entrar al urgente y los reactiva cuando termina. El reloj se maneja a
 * mano (Computer.onTick y luego Disk.onTick) para que sea determinista.
 *
 * En cada ciclo se comprueba que la RAM ocupada es exactamente la de los procesos que la tienen
 * (nunca se libera ni se reserva dos veces) y que nunca pasa de la capacidad.
 *
 * Se ejecuta desde la carpeta del proyecto (usa config/p2/...). Termina con código 1 si algo falla.
 */
public class SuspensionPrueba {

    private static final String PROCESOS = "config/p2/suspension.json";
    private static final String ARCHIVOS = "config/p2/sistema_archivos_enunciado.json";
    private static final int RAM = 128;

    private static int total;
    private static int fallos;

    // Estado de la corrida en curso.
    private static long cycle;
    private static StringBuilder log;
    private static PCB[] procesos;
    private static boolean[][] visto;
    private static boolean ramConsistente;
    private static int ramMaxima;
    private static int swapMaximo;
    private static long ciclosEnNuevoUrgente;

    public static void main(String[] args) throws IOException {
        escenarioBase();
        conCadaPolitica();
        swapDemasiadoChico();
        deadlineEnTransito();
        deadlineSuspendido();
        guardarConSuspendidos();

        System.out.println();
        System.out.println(total + " comprobaciones, " + fallos + " fallos");
        if (fallos > 0) {
            System.exit(1);
        }
    }

    private static void escenarioBase() throws IOException {
        System.out.println("--- 1. Dos procesos bloqueados se suspenden para que entre el urgente (FIFO) ---");
        Simulation sim = armar(DiskPolicyType.FIFO, 100, null);
        Computer c = sim.computer;
        check("arranca con 3 procesos nuevos, RAM de 128 MB y swap de 100 bloques",
                procesos.length == 3 && c.getMemoryManager().getTotalMemory() == RAM
                        && c.getDisk().getSwapBlockCount() == 100);
        PCB lector1 = procesos[0];
        PCB lector2 = procesos[1];
        PCB urgente = procesos[2];

        correr(c, 3000);

        check("los tres procesos terminan COMPLETED", todosCompleted());
        check("el planificador suspendió a los dos lectores y los reactivó una vez cada uno",
                c.getKernel().getSuspensionCount() == 2 && c.getKernel().getReactivationCount() == 2);
        check("el log explica a quién se suspende y para quién",
                log.indexOf("Se suspende a " + lector1.getTag() + " para admitir a " + urgente.getTag()) >= 0
                        && log.indexOf("Se suspende a " + lector2.getTag() + " para admitir a " + urgente.getTag()) >= 0);
        check("solo hubo una ronda de suspensiones (el urgente no volvió a esperar)", cuenta("Se suspende a ") == 2);
        check("lector_1 (prioridad 8, la menor) se suspende primero: su swap empieza en el 200 y el de lector_2 en el 250",
                log.indexOf("Se suspende a " + lector1.getTag() + " para admitir a " + urgente.getTag()
                        + ": pide al disco escribir 50 bloques en el swap (desde el bloque 200)") >= 0
                        && log.indexOf("Se suspende a " + lector2.getTag() + " para admitir a " + urgente.getTag()
                        + ": pide al disco escribir 50 bloques en el swap (desde el bloque 250)") >= 0);
        check("lector_1 pasó por Bloqueado suspendido y por Listo suspendido",
                visto[0][ProcessState.BLOCKED_SUSPENDED.ordinal()] && visto[0][ProcessState.READY_SUSPENDED.ordinal()]);
        check("lector_2 pasó por Listo suspendido (su espera terminó mientras se suspendía)",
                visto[1][ProcessState.READY_SUSPENDED.ordinal()]);
        check("el urgente nunca estuvo suspendido", !visto[2][ProcessState.READY_SUSPENDED.ordinal()]
                && !visto[2][ProcessState.BLOCKED_SUSPENDED.ordinal()]);
        check("los dos lectores estuvieron en el swap a la vez (100 de 100 bloques)", swapMaximo == 100);
        check("el urgente entró cuando ya habían salido los dos lectores (no antes del ciclo 100)",
                ciclosEnNuevoUrgente > 100 && ciclosEnNuevoUrgente < 200);
        check("el urgente termina antes de que se reactive ningún lector",
                log.indexOf(urgente.getTag() + " termina correctamente")
                        < log.indexOf("Se reactiva a "));
        check("se reactiva primero lector_2 (prioridad 6) y después lector_1 (prioridad 8)",
                log.indexOf("Se reactiva a " + lector2.getTag()) >= 0
                        && log.indexOf("Se reactiva a " + lector2.getTag()) < log.indexOf("Se reactiva a " + lector1.getTag()));
        check("el urgente terminó mucho antes que sin suspensión (antes del ciclo 250; sin ella esperaría a que terminen los lectores)",
                urgente.getFinishCycle() < 250);
        check("la RAM ocupada coincidió en cada ciclo con la de los procesos que la tienen", ramConsistente);
        check("la RAM nunca pasó de 128 MB (máximo visto: " + ramMaxima + ")", ramMaxima <= RAM);
        check("al final no queda RAM ni swap ocupados",
                c.getMemoryManager().getUsedMemory() == 0 && c.getKernel().getSwap().getUsedBlocks() == 0);
        check("no queda nadie en las colas de suspendidos",
                c.getKernel().getReadySuspendedQueue().isEmpty() && c.getKernel().getBlockedSuspendedQueue().isEmpty());
        check("el disco atendió las 5 solicitudes: 2 SUSPEND, 1 READ y 2 RESUME", c.getDisk().getCompletedRequests() == 5);
        check("el READ del urgente sí tocó el sistema de archivos (lee config.sys)",
                log.indexOf("lee /sistema/config.sys (24 bloques)") >= 0);
        System.out.println("  terminan: lector_1 en el ciclo " + lector1.getFinishCycle() + ", lector_2 en el "
                + lector2.getFinishCycle() + ", urgente en el " + urgente.getFinishCycle());
    }

    private static void conCadaPolitica() throws IOException {
        System.out.println("--- 2. Lo mismo con cada política de disco ---");
        System.out.println("  (política | suspensiones | reactivaciones | ciclo en que termina el urgente | ciclo final)");
        DiskPolicyType[] politicas = DiskPolicyType.values();
        for (DiskPolicyType politica : politicas) {
            Simulation sim = armar(politica, 100, null);
            correr(sim.computer, 3000);
            System.out.println("  " + politica.getLabel() + " | " + sim.computer.getKernel().getSuspensionCount()
                    + " | " + sim.computer.getKernel().getReactivationCount() + " | " + procesos[2].getFinishCycle()
                    + " | " + cycle);
            check(politica.getLabel() + ": los tres terminan COMPLETED", todosCompleted());
            check(politica.getLabel() + ": se suspende a los dos lectores y se los reactiva",
                    sim.computer.getKernel().getSuspensionCount() == 2
                            && sim.computer.getKernel().getReactivationCount() == 2);
            check(politica.getLabel() + ": la RAM siempre fue consistente y no queda nada ocupado",
                    ramConsistente && ramMaxima <= RAM && sim.computer.getMemoryManager().getUsedMemory() == 0
                            && sim.computer.getKernel().getSwap().getUsedBlocks() == 0);
        }
    }

    private static void swapDemasiadoChico() throws IOException {
        System.out.println("--- 3. Swap de 40 bloques: los lectores (50 MB) no caben, el urgente espera ---");
        Simulation sim = armar(DiskPolicyType.FIFO, 40, null);
        Computer c = sim.computer;
        correr(c, 3000);
        check("no se suspende a nadie", c.getKernel().getSuspensionCount() == 0
                && c.getKernel().getReactivationCount() == 0 && log.indexOf("Se suspende a ") < 0);
        check("los tres terminan COMPLETED de todos modos", todosCompleted());
        check("el urgente solo entró cuando terminaron los dos lectores (necesita 90 MB y quedaban 28 o 78)",
                procesos[2].getFinishCycle() > procesos[0].getFinishCycle()
                        && procesos[2].getFinishCycle() > procesos[1].getFinishCycle());
        check("la RAM siempre fue consistente", ramConsistente && ramMaxima <= RAM);
    }

    private static void deadlineEnTransito() throws IOException {
        System.out.println("--- 4. Un lector se queda sin deadline mientras se está suspendiendo (deadline 60) ---");
        Simulation sim = armar(DiskPolicyType.FIFO, 100, 60);
        Computer c = sim.computer;
        correr(c, 3000);
        PCB lector1 = procesos[0];
        check("lector_1 termina por DEADLINE_MISSED",
                lector1.getState() == ProcessState.TERMINATED
                        && lector1.getTerminationCause() == TerminationCause.DEADLINE_MISSED);
        check("terminó en tránsito: su SUSPEND seguía en el disco (no llegó a quedar suspendido)",
                !visto[0][ProcessState.BLOCKED_SUSPENDED.ordinal()] && lector1.getFinishCycle() <= 61);
        check("el disco descartó su solicitud",
                log.indexOf("El disco descarta la solicitud de " + lector1.getTag()) >= 0);
        check("los otros dos terminan COMPLETED",
                procesos[1].getTerminationCause() == TerminationCause.COMPLETED
                        && procesos[2].getTerminationCause() == TerminationCause.COMPLETED);
        check("su RAM y su swap se liberaron una sola vez (consistentes en todos los ciclos y vacíos al final)",
                ramConsistente && ramMaxima <= RAM && c.getMemoryManager().getUsedMemory() == 0
                        && c.getKernel().getSwap().getUsedBlocks() == 0);
    }

    private static void deadlineSuspendido() throws IOException {
        System.out.println("--- 5. Un lector se queda sin deadline estando Bloqueado suspendido (deadline 90) ---");
        Simulation sim = armar(DiskPolicyType.FIFO, 100, 90);
        Computer c = sim.computer;
        correr(c, 3000);
        PCB lector1 = procesos[0];
        check("lector_1 estuvo Bloqueado suspendido y termina por DEADLINE_MISSED",
                visto[0][ProcessState.BLOCKED_SUSPENDED.ordinal()]
                        && lector1.getTerminationCause() == TerminationCause.DEADLINE_MISSED);
        check("terminó con su deadline (ciclo 90) estando suspendido, no por otra cosa",
                lector1.getFinishCycle() >= 89 && lector1.getFinishCycle() <= 91);
        check("sus 50 bloques de swap quedaron libres y su RAM no se descontó otra vez",
                ramConsistente && c.getKernel().getSwap().getUsedBlocks() == 0
                        && c.getMemoryManager().getUsedMemory() == 0);
        check("los otros dos terminan COMPLETED",
                procesos[1].getTerminationCause() == TerminationCause.COMPLETED
                        && procesos[2].getTerminationCause() == TerminationCause.COMPLETED);
    }

    private static void guardarConSuspendidos() throws IOException {
        System.out.println("--- 6. Guardar la simulación con procesos suspendidos ---");
        Simulation sim = armar(DiskPolicyType.FIFO, 100, null);
        Computer c = sim.computer;
        correr(c, 110);   // en el ciclo 110 lector_1 y lector_2 están suspendidos o saliendo al swap
        check("en el ciclo 110 hay procesos en las colas de suspendidos",
                c.getKernel().getReadySuspendedQueue().size() + c.getKernel().getBlockedSuspendedQueue().size() >= 1);
        ProcessesConfig guardado = SimulationFactory.describeProcesses(c, sim.clock);
        check("el JSON guardado incluye a los 3 procesos (ninguno se pierde por estar suspendido)",
                guardado.processes.length == 3);
        String json = ConfigStore.toJson(guardado);
        ProcessesConfig leido = ConfigStore.parseProcesses(json);
        check("y se vuelve a leer sin errores, con los nombres y los tipos originales",
                leido.processes.length == 3 && "Lector_1".equals(leido.processes[0].name)
                        && "IO_BOUND".equals(leido.processes[0].type)
                        && "Urgente".equals(leido.processes[2].name) && "READ".equals(leido.processes[2].operation));
        check("la velocidad del cabezal (10) quedó en el archivo",
                leido.globalConfig.headSpeed != null && leido.globalConfig.headSpeed == 10);
    }

    // ------------------------------------------------------------------

    /**
     * Carga los JSON, aplica los cambios de la prueba y arma la simulación.
     *
     * @param swap     bloques de swap (en el JSON hay 100)
     * @param deadline deadline de lector_1, o null para dejar el del JSON
     */
    private static Simulation armar(DiskPolicyType politica, int swap, Integer deadline) throws IOException {
        ProcessesConfig config = ConfigStore.loadProcesses(new File(PROCESOS));
        config.globalConfig.initialDiskPolicy = politica.getLabel();
        config.globalConfig.swapSizeBlocks = swap;
        if (deadline != null) {
            config.processes[0].deadline = deadline;
        }
        FileSystemConfig archivos = ConfigStore.loadFileSystem(new File(ARCHIVOS));
        Simulation sim = SimulationFactory.createP2(config, archivos);

        cycle = 0;
        log = new StringBuilder();
        sim.computer.addEventListener((ciclo, mensaje) -> log.append(mensaje).append('\n'));
        Object[] nuevos = sim.computer.getKernel().getNewQueue().toArray();
        procesos = new PCB[nuevos.length];
        for (int i = 0; i < nuevos.length; i++) {
            procesos[i] = (PCB) nuevos[i];
        }
        visto = new boolean[procesos.length][ProcessState.values().length];
        ramConsistente = true;
        ramMaxima = 0;
        swapMaximo = 0;
        ciclosEnNuevoUrgente = 0;
        return sim;
    }

    /** Un ciclo de reloj = Computer.onTick + Disk.onTick; revisa los invariantes después de cada ciclo. */
    private static void correr(Computer c, int hastaCiclo) {
        while (cycle < hastaCiclo) {
            boolean todos = true;
            for (PCB p : procesos) {
                todos &= p.getState() == ProcessState.TERMINATED;
            }
            if (todos) {
                return;
            }
            cycle++;
            c.onTick(cycle);
            c.getDisk().onTick(cycle);
            observar(c);
        }
    }

    private static void observar(Computer c) {
        int esperada = 0;
        for (int i = 0; i < procesos.length; i++) {
            PCB p = procesos[i];
            visto[i][p.getState().ordinal()] = true;
            if (p.holdsMemory()) {
                esperada += p.getMemoryRequired();
            }
        }
        int usada = c.getMemoryManager().getUsedMemory();
        if (usada != esperada) {
            if (ramConsistente) {
                System.out.println("    (ciclo " + cycle + ": RAM usada " + usada + " MB, pero la de los procesos que la tienen es "
                        + esperada + " MB)");
            }
            ramConsistente = false;
        }
        ramMaxima = Math.max(ramMaxima, usada);
        swapMaximo = Math.max(swapMaximo, c.getKernel().getSwap().getUsedBlocks());
        if (procesos.length == 3 && procesos[2].getState() == ProcessState.NEW) {
            ciclosEnNuevoUrgente = cycle + 1;   // el último ciclo en que seguía Nuevo, más uno = el ciclo en que entró
        }
    }

    private static boolean todosCompleted() {
        for (PCB p : procesos) {
            if (p.getState() != ProcessState.TERMINATED || p.getTerminationCause() != TerminationCause.COMPLETED) {
                return false;
            }
        }
        return true;
    }

    private static int cuenta(String texto) {
        int n = 0;
        int pos = 0;
        String todo = log.toString();
        while ((pos = todo.indexOf(texto, pos)) >= 0) {
            n++;
            pos += texto.length();
        }
        return n;
    }

    private static void check(String name, boolean ok) {
        total++;
        if (!ok) {
            fallos++;
        }
        System.out.println((ok ? "  OK   " : "  FALLA ") + name);
        if (!ok && log != null && log.length() < 12000) {
            System.out.println("    ---- log ----");
            System.out.println("    " + log.toString().replace("\n", "\n    "));
        }
    }
}
