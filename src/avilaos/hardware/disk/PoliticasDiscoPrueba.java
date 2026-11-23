package avilaos.hardware.disk;

import avilaos.kernel.disk.DiskPolicyFactory;
import avilaos.kernel.disk.DiskPolicyType;
import avilaos.kernel.disk.DiskRequest;
import avilaos.kernel.disk.DiskScheduler;
import avilaos.kernel.process.IoOperation;
import avilaos.kernel.process.PCB;
import avilaos.kernel.process.ProcessFactory;

/**
 * F1.5 – Prueba de las políticas de planificación de disco.
 *
 * Usa el anexo del enunciado (518, 208, 332 y 192 bloques) y los casos
 * P1 a P4, siempre con todas las solicitudes en la cola desde el inicio.
 * Además muestra qué pasa con el disco real de 300 bloques (archivos
 * 0–199 y swap 200–299): SCAN y C-SCAN atienden en el mismo orden pero
 * barren hasta el bloque 299, así que su desplazamiento es mayor.
 *
 * Se ejecuta como programa normal. Termina con código 1 si algo falla.
 */
public class PoliticasDiscoPrueba {

    private static final int CABEZAL_INICIAL = 50;
    private static final int[] ANEXO = {82, 17, 43, 140, 24, 16, 190};
    private static final int[] CASOS = {95, 180, 34, 119, 11, 123, 62, 64};

    private static int fallos = 0;
    private static int comprobaciones = 0;

    public static void main(String[] args) {
        System.out.println("=== ANEXO DEL ENUNCIADO (disco de 200 bloques) ===");
        comprobar(200, DiskPolicyType.FIFO, ANEXO, "82 17 43 140 24 16 190", 518);
        comprobar(200, DiskPolicyType.SSTF, ANEXO, "43 24 17 16 82 140 190", 208);
        comprobar(200, DiskPolicyType.SCAN, ANEXO, "82 140 190 43 24 17 16", 332);
        comprobar(200, DiskPolicyType.CSCAN, ANEXO, "82 140 190 16 17 24 43", 192);
        // LOOK no es obligatoria: 50 -> 190 (140) y de vuelta hasta el 16 (174).
        comprobar(200, DiskPolicyType.LOOK, ANEXO, "82 140 190 43 24 17 16", 314);

        System.out.println();
        System.out.println("=== CASOS P1 A P4 (cabezal 50, hacia bloques mayores) ===");
        // P1: 45 + 85 + 146 + 85 + 108 + 112 + 61 + 2
        comprobar(200, DiskPolicyType.FIFO, CASOS, "95 180 34 119 11 123 62 64", 644);
        // P2: 12 + 2 + 30 + 23 + 84 + 24 + 4 + 57
        comprobar(200, DiskPolicyType.SSTF, CASOS, "62 64 34 11 95 119 123 180", 236);
        // P3: 50 -> 180 (130), hasta el 199 (19), vuelve al 34 (165) y al 11 (23)
        comprobar(200, DiskPolicyType.SCAN, CASOS, "62 64 95 119 123 180 34 11", 337);
        // P4: 50 -> 180 (130), hasta el 199 (19), salta al 0 sin contar, 11 y 34 (34)
        comprobar(200, DiskPolicyType.CSCAN, CASOS, "62 64 95 119 123 180 11 34", 183);
        comprobar(200, DiskPolicyType.LOOK, CASOS, "62 64 95 119 123 180 34 11", 299);

        System.out.println();
        System.out.println("=== DISCO REAL DE 300 BLOQUES (archivos 0-199 + swap 200-299) ===");
        System.out.println("(FIFO, SSTF y LOOK no cambian; SCAN y C-SCAN barren hasta el 299)");
        comprobar(300, DiskPolicyType.FIFO, ANEXO, "82 17 43 140 24 16 190", 518);
        comprobar(300, DiskPolicyType.SSTF, ANEXO, "43 24 17 16 82 140 190", 208);
        comprobar(300, DiskPolicyType.SCAN, ANEXO, "82 140 190 43 24 17 16", 532);
        comprobar(300, DiskPolicyType.CSCAN, ANEXO, "82 140 190 16 17 24 43", 292);
        comprobar(300, DiskPolicyType.LOOK, ANEXO, "82 140 190 43 24 17 16", 314);
        // P3: 50 -> 299 (249) y de vuelta hasta el 11 (288). P4: 249 + 34.
        comprobar(300, DiskPolicyType.SCAN, CASOS, "62 64 95 119 123 180 34 11", 537);
        comprobar(300, DiskPolicyType.CSCAN, CASOS, "62 64 95 119 123 180 11 34", 283);

        System.out.println();
        System.out.println("=== SOLICITUDES DE VARIOS BLOQUES ===");
        probarBloquesMultiples();

        System.out.println();
        System.out.println("Comprobaciones: " + comprobaciones + " | Fallos: " + fallos);
        if (fallos > 0) {
            System.exit(1);
        }
    }

