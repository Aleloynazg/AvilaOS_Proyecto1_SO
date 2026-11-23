package avilaos.kernel.scheduler;

import avilaos.kernel.Kernel;
import avilaos.kernel.disk.DiskRequest;
import avilaos.kernel.disk.DiskScheduler;
import avilaos.kernel.memory.MemoryManager;
import avilaos.kernel.process.IoOperation;
import avilaos.kernel.process.PCB;
import avilaos.kernel.process.ProcessState;
import avilaos.kernel.swap.SwapArea;

/**
 * Planificador a mediano plazo: decide qué procesos salen de la RAM hacia el swap
 * (suspensión) y cuáles vuelven (reactivación). Lo llama el {@link Kernel} cada vez
 * que revisa la admisión de procesos Nuevos.
 *
 * Reglas (ver DECISIONES.md):
 * <ul>
 *   <li><b>Cuándo suspender:</b> solo cuando el primer proceso de Nuevos no cabe en la RAM.
 *       Se suspende a procesos Bloqueados hasta que quepa; si aunque se suspendieran todos
 *       los Bloqueados no cabría, no se suspende a nadie (sería en vano).</li>
 *   <li><b>A quién:</b> el Bloqueado de menor prioridad (el de número de prioridad más
 *       grande); si empatan, el que lleva más tiempo bloqueado; si siguen empatados, el de
 *       menor id. Si no hay espacio consecutivo suficiente en el swap para él, se prueba con
 *       el siguiente.</li>
 *   <li><b>Cómo:</b> se reserva su espacio en el swap y se manda una solicitud SUSPEND al disco
 *       (cuesta tantos bloques como MB tiene el proceso). Mientras el disco la atiende, el
 *       proceso sigue ocupando la RAM; cuando termina, la RAM queda libre y se admite al
 *       proceso que esperaba.</li>
 *   <li><b>Reactivar:</b> los Listos suspendidos vuelven ANTES de que se admita a un proceso
 *       Nuevo, de mayor a menor prioridad, y se detiene en el primero que no cabe (para que un
 *       proceso grande no espere mientras pasan los pequeños). La RAM se reserva al generar la
 *       solicitud RESUME; cuando el disco termina de leer el swap el proceso pasa a Listos.</li>
 * </ul>
 */
public class MediumTermScheduler {

    private final Kernel kernel;
    private final MemoryManager memory;
    private final SwapArea swap;

    /** RAM que se liberará cuando terminen las suspensiones que esperan al disco. */
    private int pendingSuspendMemory;

    private int suspensions;
    private int reactivations;

    public MediumTermScheduler(Kernel kernel, MemoryManager memory, SwapArea swap) {
        if (kernel == null || memory == null || swap == null) {
            throw new IllegalArgumentException("Faltan el kernel, la memoria o el área de swap");
        }
        this.kernel = kernel;
        this.memory = memory;
        this.swap = swap;
    }

    // ------------------------------------------------------------------
    // Suspender
    // ------------------------------------------------------------------

    /**
     * El primer proceso de Nuevos ({@code newcomer}) no cabe en la RAM: suspende a Bloqueados
     * hasta que quepa. No hace nada si ya se espera a que terminen suspensiones suficientes, si
     * no hay Bloqueados a quienes suspender o si no hay espacio en el swap.
     */
    public void makeRoomFor(PCB newcomer, long cycle) {
        DiskScheduler scheduler = diskScheduler();
        if (scheduler == null) {
            return;
        }
        int need = newcomer.getMemoryRequired();
        int expected = memory.getFreeMemory() + pendingSuspendMemory;
        if (expected >= need) {
            return;     // ya cabrá cuando terminen las suspensiones en curso
        }

        PCB[] candidates = victimCandidates();
        int potential = expected;
        for (PCB c : candidates) {
            potential += c.getMemoryRequired();
        }
        if (potential < need) {
            return;     // ni suspendiendo a todos los Bloqueados cabría: no se suspende en vano
        }

        while (expected < need) {
            int best = -1;
            for (int i = 0; i < candidates.length; i++) {
                if (candidates[i] != null && (best < 0 || betterVictim(candidates[i], candidates[best]))) {
                    best = i;
                }
            }
            if (best < 0) {
                return;     // no quedan candidatos
            }
            PCB victim = candidates[best];
            candidates[best] = null;
            if (swap.reserve(victim) < 0) {
                continue;   // no cabe en el swap: se prueba con el siguiente
            }
            victim.setSuspending(true);
            pendingSuspendMemory += victim.getMemoryRequired();
            expected += victim.getMemoryRequired();
            scheduler.addRequest(new DiskRequest(victim, IoOperation.SUSPEND, victim.getSwapStartBlock(),
                    victim.getMemoryRequired(), cycle, null, null, null, null));
            kernel.reportEvent(cycle, "Se suspende a " + victim.getTag() + " para admitir a " + newcomer.getTag()
                    + ": pide al disco escribir " + victim.getMemoryRequired() + " bloques en el swap (desde el bloque "
                    + victim.getSwapStartBlock() + ")");
        }
    }

