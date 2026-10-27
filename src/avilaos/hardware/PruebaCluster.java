package avilaos.hardware;

import avilaos.kernel.process.IdGenerator;
import avilaos.kernel.process.PCB;
import avilaos.kernel.process.ProcessFactory;
import avilaos.kernel.sync.Buffer;

/** Prueba manual de Cluster. Ejecutar con Run File. */
public class PruebaCluster {

    public static void main(String[] args) {
        IdGenerator.reset();

        System.out.println("--- 1. Crear el clúster ---");
        Cluster cluster = new Cluster(new int[] {1024, 512}, 3);
        System.out.println("Computadores: " + cluster.getComputerCount() + " (esperado: 2)");
        System.out.println(cluster.getComputer(1) + " RAM " + cluster.getComputer(1).getMemoryManager().getTotalMemory()
                + " | " + cluster.getComputer(2) + " RAM " + cluster.getComputer(2).getMemoryManager().getTotalMemory()
                + "   (esperado: Computador 1 RAM 1024 | Computador 2 RAM 512)");
        System.out.println("H3: " + cluster.getComputer(3) + " | H0: " + cluster.getComputer(0)
                + "   (esperado: null | null)");
        System.out.println("Latencia: " + cluster.getNetworkLatency() + " (esperado: 3)");

        System.out.println("--- 2. Crear buffers en distintos anfitriones ---");
        Buffer b1 = cluster.createBuffer(5, 1);
        Buffer b2 = cluster.createBuffer(10, 2);
        System.out.println(b1 + " | " + b2
                + "   (esperado: B1 (H1) [0/5] s=1 n=0 e=5 | B2 (H2) [0/10] s=1 n=0 e=10)");
        System.out.println("RAM usada H1: " + cluster.getComputer(1).getMemoryManager().getUsedMemory()
                + " | H2: " + cluster.getComputer(2).getMemoryManager().getUsedMemory()
                + "   (esperado: 5 | 10)");
        System.out.println("Buffers registrados: " + cluster.getBufferCount() + " (esperado: 2)");

        System.out.println("--- 3. Buscar buffers por id ---");
        System.out.println("getBuffer(2): " + cluster.getBuffer(2).getTag()
                + " | getBuffer(9): " + cluster.getBuffer(9) + "   (esperado: B2 | null)");

        System.out.println("--- 4. Local o remoto y el Kernel de cada proceso ---");
        PCB enH1 = ProcessFactory.producer("Prod", 1, 1, 50, 500, b1.getId(), 2, 3);
        PCB enH2 = ProcessFactory.consumer("Cons", 2, 1, 50, 500, b1.getId(), 2, 3);
        System.out.println(enH1.getTag() + " -> B1 remoto? " + cluster.isRemoteAccess(enH1, b1)
                + " | " + enH2.getTag() + " -> B1 remoto? " + cluster.isRemoteAccess(enH2, b1)
                + "   (esperado: false | true)");
        System.out.println("Kernel de " + enH2.getTag() + ": computador "
                + cluster.getKernelOf(enH2).getComputerId() + " (esperado: 2)");

        System.out.println("--- 5. Cambiar la latencia ---");
        cluster.setNetworkLatency(7);
        System.out.println("Latencia: " + cluster.getNetworkLatency() + " (esperado: 7)");

        System.out.println("--- 6. Errores ---");
        System.out.println("Buffer sin RAM: " + cluster.createBuffer(600, 2) + " (esperado: null)");
        System.out.println("Buffers registrados: " + cluster.getBufferCount() + " (esperado: 2)");
        try {
            cluster.createBuffer(5, 9);
        } catch (IllegalArgumentException e) {
            System.out.println("Error esperado: " + e.getMessage());
        }
        try {
            new Cluster(new int[] {1024}, 3);
        } catch (IllegalArgumentException e) {
            System.out.println("Error esperado: " + e.getMessage());
        }
        try {
            cluster.setNetworkLatency(-1);
        } catch (IllegalArgumentException e) {
            System.out.println("Error esperado: " + e.getMessage());
        }
    }
}