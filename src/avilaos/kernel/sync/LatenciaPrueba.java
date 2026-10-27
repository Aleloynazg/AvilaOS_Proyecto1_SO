package avilaos.kernel.sync;

import avilaos.hardware.Cluster;
import avilaos.kernel.Kernel;
import avilaos.kernel.process.IdGenerator;
import avilaos.kernel.process.Instruction;
import avilaos.kernel.process.InstructionModel;
import avilaos.kernel.process.PCB;
import avilaos.kernel.process.ProcessFactory;

/** Prueba manual del acceso remoto con latencia (F3.6). Ejecutar con Run File. */
public class LatenciaPrueba {

    private static SyncExecutor executor;
    private static long ciclo = 0;

    public static void main(String[] args) {
        IdGenerator.reset();
        Cluster cluster = new Cluster(new int[] {1000, 1000}, 2);   // latencia de 2 ciclos
        cluster.getComputer(1).addEventListener((c, m) -> System.out.println("  [" + c + "] " + m));
        cluster.getComputer(2).addEventListener((c, m) -> System.out.println("  [" + c + "] " + m));
        executor = new SyncExecutor(cluster);
        Kernel k1 = cluster.getComputer(1).getKernel();
        Kernel k2 = cluster.getComputer(2).getKernel();

        Buffer b1 = cluster.createBuffer(5, 1);   // el buffer vive en H1
        PCB local = ProcessFactory.producer("Local", 1, 1, 50, 500, b1.getId(), 1, 1);    // en H1
        PCB remoto = ProcessFactory.producer("Remoto", 2, 1, 50, 500, b1.getId(), 1, 1);  // en H2
        admitir(k1, local);
        admitir(k2, remoto);

        System.out.println("--- 1. Acceso local: nunca espera a la red ---");
        paso(local);                       // PRODUCE
        boolean bloqueado = paso(local);   // SEM_WAIT(e)
        System.out.println("Bloqueado: " + bloqueado + " | " + local.getState()
                + "   (esperado: false | RUNNING o READY, no BLOCKED)");

        System.out.println("--- 2. Acceso remoto: hace la operación y espera 2 ciclos de red ---");
        paso(remoto);                      // PRODUCE
        bloqueado = paso(remoto);          // SEM_WAIT(e) sobre B1, que está en H1
        System.out.println("Bloqueado: " + bloqueado + " | " + remoto.getState() + " " + remoto.getBlockReason()
                + " | " + b1.getEmpty() + "   (esperado: true | BLOCKED NETWORK | B1.e = 3)");

        System.out.println("--- 3. Pasan los 2 ciclos de red ---");
        ciclo++;
        k2.tickTimedBlocks(ciclo);
        System.out.println("Tras 1 ciclo: " + remoto.getState() + "   (esperado: BLOCKED)");
        ciclo++;
        k2.tickTimedBlocks(ciclo);
        System.out.println("Tras 2 ciclos: " + remoto.getState() + "   (esperado: READY)");
        k2.getReadyQueue().remove(remoto);   // la CPU lo vuelve a tomar

        System.out.println("--- 4. Sigue con la instrucción siguiente (no repite el semWait) ---");
        Instruction siguiente = InstructionModel.next(remoto);
        System.out.println("Siguiente: " + siguiente + "   (esperado: SEM_WAIT(s))");

        System.out.println("--- 5. Su última instrucción remota no espera a la red ---");
        int esperas = 0;
        while (!remoto.isFinished()) {
            if (paso(remoto)) {
                esperas++;
                ciclo += 2;
                k2.tickTimedBlocks(ciclo - 1);
                k2.tickTimedBlocks(ciclo);
                k2.getReadyQueue().remove(remoto);
            }
        }
        System.out.println("Esperas de red: " + esperas + " | " + remoto.getState() + " | " + b1
                + "   (esperado: 3 | READY o RUNNING, no BLOCKED | B1 (H1) [1/5] s=1 n=1 e=3)");

        System.out.println("--- 6. Con latencia 0 no espera ---");
        cluster.setNetworkLatency(0);
        PCB remoto2 = ProcessFactory.consumer("Remoto2", 2, 1, 50, 500, b1.getId(), 1, 1);
        admitir(k2, remoto2);
        bloqueado = paso(remoto2);         // SEM_WAIT(n): hay 1 elemento, pasa
        System.out.println("Bloqueado: " + bloqueado + "   (esperado: false)");
    }

    private static void admitir(Kernel kernel, PCB p) {
        kernel.submitProcess(p, ciclo);
        kernel.admitNewProcesses(ciclo);
        kernel.getReadyQueue().remove(p);
    }

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