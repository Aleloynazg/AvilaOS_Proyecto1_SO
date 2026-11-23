package avilaos.kernel.scheduler;

import avilaos.hardware.disk.Disk;
import avilaos.kernel.Kernel;
import avilaos.kernel.disk.DiskPolicyFactory;
import avilaos.kernel.disk.DiskPolicyType;
import avilaos.kernel.disk.DiskScheduler;
import avilaos.kernel.memory.MemoryManager;
import avilaos.kernel.process.BlockReason;
import avilaos.kernel.process.PCB;
import avilaos.kernel.process.ProcessState;
import avilaos.kernel.process.ProcessType;
import avilaos.kernel.process.TerminationCause;

/**
 * Prueba del planificador a mediano plazo (F4.3): cuándo y a quién se suspende, cómo se
 * reactiva, y qué pasa si el proceso termina mientras su suspensión o reactivación espera al
 * disco. Se maneja el reloj a mano (kernel y luego disco, como hace el computador).
 * Ejecutar con Run File; termina con código 1 si algo falla.
 */
public class MediumTermSchedulerPrueba {

    private static int total;
    private static int fallos;

    private static MemoryManager ram;
    private static Kernel kernel;
    private static Disk disk;
    private static DiskScheduler diskScheduler;
    private static StringBuilder log;
    private static long cycle;

