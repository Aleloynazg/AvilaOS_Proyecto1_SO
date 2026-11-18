package avilaos.hardware.disk;

import avilaos.kernel.disk.CScanPolicy;
import avilaos.kernel.disk.DiskRequest;
import avilaos.kernel.disk.DiskScheduler;
import avilaos.kernel.disk.DiskSchedulingPolicy;
import avilaos.kernel.disk.ScanPolicy;
import avilaos.kernel.process.IoOperation;
import avilaos.kernel.process.PCB;
import avilaos.kernel.process.ProcessFactory;

/**
 * Prueba del disco de tamaño configurable (F1.1) y de que SCAN y C-SCAN
 * llegan al último bloque REAL del disco (F1.3). Usa el ejemplo del anexo:
 * disco de 200 bloques, cabezal en 50 hacia bloques mayores,
 * solicitudes 82, 17, 43, 140, 24, 16, 190.
 */
public class DiskTamanoPrueba {

    private static final int[] ANEXO = { 82, 17, 43, 140, 24, 16, 190 };

    public static void main(String[] args) {
        System.out.println("--- 1. Tamaños ---");
        Disk porDefecto = new Disk();
        System.out.println("Por defecto: total " + porDefecto.getTotalBlocks()
                + ", archivos " + porDefecto.getFileBlockCount()
                + ", swap " + porDefecto.getSwapBlockCount()
                + ", último bloque " + porDefecto.getLastBlock()
                + " (esperado: total 300, archivos 200, swap 100, último bloque 299)");

        Disk chico = new Disk(50, 10);
        System.out.println("Disk(50, 10): total " + chico.getTotalBlocks()
                + ", libres archivos " + chico.countFreeFileBlocks()
                + ", libres swap " + chico.countFreeSwapBlocks()
                + ", swap empieza en " + chico.getSwapStart()
                + " (esperado: total 60, libres archivos 50, libres swap 10, swap empieza en 50)");
        System.out.println("Bloque 49 es swap: " + chico.getBlock(49).isSwapBlock()
                + " | bloque 50 es swap: " + chico.getBlock(50).isSwapBlock()
                + " (esperado: false | true)");

        Disk sinSwap = new Disk(200, 0);
        System.out.println("Disk(200, 0): último bloque " + sinSwap.getLastBlock()
                + ", libres swap " + sinSwap.countFreeSwapBlocks()
                + " (esperado: último bloque 199, libres swap 0)");

        try {
            new Disk(0, 10);
        } catch (IllegalArgumentException e) {
            System.out.println("Error esperado: " + e.getMessage());
        }

        System.out.println("--- 2. SCAN con el anexo (disco de 200) ---");
        correr(new ScanPolicy(), "82 140 190 43 24 17 16", 332);

        System.out.println("--- 3. C-SCAN con el anexo (disco de 200) ---");
        correr(new CScanPolicy(), "82 140 190 16 17 24 43", 192);
    }

    private static void correr(DiskSchedulingPolicy politica, String ordenEsperado, long desplazamientoEsperado) {
        Disk disk = new Disk(200, 0);
        DiskScheduler scheduler = new DiskScheduler(politica);
        disk.setScheduler(scheduler);
        disk.setCurrentHead(50);
        disk.setHeadDirection(HeadDirection.UP);

        for (int i = 0; i < ANEXO.length; i++) {
            PCB p = ProcessFactory.cpuBound("Proc_" + ANEXO[i], 1, 1, 1, 9999, 1);
            scheduler.addRequest(new DiskRequest(p, IoOperation.READ, ANEXO[i], 1, 0,
                    null, null, null, null));
        }

        String orden = "";
        int atendidas = 0;
        for (long ciclo = 1; ciclo < 5000 && disk.getCompletedRequests() < ANEXO.length; ciclo++) {
            disk.onTick(ciclo);
            if (disk.getCompletedRequests() > atendidas) {
                atendidas = disk.getCompletedRequests();
                orden += (orden.isEmpty() ? "" : " ") + disk.getCurrentHead();
            }
        }
        System.out.println("Orden: " + orden + " (esperado: " + ordenEsperado + ")");
        System.out.println("Desplazamiento: " + disk.getTotalHeadMovement()
                + " (esperado: " + desplazamientoEsperado + ")");
    }
}
