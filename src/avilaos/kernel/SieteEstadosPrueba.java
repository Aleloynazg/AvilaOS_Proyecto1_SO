package avilaos.kernel;

import avilaos.hardware.disk.Disk;
import avilaos.kernel.memory.MemoryManager;
import avilaos.kernel.process.BlockReason;
import avilaos.kernel.process.PCB;
import avilaos.kernel.process.ProcessState;
import avilaos.kernel.process.ProcessType;
import avilaos.kernel.process.TerminationCause;

/**
 * Prueba de los siete estados (F4.1): los dos estados suspendidos, cómo se suspende y se
 * reactiva un proceso y qué pasa con la RAM, el swap, los bloqueos con tiempo y los deadlines
 * mientras el proceso está suspendido. Ejecutar con Run File; termina con código 1 si algo falla.
 */
public class SieteEstadosPrueba {

    private static int total;
    private static int fallos;

    private static MemoryManager ram;
    private static Kernel kernel;
    private static Disk disk;

    public static void main(String[] args) {
        System.out.println("--- 1. Estados y memoria ---");
        nuevoKernel(1000);
        PCB nuevo = proceso("Nuevo", 1, 100, 50);
        check("un proceso NEW no ocupa RAM", !nuevo.holdsMemory());
        kernel.submitProcess(nuevo, 0);
        check("sigue sin ocupar RAM en la cola de nuevos", !nuevo.holdsMemory() && ram.getUsedMemory() == 0);
        kernel.admitNewProcesses(1);
        check("READY ocupa RAM", nuevo.holdsMemory() && ram.getUsedMemory() == 100);
        nuevo.setState(ProcessState.RUNNING);
        check("RUNNING ocupa RAM", nuevo.holdsMemory());
        nuevo.setState(ProcessState.READY_SUSPENDED);
        check("READY_SUSPENDED en reposo no ocupa RAM", !nuevo.holdsMemory());
        nuevo.setSuspending(true);
        check("READY_SUSPENDED en tránsito de salida sí ocupa RAM", nuevo.holdsMemory());
        nuevo.setSuspending(false);
        nuevo.setResuming(true);
        check("READY_SUSPENDED en tránsito de regreso ya tiene RAM reservada", nuevo.holdsMemory());
        nuevo.setResuming(false);
        nuevo.setState(ProcessState.BLOCKED_SUSPENDED);
        check("BLOCKED_SUSPENDED no ocupa RAM", !nuevo.holdsMemory() && nuevo.isSuspended());
        nuevo.setState(ProcessState.TERMINATED);
        check("TERMINATED no ocupa RAM", !nuevo.holdsMemory() && !nuevo.isSuspended());

        System.out.println("--- 2. Suspender un Bloqueado ---");
        nuevoKernel(1000);
        PCB a = admitido("A", 3, 300, 100, 1);
        kernel.getReadyQueue().remove(a);
        kernel.blockProcess(a, BlockReason.DISK_IO, 0, 5);
        check("blockProcess anota el ciclo del bloqueo", a.getBlockedSinceCycle() == 5);
        check("RAM usada antes de suspender: 300", ram.getUsedMemory() == 300);
        check("suspendProcess devuelve true", kernel.suspendProcess(a, 6));
        check("queda BLOCKED_SUSPENDED", a.getState() == ProcessState.BLOCKED_SUSPENDED);
        check("está en la cola de bloqueados suspendidos y no en la de bloqueados",
                kernel.getBlockedSuspendedQueue().size() == 1 && kernel.getBlockedQueue().isEmpty());
        check("su RAM quedó libre", ram.getUsedMemory() == 0 && !a.holdsMemory());
        check("conserva el motivo del bloqueo", a.getBlockReason() == BlockReason.DISK_IO);
        check("no se puede suspender dos veces", !kernel.suspendProcess(a, 7) && ram.getUsedMemory() == 0);

        System.out.println("--- 3. Suspender un Listo ---");
        PCB b = admitido("B", 3, 200, 100, 8);
        check("suspendProcess sobre un Listo", kernel.suspendProcess(b, 9));
        check("queda READY_SUSPENDED y en su cola",
                b.getState() == ProcessState.READY_SUSPENDED && kernel.getReadySuspendedQueue().size() == 1
                        && kernel.getReadyQueue().isEmpty());
        check("RAM liberada", ram.getUsedMemory() == 0);

        System.out.println("--- 4. Lo que no se puede suspender ---");
        nuevoKernel(1000);
        PCB n = proceso("N", 1, 100, 50);
        kernel.submitProcess(n, 0);
        check("un Nuevo no se suspende", !kernel.suspendProcess(n, 1));
        PCB run = admitido("Run", 1, 100, 50, 1);
        kernel.getReadyQueue().remove(run);
        run.setState(ProcessState.RUNNING);
        check("uno en Ejecución no se suspende", !kernel.suspendProcess(run, 2) && ram.getUsedMemory() == 200);   // N también se admitió
        kernel.terminateProcess(run, TerminationCause.COMPLETED, 3);
        check("uno Terminado no se suspende", !kernel.suspendProcess(run, 4));
        check("RAM sin cambios tras los intentos fallidos (solo N ocupa)", ram.getUsedMemory() == 100);

        System.out.println("--- 5. Suspender deja entrar a un Nuevo ---");
        nuevoKernel(500);
        PCB viejo = admitido("Viejo", 3, 400, 100, 1);
        PCB grande = proceso("Grande", 1, 300, 100);
        kernel.submitProcess(grande, 2);
        check("no cabe mientras Viejo ocupa la RAM", kernel.admitNewProcesses(2) == 0);
        kernel.getReadyQueue().remove(viejo);
        kernel.blockProcess(viejo, BlockReason.IO, 50, 3);
        kernel.suspendProcess(viejo, 4);
        check("tras suspender, Grande sí cabe", kernel.admitNewProcesses(4) == 1
                && grande.getState() == ProcessState.READY && ram.getUsedMemory() == 300);

        System.out.println("--- 6. La espera con tiempo sigue corriendo estando suspendido ---");
        nuevoKernel(1000);
        PCB io = admitido("IO", 3, 100, 100, 1);
        kernel.getReadyQueue().remove(io);
        kernel.blockProcess(io, BlockReason.IO, 2, 2);
        kernel.suspendProcess(io, 3);
        kernel.tickTimedBlocks(4);
        check("un ciclo después sigue Bloqueado suspendido", io.getState() == ProcessState.BLOCKED_SUSPENDED);
        kernel.tickTimedBlocks(5);
        check("al terminar la E/S pasa a Listo suspendido (no a Listo)",
                io.getState() == ProcessState.READY_SUSPENDED && kernel.getReadySuspendedQueue().size() == 1
                        && kernel.getBlockedSuspendedQueue().isEmpty() && kernel.getReadyQueue().isEmpty());
        check("sigue sin ocupar RAM", ram.getUsedMemory() == 0);

        System.out.println("--- 7. Despertar por el disco estando suspendido ---");
        nuevoKernel(1000);
        PCB dk = admitido("Disco", 3, 100, 100, 1);
        kernel.getReadyQueue().remove(dk);
        kernel.blockProcess(dk, BlockReason.DISK_IO, 0, 2);
        kernel.suspendProcess(dk, 3);
        kernel.unblockProcess(dk, 4);
        check("unblockProcess lo deja Listo suspendido",
                dk.getState() == ProcessState.READY_SUSPENDED && dk.getBlockReason() == BlockReason.NONE
                        && kernel.getReadySuspendedQueue().size() == 1);

        System.out.println("--- 8. Deadline estando suspendido ---");
        nuevoKernel(1000);
        PCB dl = admitido("Deadline", 3, 100, 2, 1);
        PCB otro = admitido("Otro", 3, 50, 100, 1);
        kernel.getReadyQueue().remove(dl);
        kernel.blockProcess(dl, BlockReason.DISK_IO, 0, 2);
        kernel.suspendProcess(dl, 3);
        check("RAM sin Deadline: solo Otro (50)", ram.getUsedMemory() == 50);
        kernel.tickDeadlines(4);
        check("a un ciclo del deadline sigue suspendido", dl.getState() == ProcessState.BLOCKED_SUSPENDED);
        kernel.tickDeadlines(5);
        check("al vencer se termina con DEADLINE_MISSED",
                dl.getState() == ProcessState.TERMINATED
                        && dl.getTerminationCause() == TerminationCause.DEADLINE_MISSED);
        check("sale de la cola de suspendidos", kernel.getBlockedSuspendedQueue().isEmpty());
        check("la RAM no se libera dos veces (sigue 50 de Otro)", ram.getUsedMemory() == 50 && otro.holdsMemory());

        System.out.println("--- 9. Reactivar un Listo suspendido ---");
        nuevoKernel(500);
        PCB r = admitido("R", 3, 300, 100, 1);
        kernel.suspendProcess(r, 2);
        check("no se reactiva un Listo (solo Listo suspendido)", !kernel.resumeProcess(proceso("X", 1, 1, 1), 3, false));
        PCB hueco = admitido("Hueco", 3, 300, 100, 3);
        check("sin RAM libre (200) no se puede reactivar los 300 de R", !kernel.resumeProcess(r, 4, false));
        check("R sigue Listo suspendido y en su cola",
                r.getState() == ProcessState.READY_SUSPENDED && kernel.getReadySuspendedQueue().size() == 1);
        check("el intento fallido no ocupó RAM", ram.getUsedMemory() == 300);
        kernel.terminateProcess(hueco, TerminationCause.COMPLETED, 5);
        check("con RAM libre sí se reactiva", kernel.resumeProcess(r, 6, false));
        check("R vuelve a Listos con su RAM",
                r.getState() == ProcessState.READY && kernel.getReadyQueue().size() == 1
                        && ram.getUsedMemory() == 300 && r.holdsMemory());
        check("la cola de Listos suspendidos queda vacía", kernel.getReadySuspendedQueue().isEmpty());

        System.out.println("--- 10. Reactivar con la RAM ya reservada ---");
        nuevoKernel(500);
        PCB rr = admitido("RR", 3, 300, 100, 1);
        kernel.suspendProcess(rr, 2);
        check("se reserva la RAM al generar la solicitud", ram.allocate(rr.getMemoryRequired()));
        rr.setResuming(true);
        check("mientras viaja de regreso ya cuenta como con RAM", rr.holdsMemory() && ram.getUsedMemory() == 300);
        check("al completar no se reserva otra vez", kernel.resumeProcess(rr, 3, true));
        check("RAM usada: 300 (no 600)", ram.getUsedMemory() == 300);
        check("el proceso ya no está en tránsito", !rr.isResuming() && rr.getState() == ProcessState.READY);

        System.out.println("--- 11. Un Bloqueado suspendido no se reactiva directamente ---");
        nuevoKernel(1000);
        PCB bs = admitido("BS", 3, 100, 100, 1);
        kernel.getReadyQueue().remove(bs);
        kernel.blockProcess(bs, BlockReason.DISK_IO, 0, 2);
        kernel.suspendProcess(bs, 3);
        check("resumeProcess sobre Bloqueado suspendido devuelve false",
                !kernel.resumeProcess(bs, 4, false) && bs.getState() == ProcessState.BLOCKED_SUSPENDED);

        System.out.println("--- 12. Terminar a un suspendido ---");
        nuevoKernel(1000);
        PCB t = admitido("T", 3, 200, 100, 1);
        PCB u = admitido("U", 3, 100, 100, 1);
        kernel.getReadyQueue().remove(t);
        kernel.blockProcess(t, BlockReason.DISK_IO, 0, 2);
        int swapStart = disk.allocateSwapBlocks(t, 20);
        t.setSwapStartBlock(swapStart);
        kernel.suspendProcess(t, 3);
        check("el swap tiene 20 bloques ocupados por T", disk.countFreeSwapBlocks() == 80 && swapStart >= 200);
        kernel.terminateProcess(t, TerminationCause.COMPLETED, 4);
        check("terminar a un suspendido no libera RAM (solo U ocupa)", ram.getUsedMemory() == 100 && u.holdsMemory());
        check("sus bloques de swap quedaron libres", disk.countFreeSwapBlocks() == 100 && t.getSwapStartBlock() == -1);
        check("sale de las colas de suspendidos", kernel.getBlockedSuspendedQueue().isEmpty());
        check("queda en Terminados", kernel.getTerminatedQueue().size() == 1);

        System.out.println("--- 13. Suspensión en tránsito ---");
        nuevoKernel(1000);
        PCB v = admitido("V", 3, 250, 100, 1);
        kernel.getReadyQueue().remove(v);
        kernel.blockProcess(v, BlockReason.DISK_IO, 0, 2);
        v.setSuspending(true);   // su SUSPEND está en la cola del disco
        check("en tránsito sigue ocupando RAM", v.holdsMemory() && ram.getUsedMemory() == 250);
        kernel.unblockProcess(v, 3);
        check("si se desbloquea en tránsito queda Listo suspendido",
                v.getState() == ProcessState.READY_SUSPENDED && kernel.getReadySuspendedQueue().size() == 1
                        && kernel.getReadyQueue().isEmpty() && kernel.getBlockedQueue().isEmpty());
        check("y todavía ocupa la RAM hasta que el disco escriba el swap", v.holdsMemory() && ram.getUsedMemory() == 250);
        check("no se puede reactivar mientras sale", !kernel.resumeProcess(v, 4, false));
        check("el disco termina: suspendProcess libera la RAM", kernel.suspendProcess(v, 5));
        check("RAM libre y fuera de tránsito", ram.getUsedMemory() == 0 && !v.isSuspending() && !v.holdsMemory());
        check("sigue Listo suspendido una sola vez en su cola",
                v.getState() == ProcessState.READY_SUSPENDED && kernel.getReadySuspendedQueue().size() == 1);

        System.out.println("--- 14. Terminar en tránsito libera la RAM una sola vez ---");
        nuevoKernel(1000);
        PCB w = admitido("W", 3, 250, 100, 1);
        PCB z = admitido("Z", 3, 100, 100, 1);
        kernel.getReadyQueue().remove(w);
        kernel.blockProcess(w, BlockReason.DISK_IO, 0, 2);
        w.setSuspending(true);
        kernel.terminateProcess(w, TerminationCause.DEADLINE_MISSED, 3);
        check("la RAM de W se liberó (queda Z con 100)", ram.getUsedMemory() == 100 && z.holdsMemory());
        check("W ya no está en transito", !w.isSuspending());

        System.out.println();
        System.out.println(total + " comprobaciones, " + fallos + " fallos");
        if (fallos > 0) {
            System.exit(1);
        }
    }

    /** Kernel nuevo con RAM de {@code ramMb}, un disco de 200 + 100 bloques y sin mensajes de log. */
    private static void nuevoKernel(int ramMb) {
        ram = new MemoryManager(ramMb);
        kernel = new Kernel(1, ram);
        disk = new Disk(200, 100);
        kernel.setStorage(disk, null);
    }

    private static PCB proceso(String name, int priority, int memory, int deadline) {
        return new PCB(name, 1, ProcessType.CPU_BOUND, priority, memory, deadline, 20);
    }

    /** Un proceso que ya está en Listos (con su RAM reservada). */
    private static PCB admitido(String name, int priority, int memory, int deadline, long cycle) {
        PCB p = proceso(name, priority, memory, deadline);
        kernel.submitProcess(p, cycle);
        kernel.admitNewProcesses(cycle);
        return p;
    }

    private static void check(String name, boolean ok) {
        total++;
        if (!ok) {
            fallos++;
        }
        System.out.println((ok ? "  OK   " : "  FALLA ") + name);
    }
}