    public static void main(String[] args) {
        System.out.println("--- 1. Se suspende al Bloqueado de menor prioridad cuando un Nuevo no cabe ---");
        montar(100, 100);
        PCB a = admitido("A", 2, 40);
        PCB b = admitido("B", 7, 40);
        bloquear(a);
        cycle++;
        bloquear(b);
        PCB c = nuevo("C", 1, 50);
        kernel.admitNewProcesses(cycle);
        check("C no cabe: RAM libre 20 de 50", c.getState() == ProcessState.NEW && ram.getFreeMemory() == 20);
        check("se suspende a B (prioridad 7, la menor), no a A",
                b.isSuspending() && !a.isSuspending() && b.getState() == ProcessState.BLOCKED);
        check("se reservaron 40 bloques de swap para B desde el 200",
                b.getSwapStartBlock() == 200 && kernel.getSwap().blocksOf(b) == 40);
        check("hay una solicitud SUSPEND en la cola del disco", diskScheduler.getRequestCount() == 1);
        check("el kernel sabe cuánta RAM va a liberar", kernel.getMediumTermScheduler().getPendingSuspendMemory() == 40);
        check("el log lo explica", log.indexOf("Se suspende a " + b.getTag() + " para admitir a " + c.getTag()) >= 0);
        kernel.admitNewProcesses(cycle + 1);
        check("revisar otra vez no suspende a nadie más", !a.isSuspending() && diskScheduler.getRequestCount() == 1);
        ciclos(60);
        check("el disco atendió la solicitud y B quedó Bloqueado suspendido",
                b.getState() == ProcessState.BLOCKED_SUSPENDED && kernel.getBlockedSuspendedQueue().size() == 1);
        check("C entró a Listos con la RAM de B", c.getState() == ProcessState.READY && ram.getUsedMemory() == 90);
        check("una suspensión contada", kernel.getSuspensionCount() == 1 && kernel.getReactivationCount() == 0);
        check("ya no hay RAM pendiente de liberar", kernel.getMediumTermScheduler().getPendingSuspendMemory() == 0);
        check("el log dice que el disco escribió en el swap", log.indexOf("El disco termina de escribir a " + b.getTag()) >= 0);
        check("B conserva su memoria en el swap (40 ocupados)", kernel.getSwap().getUsedBlocks() == 40);

        System.out.println("--- 2. Reactivación cuando hay RAM ---");
        kernel.unblockProcess(b, cycle);
        check("al terminar su espera B pasa a Listo suspendido", b.getState() == ProcessState.READY_SUSPENDED);
        kernel.admitNewProcesses(cycle);
        check("sin RAM libre (10 de 40) no se reactiva todavía", !b.isResuming() && diskScheduler.getRequestCount() == 0);
        kernel.terminateProcess(c, TerminationCause.COMPLETED, cycle);
        check("al liberarse la RAM de C se pide la reactivación de B",
                b.isResuming() && diskScheduler.getRequestCount() == 1);
        check("la RAM de B se reservó al generar la solicitud (usada 80)", ram.getUsedMemory() == 80);
        check("mientras el disco lee, B sigue en Listo suspendido", b.getState() == ProcessState.READY_SUSPENDED);
        ciclos(60);
        check("B volvió a Listos", b.getState() == ProcessState.READY && kernel.getReadyQueue().size() == 1);
        check("su espacio en el swap quedó libre", kernel.getSwap().getUsedBlocks() == 0 && b.getSwapStartBlock() == -1);
        check("RAM usada sigue en 80 (no se reservó dos veces)", ram.getUsedMemory() == 80);
        check("una reactivación contada", kernel.getReactivationCount() == 1 && !b.isResuming());

        System.out.println("--- 3. Empate de prioridad: el que lleva más tiempo bloqueado ---");
        montar(100, 100);
        PCB p1 = admitido("P1", 5, 30);
        PCB p2 = admitido("P2", 5, 30);
        PCB p3 = admitido("P3", 5, 30);
        cycle = 10;
        bloquear(p2);
        cycle = 12;
        bloquear(p3);
        cycle = 20;
        bloquear(p1);
        nuevo("N", 1, 50);
        kernel.admitNewProcesses(cycle);
        check("primero P2 (bloqueado desde el ciclo 10): su swap empieza en el 200",
                p2.isSuspending() && p2.getSwapStartBlock() == 200);
        check("con P2 hay 10 + 30 = 40 < 50: sigue P3 (desde el 12, swap en el 230) y P1 (desde el 20) no hace falta",
                p3.isSuspending() && p3.getSwapStartBlock() == 230 && !p1.isSuspending());

        System.out.println("--- 4. Si no hay espacio en el swap para la víctima se prueba con otra ---");
        montar(100, 50);
        PCB grande = admitido("Grande", 9, 60);
        PCB chico = admitido("Chico", 3, 30);
        bloquear(grande);
        cycle++;
        bloquear(chico);
        nuevo("N", 1, 50);
        kernel.admitNewProcesses(cycle);
        check("Grande (60 MB) no cabe en un swap de 50 bloques: se suspende a Chico",
                !grande.isSuspending() && chico.isSuspending() && chico.getSwapStartBlock() == 200);

        System.out.println("--- 5. No se suspende en vano ---");
        montar(100, 100);
        PCB listo = admitido("Listo", 5, 60);
        PCB bloqueado = admitido("Bloqueado", 5, 20);
        bloquear(bloqueado);
        nuevo("N", 1, 50);
        kernel.admitNewProcesses(cycle);
        check("20 libres + 20 del Bloqueado = 40 < 50: no se suspende a nadie",
                !bloqueado.isSuspending() && diskScheduler.getRequestCount() == 0 && listo.getState() == ProcessState.READY);
        check("a un proceso Listo nunca se le suspende por esta vía", !listo.isSuspending());

        System.out.println("--- 6. Sin Bloqueados no hay a quién suspender ---");
        montar(100, 100);
        admitido("L1", 5, 60);
        admitido("L2", 5, 30);
        PCB espera = nuevo("N", 1, 50);
        kernel.admitNewProcesses(cycle);
        check("N sigue Nuevo y no hay solicitudes", espera.getState() == ProcessState.NEW
                && diskScheduler.getRequestCount() == 0 && kernel.getSuspensionCount() == 0);

        System.out.println("--- 7. Los Listos suspendidos vuelven antes que los Nuevos ---");
        montar(100, 100);
        PCB s = suspendidoListo("S", 5, 30);
        PCB n2 = nuevo("N2", 1, 30);
        ram.allocate(70);   // queda justo 30 libres: solo uno de los dos cabe
        kernel.admitNewProcesses(cycle);
        check("se reactiva S, no se admite N2", s.isResuming() && n2.getState() == ProcessState.NEW);
        check("la RAM de S está reservada (libre 0)", ram.getFreeMemory() == 0);

        System.out.println("--- 8. Reactivación por prioridad y sin saltarse al primero ---");
        montar(100, 100);
        PCB alta = admitido("Alta", 2, 50);
        PCB baja = admitido("Baja", 8, 20);
        aListoSuspendido(alta);   // (se suspenden después de admitir a ambos, para que ninguno vuelva antes de tiempo)
        aListoSuspendido(baja);
        ram.allocate(60);   // libres: 40
        kernel.admitNewProcesses(cycle);
        check("Alta (50 MB) no cabe en 40 libres: no se reactiva ni se salta a Baja (20 MB)",
                !alta.isResuming() && !baja.isResuming() && diskScheduler.getRequestCount() == 0);
        ram.free(20);   // libres: 60
        kernel.admitNewProcesses(cycle);
        check("con 60 libres se reactiva Alta primero", alta.isResuming() && !baja.isResuming());
        ram.free(20);   // libres: 30 (50 reservados por Alta + 40 usados)
        kernel.admitNewProcesses(cycle);
        check("luego Baja", baja.isResuming());

        System.out.println("--- 9. El proceso muere con su SUSPEND esperando en la cola ---");
        montar(100, 100);
        PCB v = admitido("V", 7, 40);
        PCB w = admitido("W", 2, 40);
        bloquear(v);
        bloquear(w);
        nuevo("N", 1, 50);
        kernel.admitNewProcesses(cycle);
        check("V es la víctima (SUSPEND en cola)", v.isSuspending() && diskScheduler.getRequestCount() == 1);
        kernel.terminateProcess(v, TerminationCause.DEADLINE_MISSED, cycle);
        check("la solicitud se canceló", diskScheduler.getRequestCount() == 0);
        check("su swap quedó libre y ya no hay RAM pendiente",
                kernel.getSwap().getUsedBlocks() == 0 && kernel.getMediumTermScheduler().getPendingSuspendMemory() == 0);
        check("su RAM se liberó una sola vez y entró N (40 de W + 50 de N)", ram.getUsedMemory() == 90);

        System.out.println("--- 10. El proceso muere con su SUSPEND ya en el disco ---");
        montar(100, 100);
        PCB x = admitido("X", 7, 40);
        PCB y = admitido("Y", 2, 40);
        bloquear(x);
        bloquear(y);
        nuevo("N", 1, 50);
        kernel.admitNewProcesses(cycle);
        ciclos(1);   // el disco toma la solicitud
        check("el disco ya la tomó (cola vacía) y X sigue en tránsito", diskScheduler.getRequestCount() == 0 && x.isSuspending());
        kernel.terminateProcess(x, TerminationCause.DEADLINE_MISSED, cycle);
        check("X terminó y liberó su RAM una vez", ram.getUsedMemory() == 90 && !x.holdsMemory());
        ciclos(60);
        check("el disco descartó la solicitud sin contar una suspensión",
                kernel.getSuspensionCount() == 0 && log.indexOf("El disco descarta la solicitud de " + x.getTag()) >= 0);
        check("el swap sigue libre", kernel.getSwap().getUsedBlocks() == 0);
        check("la RAM no se tocó dos veces", ram.getUsedMemory() == 90);

        System.out.println("--- 11. El proceso despierta mientras su SUSPEND espera al disco ---");
        montar(100, 100);
        PCB q = admitido("Q", 7, 40);
        PCB r = admitido("R", 2, 40);
        bloquear(q);
        bloquear(r);
        PCB nn = nuevo("N", 1, 50);
        kernel.admitNewProcesses(cycle);
        kernel.unblockProcess(q, cycle);
        check("Q queda Listo suspendido en tránsito y aún con RAM",
                q.getState() == ProcessState.READY_SUSPENDED && q.holdsMemory() && ram.getUsedMemory() == 80);
        ciclos(60);
        check("el disco termina: Q sale de la RAM y entra N",
                !q.holdsMemory() && q.getState() == ProcessState.READY_SUSPENDED && nn.getState() == ProcessState.READY);
        check("una suspensión contada y Q una sola vez en su cola",
                kernel.getSuspensionCount() == 1 && kernel.getReadySuspendedQueue().size() == 1);

        System.out.println("--- 12. El proceso muere con su RESUME en curso ---");
        montar(100, 100);
        PCB z = suspendidoListo("Z", 5, 40);
        kernel.admitNewProcesses(cycle);
        check("Z se reactiva (RESUME en cola, RAM reservada)", z.isResuming() && ram.getUsedMemory() == 40
                && diskScheduler.getRequestCount() == 1);
        kernel.terminateProcess(z, TerminationCause.DEADLINE_MISSED, cycle);
        check("termina: la RAM reservada y el swap quedan libres",
                ram.getUsedMemory() == 0 && kernel.getSwap().getUsedBlocks() == 0 && diskScheduler.getRequestCount() == 0);
        check("no se cuenta como reactivación", kernel.getReactivationCount() == 0);

        System.out.println("--- 13. Con suspensiones en curso no se reactiva a nadie ---");
        montar(100, 100);
        PCB m1 = admitido("M1", 7, 40);
        PCB m2 = admitido("M2", 6, 40);
        bloquear(m1);
        bloquear(m2);
        PCB gran = nuevo("Gran", 1, 90);
        kernel.admitNewProcesses(cycle);
        check("hace falta suspender a los dos (20 + 40 + 40 = 100 para 90)", m1.isSuspending() && m2.isSuspending()
                && diskScheduler.getRequestCount() == 2);
        ciclos(1);   // el disco toma la solicitud de M1 (la primera)
        ciclos(60);  // ... y la termina; la de M2 está en curso o ya terminada según el disco
        check("M1 ya está suspendido", m1.getState() == ProcessState.BLOCKED_SUSPENDED);
        kernel.unblockProcess(m1, cycle);
        check("M1 despierta: Listo suspendido", m1.getState() == ProcessState.READY_SUSPENDED);
        check("la suspensión de M2 sigue en curso (40 MB por liberar)",
                kernel.getMediumTermScheduler().getPendingSuspendMemory() == 40 && ram.getFreeMemory() == 60);
        kernel.admitNewProcesses(cycle);
        check("con la suspensión de M2 en curso, M1 NO se reactiva aunque haya 60 MB libres",
                !m1.isResuming() && m1.getState() == ProcessState.READY_SUSPENDED
                        && diskScheduler.getRequestCount() == 0);
        ciclos(80);
        check("al terminar M2 entra Gran con la RAM de los dos (90 usados)",
                gran.getState() == ProcessState.READY && ram.getUsedMemory() == 90 && kernel.getSuspensionCount() == 2);
        check("M1 sigue esperando (40 MB no caben junto a Gran: quedan 10) y nadie se reactivó",
                m1.getState() == ProcessState.READY_SUSPENDED && !m1.isResuming() && kernel.getReactivationCount() == 0);
        kernel.terminateProcess(gran, TerminationCause.COMPLETED, cycle);
        check("cuando termina Gran, M1 vuelve (prioridad 7) y M2 sigue suspendido por su espera",
                m1.isResuming() && m2.getState() == ProcessState.BLOCKED_SUSPENDED);

        System.out.println("--- 14. Sin disco no hay planificador a mediano plazo ---");
        Kernel sinDisco = new Kernel(1, new MemoryManager(100));
        check("el kernel sin disco no tiene planificador ni swap",
                sinDisco.getMediumTermScheduler() == null && sinDisco.getSwap() == null
                        && sinDisco.getSuspensionCount() == 0 && sinDisco.getReactivationCount() == 0);

        System.out.println();
        System.out.println(total + " comprobaciones, " + fallos + " fallos");
        if (fallos > 0) {
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------

    /** Computador de laboratorio: RAM, disco rápido (cabezal de 100 bloques por ciclo) y reloj a mano. */
    private static void montar(int ramMb, int swapBlocks) {
        ram = new MemoryManager(ramMb);
        kernel = new Kernel(1, ram);
        disk = new Disk(200, swapBlocks);
        disk.setHeadSpeed(100);
        diskScheduler = new DiskScheduler(DiskPolicyFactory.create(DiskPolicyType.FIFO));
        disk.setKernel(kernel);
        disk.setScheduler(diskScheduler);
        kernel.setStorage(disk, null);
        log = new StringBuilder();
        kernel.addEventListener((c, m) -> log.append(m).append('\n'));
        cycle = 1;
    }

    private static PCB nuevo(String name, int priority, int memory) {
        PCB p = new PCB(name, 1, ProcessType.CPU_BOUND, priority, memory, 100000, 20);
        kernel.submitProcess(p, cycle);
        return p;
    }

    /** Un proceso ya en Listos. */
    private static PCB admitido(String name, int priority, int memory) {
        PCB p = nuevo(name, priority, memory);
        kernel.admitNewProcesses(cycle);
        return p;
    }

    /** Pasa un proceso Listo a Bloqueado (espera un evento sin tiempo fijo). */
    private static void bloquear(PCB p) {
        kernel.getReadyQueue().remove(p);
        kernel.blockProcess(p, BlockReason.DISK_IO, 0, cycle);
    }

    /** Un proceso que ya está en Listo suspendido, con su memoria en el swap. */
    private static PCB suspendidoListo(String name, int priority, int memory) {
        PCB p = admitido(name, priority, memory);
        aListoSuspendido(p);
        return p;
    }

    /** Lleva un proceso Listo a Listo suspendido: se bloquea, se suspende con su swap reservado y despierta. */
    private static void aListoSuspendido(PCB p) {
        bloquear(p);
        kernel.getSwap().reserve(p);
        kernel.suspendProcess(p, cycle);
        kernel.unblockProcess(p, cycle);
    }

    /** Avanza el reloj: primero el computador (kernel) y después el disco, como el reloj global. */
    private static void ciclos(int n) {
        for (int i = 0; i < n; i++) {
            cycle++;
            kernel.tickTimedBlocks(cycle);
            kernel.tickDeadlines(cycle);
            kernel.admitNewProcesses(cycle);
            disk.onTick(cycle);
        }
    }

    private static void check(String name, boolean ok) {
        total++;
        if (!ok) {
            fallos++;
        }
        System.out.println((ok ? "  OK   " : "  FALLA ") + name);
    }
}
