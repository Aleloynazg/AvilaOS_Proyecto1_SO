/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos;

/**
 *
 * @author alexandraloynaz
 */
import avilaos.hardware.Cluster;
import avilaos.hardware.Computer;
import avilaos.hardware.GlobalClock;
import avilaos.kernel.process.PCB;
import avilaos.kernel.process.ProcessFactory;
import avilaos.kernel.scheduler.FCFSPolicy;
import avilaos.kernel.sync.Buffer;
import avilaos.kernel.sync.SyncExecutor;
import avilaos.kernel.process.ProcessState;
public class PruebaLocalRemote {
     public static void main(String[] args) {
        Cluster cluster = new Cluster(
            new int[] {1000, 1000},
            2
        );

        Computer h1 = cluster.getComputer(1);
        Computer h2 = cluster.getComputer(2);

        h1.setPolicy(new FCFSPolicy());
        h2.setPolicy(new FCFSPolicy());

        GlobalClock reloj = new GlobalClock(500);
        cluster.connectClock(reloj);

        Buffer bufferLocal = cluster.createBuffer(5, 1);
        Buffer bufferRemoto = cluster.createBuffer(5, 2);

if (bufferLocal == null || bufferRemoto == null) {
    throw new IllegalStateException(
        "No se pudieron crear los buffers"
    );
}



        SyncExecutor ejecutor = new SyncExecutor(cluster);

        h1.setSyncExecutor(ejecutor);
        h2.setSyncExecutor(ejecutor);

        h1.addEventListener((ciclo, mensaje) ->
            System.out.println(ciclo + " | " + mensaje)
        );

        h2.addEventListener((ciclo, mensaje) ->
            System.out.println(ciclo + " | " + mensaje)
        );

      
        PCB productorLocal = ProcessFactory.producer(
    "ProductorLocal", 1, 1, 100, 200, 1, 1, 2
);

PCB productorRemoto = ProcessFactory.producer(
    "ProductorRemoto", 1, 1, 100, 200, 2, 1, 2
);

h1.getKernel().submitProcess(productorLocal, 0);
h1.getKernel().submitProcess(productorRemoto, 0);


        reloj.startClock();

new Thread(() -> {
    try {
        while (productorLocal.getState() != ProcessState.TERMINATED
                || productorRemoto.getState() != ProcessState.TERMINATED) {
            Thread.sleep(100);
        }

        System.out.println("=== RESULTADO F3.11 ===");
        System.out.println("Buffer local: " + bufferLocal);
        System.out.println("Buffer remoto: " + bufferRemoto);
        System.out.println(
            "Productor local terminado: "
            + (productorLocal.getState() == ProcessState.TERMINATED)
        );
        System.out.println(
            "Productor remoto terminado: "
            + (productorRemoto.getState() == ProcessState.TERMINATED)
        );

        reloj.stopClock();

    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    }
}).start();
    }
}

