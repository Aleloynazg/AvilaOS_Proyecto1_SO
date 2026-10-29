package avilaos;

import avilaos.gui.MainWindow;
import avilaos.hardware.Computer;
import avilaos.hardware.GlobalClock;
import avilaos.kernel.process.PCB;
import avilaos.kernel.process.ProcessType;
import javax.swing.SwingUtilities;
import avilaos.kernel.process.ProcessFactory;
import avilaos.kernel.scheduler.PriorityPolicy;
import avilaos.hardware.Cluster;
import avilaos.kernel.sync.Buffer;
import avilaos.kernel.sync.SyncExecutor;

/** Punto de entrada: arma el sistema y abre la ventana. */
public class AvilaOS {

    public static void main(String[] args) {
        // 1. El sistema (lógica): 2 computadores y el reloj
        Cluster cluster = new Cluster(
            new int[] {1000, 1000},
            2
        );

        Computer[] computers = cluster.getComputers();

        Computer c1 = computers[0];
        Computer c2 = computers[1];

        c1.setPolicy(new PriorityPolicy());

        GlobalClock clock = new GlobalClock(500);
        cluster.connectClock(clock);

        Buffer buffer = cluster.createBuffer(2, 1);

        if (buffer == null) {
            throw new IllegalStateException(
                "No se pudo reservar memoria para el buffer"
            );
        }

        SyncExecutor syncExecutor = new SyncExecutor(cluster);

        for (int i = 0; i < computers.length; i++) {
            computers[i].setSyncExecutor(syncExecutor);
        }

        // 2. La ventana (observa al sistema)
        SwingUtilities.invokeLater(() -> {
            MainWindow window = new MainWindow(clock, computers);
            window.setVisible(true);

            // 3. Procesos de prueba (en la Fase 2 se crearán desde un formulario)
            //               nombre, computador, tipo, prioridad, memoria, deadline, instrucciones
/*
             c1.getKernel().submitProcess(
               
            ProcessFactory.ioBound("Editor", 1, 1, 200, 60, 8, 3, 4),
                0
            );
            

            c1.getKernel().submitProcess(
                new PCB("Navegador", 1, ProcessType.CPU_BOUND, 3, 500, 60, 6),
                0
            );
            c1.getKernel().submitProcess(new PCB("Juego", 1, ProcessType.CPU_BOUND, 2, 300, 60, 5), 0);
            c2.getKernel().submitProcess(new PCB("Compilador", 2, ProcessType.CPU_BOUND, 1, 700, 40, 10), 0);
            c2.getKernel().submitProcess(new PCB("Música", 2, ProcessType.CPU_BOUND, 2, 200, 4, 10), 0);
         */  
//Prueba para el quantum de round robin
c1.getKernel().submitProcess(
    new PCB("Proceso A", 1, ProcessType.CPU_BOUND, 1, 200, 100, 20),
    0
);

c1.getKernel().submitProcess(
    new PCB("Proceso B", 1, ProcessType.CPU_BOUND, 2, 200, 100, 20),
    0
);

c1.getKernel().submitProcess(
    new PCB("Proceso C", 1, ProcessType.CPU_BOUND, 3, 200, 100, 20),
    0
);
PCB productorPrueba = ProcessFactory.producer(
                "ProductorPrueba",
                1,      // Computador
                1,      // Prioridad
                100,    // Memoria
                100,    // Deadline
                1,      // ID del buffer
                2,      // Ciclos por elemento
                3       // Elementos por producir
            );

        c1.getKernel().submitProcess(productorPrueba, 0);

            // 5. Esperar a que el buffer se llene antes de iniciar el consumidor
            new Thread(() -> {
                try {
                    while (buffer.getProduced() < 2) {
                        Thread.sleep(100);
                    }

                    // Dar tiempo para que el productor intente agregar el tercer elemento
                    Thread.sleep(1500);

                    System.out.println("=== Iniciando consumidor ===");

                    PCB consumidorPrueba = ProcessFactory.consumer(
                        "ConsumidorPrueba",
                        2,      // Computador
                        1,      // Prioridad
                        100,    // Memoria
                        100,    // Deadline
                        1,      // ID del buffer
                        2,      // Ciclos por elemento
                        3       // Elementos por consumir
                    );

                    c2.getKernel().submitProcess(consumidorPrueba, 0);

                    // Esperar a que termine el intercambio de los tres elementos
                    while (buffer.getProduced() < 3
                            || buffer.getConsumed() < 3
                            || buffer.getCount() != 0
                            || buffer.getMutex().getValue() != 1
                            || buffer.getFull().getValue() != 0
                            || buffer.getEmpty().getValue() != 2) {

                        Thread.sleep(500);
                    }

                    System.out.println("=== Estado final del buffer ===");
                    System.out.println("Buffer: " + buffer.getTag());
                    System.out.println("Producidos: " + buffer.getProduced());
                    System.out.println("Consumidos: " + buffer.getConsumed());
                    System.out.println("Elementos restantes: " + buffer.getCount());
                    System.out.println(buffer);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        });
    }
}