    /** El disco terminó de escribir en el swap la memoria del proceso: sale de la RAM. */
    public void completeSuspend(DiskRequest request, long cycle) {
        PCB process = request.getProcess();
        pendingSuspendMemory = Math.max(0, pendingSuspendMemory - process.getMemoryRequired());
        if (kernel.suspendProcess(process, cycle)) {
            suspensions++;
        }
    }

    /**
     * Un proceso terminó con su suspensión todavía en curso: su RAM ya se liberó al terminar,
     * así que ya no hay que esperarla. (Su solicitud, si seguía en cola, el kernel la canceló.)
     */
    public void suspensionAborted(PCB process) {
        pendingSuspendMemory = Math.max(0, pendingSuspendMemory - process.getMemoryRequired());
    }

    // ------------------------------------------------------------------
    // Reactivar
    // ------------------------------------------------------------------

    /**
     * Reactiva a los Listos suspendidos que quepan en la RAM, el de mayor prioridad primero.
     * Para cada uno reserva su RAM y manda una solicitud RESUME al disco.
     */
    public void reactivate(long cycle) {
        DiskScheduler scheduler = diskScheduler();
        if (scheduler == null) {
            return;
        }
        while (true) {
            PCB best = null;
            Object[] suspended = kernel.getReadySuspendedQueue().toArray();
            for (Object o : suspended) {
                PCB p = (PCB) o;
                if (p.isSuspending() || p.isResuming() || p.getSwapStartBlock() < 0) {
                    continue;   // todavía está saliendo, ya está volviendo, o no tiene su memoria en el swap
                }
                if (best == null || p.getPriority() < best.getPriority()
                        || (p.getPriority() == best.getPriority() && p.getId() < best.getId())) {
                    best = p;
                }
            }
            if (best == null || !memory.allocate(best.getMemoryRequired())) {
                return;     // nadie por reactivar, o el primero no cabe: se espera
            }
            best.setResuming(true);
            scheduler.addRequest(new DiskRequest(best, IoOperation.RESUME, best.getSwapStartBlock(),
                    best.getMemoryRequired(), cycle, null, null, null, null));
            kernel.reportEvent(cycle, "Se reactiva a " + best.getTag() + ": pide al disco leer "
                    + best.getMemoryRequired() + " bloques del swap. RAM libre: " + memory.getFreeMemory() + " MB");
        }
    }

    /** El disco terminó de leer el swap: el proceso vuelve a Listos y su espacio en el swap queda libre. */
    public void completeResume(DiskRequest request, long cycle) {
        if (kernel.resumeProcess(request.getProcess(), cycle, true)) {
            reactivations++;
        }
    }

    // ------------------------------------------------------------------

    /** Bloqueados que se pueden suspender: no están ya en camino al swap ni tienen swap reservado. */
    private PCB[] victimCandidates() {
        Object[] blocked = kernel.getBlockedQueue().toArray();
        PCB[] result = new PCB[blocked.length];
        int n = 0;
        for (Object o : blocked) {
            PCB p = (PCB) o;
            if (p.getState() == ProcessState.BLOCKED && !p.isSuspending() && p.getSwapStartBlock() < 0) {
                result[n++] = p;
            }
        }
        PCB[] exact = new PCB[n];
        System.arraycopy(result, 0, exact, 0, n);
        return exact;
    }

    /** true si {@code a} es mejor víctima que {@code b}: menor prioridad, luego más tiempo bloqueado, luego menor id. */
    private static boolean betterVictim(PCB a, PCB b) {
        if (a.getPriority() != b.getPriority()) {
            return a.getPriority() > b.getPriority();
        }
        if (a.getBlockedSinceCycle() != b.getBlockedSinceCycle()) {
            return a.getBlockedSinceCycle() < b.getBlockedSinceCycle();
        }
        return a.getId() < b.getId();
    }

    private DiskScheduler diskScheduler() {
        return swap.getDisk().getScheduler();
    }

    public int getSuspensionCount() {
        return suspensions;
    }

    public int getReactivationCount() {
        return reactivations;
    }

    /** RAM que se liberará cuando terminen las suspensiones en curso. */
    public int getPendingSuspendMemory() {
        return pendingSuspendMemory;
    }

    public SwapArea getSwap() {
        return swap;
    }
}
