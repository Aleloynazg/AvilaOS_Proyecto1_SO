package avilaos.kernel.sync;

import avilaos.hardware.Cluster;
import avilaos.kernel.Kernel;
import avilaos.kernel.process.IdGenerator;
import avilaos.kernel.process.Instruction;
import avilaos.kernel.process.InstructionModel;
import avilaos.kernel.process.PCB;
import avilaos.kernel.process.ProcessFactory;
import avilaos.kernel.process.TerminationCause;

/** Prueba manual de SyncExecutor (F3.4 y F3.7). Ejecutar con Run File. */
public class SyncExecutorPrueba {

    private static SyncExecutor executor;
    private static long ciclo = 0;

    public static void main(String[] args) {
        IdGenerator.reset();
        Cluster cluster = new Cluster(new int[] {1000, 1000}, 3);
        cluster.getComputer(1).addEventListener((c, m) -> System.out.println("  [" + c + "] " + m));
        cluster.getComputer(2).addEventListener((c, m) -> System.out.println("  [" + c + "] " + m));
        executor = new SyncExecutor(cluster);

        Buffer b1 = cluster.createBuffer(1, 1);   // capacidad 1 en H1: se llena enseguida
        PCB prod = ProcessFactory.producer("Prod", 1, 1, 50, 500, b1.getId(), 1, 2);  // en H1
        PCB cons = ProcessFactory.consumer("Cons", 2, 1, 50, 500, b1.getId(), 1, 2);  // en H2 (remoto)
        admitir(cluster.getComputer(1).getKernel(), prod);
        admitir(cluster.getComputer(2).getKernel(), cons);

        System.out.println("--- 1. El consumidor llega primero: buffer vacío, se bloquea en n ---");
        boolean bloqueado = paso(cons);
        System.out.println("Bloqueado: " + bloqueado + " | " + cons.getState() + " " + cons.getBlockReason()
                + "   (esperado: true | BLOCKED SEM_FULL)");

        System.out.println("--- 2. El productor produce y despierta al consumidor de H2 ---");
        for (int i = 0; i < 6; i++) {
            paso(prod);
        }
        System.out.println(b1 + " | Cons: " + cons.getState()
                + "   (esperado: B1 (H1) [1/1] s=1 n=0 e=0 | READY)");
        System.out.println("¿Cons en Listos de H2? " + cluster.getComputer(2).getKernel().getReadyQueue().remove(cons)
                + "   (esperado: true)");

        System.out.println("--- 3. Segundo elemento: el buffer está lleno, el productor se bloquea en e ---");
        paso(prod);              // PRODUCE
        bloqueado = paso(prod);  // SEM_WAIT(e)
        System.out.println("Bloqueado: " + bloqueado + " | " + prod.getState() + " " + prod.getBlockReason()
                + "   (esperado: true | BLOCKED SEM_EMPTY)");

        System.out.println("--- 4. El consumidor sigue (sin repetir su semWait) y despierta al productor ---");
        for (int i = 0; i < 4; i++) {
            paso(cons);
        }
        System.out.println(b1 + " | Prod: " + prod.getState()
                + "   (esperado: B1 (H1) [0/1] s=1 n=0 e=0 | READY)");
        System.out.println("Producidos: " + b1.getProduced() + " | consumidos: " + b1.getConsumed()
                + "   (esperado: 1 | 1)");

        System.out.println("--- 5. F3.7: un consumidor espera en n y se le vence el deadline ---");
        PCB cons2 = ProcessFactory.consumer("Cons2", 2, 1, 50, 500, b1.getId(), 1, 1);
        Kernel k2 = cluster.getComputer(2).getKernel();
        admitir(k2, cons2);
        paso(cons2);             // el buffer está vacío (n = 0): Cons2 espera en n
        System.out.println("Antes: " + b1.getFull() + " | esperando: " + b1.getFull().getWaitingProcesses().length
                + "   (esperado: B1.n = -1 | esperando: 1)");
        k2.terminateProcess(cons2, TerminationCause.DEADLINE_MISSED, ++ciclo);
        System.out.println("Después: " + b1.getFull() + " | esperando: " + b1.getFull().getWaitingProcesses().length
                + "   (esperado: B1.n = 0 | esperando: 0)");
    }

    /** Pone el proceso en el Kernel y lo saca de Listos, como si la CPU lo tomara. */
    private static void admitir(Kernel kernel, PCB p) {
        kernel.submitProcess(p, ciclo);
        kernel.admitNewProcesses(ciclo);
        kernel.getReadyQueue().remove(p);
    }

    /** Hace lo que hará el Computer en F3.5: lee la instrucción, avanza el PC y la ejecuta. */
    private static boolean paso(PCB p) {
        ciclo++;
        Instruction ins = InstructionModel.next(p);
        p.advance();
        if (!ins.isKernelMode()) {
            System.out.println("  [" + ciclo + "] " + p.getTag() + " ejecuta " + ins + " (modo usuario)");
            return false;
        }
        return executor.execute(p, ins, ciclo);
    }
}