    /**
     * Carga las solicitudes (una de un bloque cada una) y deja correr el disco
     * hasta atenderlas todas. Compara el orden de atención y el desplazamiento.
     */
    private static void comprobar(int bloques, DiskPolicyType tipo, int[] solicitudes,
                                  String ordenEsperado, long desplazamientoEsperado) {
        Disk disk = new Disk(bloques, 0);
        DiskScheduler scheduler = new DiskScheduler(DiskPolicyFactory.create(tipo));
        disk.setScheduler(scheduler);
        disk.setCurrentHead(CABEZAL_INICIAL);
        disk.setHeadDirection(HeadDirection.UP);
        for (int i = 0; i < solicitudes.length; i++) {
            PCB p = ProcessFactory.cpuBound("Proc_" + solicitudes[i], 1, 1, 1, 99999, 1);
            scheduler.addRequest(new DiskRequest(p, IoOperation.READ, solicitudes[i], 1, 0,
                    null, null, null, null));
        }

        StringBuilder orden = new StringBuilder();
        int atendidas = 0;
        for (long ciclo = 1; ciclo < 20000 && atendidas < solicitudes.length; ciclo++) {
            disk.onTick(ciclo);
            if (disk.getCompletedRequests() > atendidas) {
                atendidas = disk.getCompletedRequests();
                if (orden.length() > 0) {
                    orden.append(' ');
                }
                orden.append(disk.getCurrentHead());
            }
        }

        boolean ok = orden.toString().equals(ordenEsperado)
                && disk.getTotalHeadMovement() == desplazamientoEsperado;
        comprobaciones++;
        if (!ok) {
            fallos++;
        }
        System.out.println((ok ? "OK    " : "FALLO ") + String.format("%-5s", tipo) + " disco " + bloques
                + " | orden: " + orden + " | desplazamiento: " + disk.getTotalHeadMovement()
                + (ok ? "" : "   (esperado: " + ordenEsperado + " | " + desplazamientoEsperado + ")"));
    }

    /**
     * Una solicitud de n bloques cuesta el desplazamiento más n ciclos de
     * transferencia (un ciclo por bloque).
     */
    private static void probarBloquesMultiples() {
        Disk disk = new Disk(200, 0);
        DiskScheduler scheduler = new DiskScheduler(DiskPolicyFactory.create(DiskPolicyType.FIFO));
        disk.setScheduler(scheduler);
        disk.setCurrentHead(10);
        PCB p = ProcessFactory.cpuBound("Grande", 1, 1, 1, 99999, 1);
        scheduler.addRequest(new DiskRequest(p, IoOperation.READ, 20, 5, 0, null, null, null, null));

        long cicloFinal = 0;
        for (long ciclo = 1; ciclo < 1000 && disk.getCompletedRequests() < 1; ciclo++) {
            disk.onTick(ciclo);
            cicloFinal = ciclo;
        }
        // 1 ciclo para elegir la solicitud + 10 de desplazamiento + 5 de transferencia
        boolean ok = disk.getTotalHeadMovement() == 10 && disk.getCurrentHead() == 20
                && cicloFinal == 16;
        comprobaciones++;
        if (!ok) {
            fallos++;
        }
        System.out.println((ok ? "OK    " : "FALLO ") + "READ de 5 bloques en el 20 con cabezal en 10: desplazamiento "
                + disk.getTotalHeadMovement() + ", terminó en el ciclo " + cicloFinal
                + (ok ? "" : "   (esperado: 10 y ciclo 16)"));
    }
}
