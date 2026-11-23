package avilaos.hardware.disk;

import avilaos.kernel.disk.DiskPolicyFactory;
import avilaos.kernel.disk.DiskPolicyType;
import avilaos.kernel.disk.DiskRequest;
import avilaos.kernel.disk.DiskScheduler;
import avilaos.kernel.process.IoOperation;
import avilaos.kernel.process.PCB;
import avilaos.kernel.process.ProcessFactory;

/**
 * F1.4b – Prueba de la velocidad del cabezal (headSpeed).
 *
 * Con más posiciones por ciclo el disco tarda menos ciclos, pero el
 * desplazamiento total (posiciones recorridas) y el orden de atención
 * no cambian: el cabezal nunca se pasa del bloque destino ni de los extremos.
 */
public class DiskVelocidadPrueba {

    private static final int[] ANEXO = {82, 17, 43, 140, 24, 16, 190};
    private static int fallos = 0;
    private static int comprobaciones = 0;

    public static void main(String[] args) {
        System.out.println("--- 1. Valor por defecto y validación ---");
        Disk disk = new Disk(200, 0);
        verificar("Velocidad por defecto = 1", disk.getHeadSpeed() == 1);
        disk.setHeadSpeed(7);
        verificar("setHeadSpeed(7)", disk.getHeadSpeed() == 7);
        verificar("setHeadSpeed(0) se rechaza", lanza(disk, 0));
        verificar("setHeadSpeed(101) se rechaza", lanza(disk, Disk.MAX_HEAD_SPEED + 1));
        verificar("La velocidad no cambió tras los rechazos", disk.getHeadSpeed() == 7);

        System.out.println("--- 2. Un desplazamiento de 10 posiciones ---");
        // 1 ciclo para elegir + ceil(10/velocidad) para moverse + 1 de transferencia
        long[] esperado = {12, 7, 5, 4, 3};
        int[] velocidades = {1, 2, 4, 5, 10};
        for (int i = 0; i < velocidades.length; i++) {
            long ciclos = cicloFinalDeUnaSolicitud(velocidades[i]);
            verificar("Velocidad " + velocidades[i] + ": termina en el ciclo " + ciclos
                    + " (esperado " + esperado[i] + ")", ciclos == esperado[i]);
        }

        System.out.println("--- 3. El anexo no cambia con otra velocidad (disco de 200) ---");
        long[] despEsperado = {518, 208, 332, 192};
        DiskPolicyType[] tipos = {DiskPolicyType.FIFO, DiskPolicyType.SSTF,
            DiskPolicyType.SCAN, DiskPolicyType.CSCAN};
        for (int velocidad : new int[]{1, 3, 7, 50}) {
            for (int i = 0; i < tipos.length; i++) {
                long desplazamiento = anexo(tipos[i], velocidad);
                verificar(tipos[i] + " con velocidad " + velocidad + ": desplazamiento "
                        + desplazamiento + " (esperado " + despEsperado[i] + ")",
                        desplazamiento == despEsperado[i]);
            }
        }

        System.out.println();
        System.out.println("Comprobaciones: " + comprobaciones + " | Fallos: " + fallos);
        if (fallos > 0) {
            System.exit(1);
        }
    }

    private static boolean lanza(Disk disk, int velocidad) {
        try {
            disk.setHeadSpeed(velocidad);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    private static long cicloFinalDeUnaSolicitud(int velocidad) {
        Disk disk = new Disk(200, 0);
        disk.setHeadSpeed(velocidad);
        DiskScheduler scheduler = new DiskScheduler(DiskPolicyFactory.create(DiskPolicyType.FIFO));
        disk.setScheduler(scheduler);
        disk.setCurrentHead(10);
        PCB p = ProcessFactory.cpuBound("P", 1, 1, 1, 99999, 1);
        scheduler.addRequest(new DiskRequest(p, IoOperation.READ, 20, 1, 0, null, null, null, null));
        long ciclo = 0;
        while (disk.getCompletedRequests() < 1 && ciclo < 1000) {
            ciclo++;
            disk.onTick(ciclo);
        }
        return ciclo;
    }

    private static long anexo(DiskPolicyType tipo, int velocidad) {
        Disk disk = new Disk(200, 0);
        disk.setHeadSpeed(velocidad);
        DiskScheduler scheduler = new DiskScheduler(DiskPolicyFactory.create(tipo));
        disk.setScheduler(scheduler);
        disk.setCurrentHead(50);
        disk.setHeadDirection(HeadDirection.UP);
        for (int bloque : ANEXO) {
            PCB p = ProcessFactory.cpuBound("Proc_" + bloque, 1, 1, 1, 99999, 1);
            scheduler.addRequest(new DiskRequest(p, IoOperation.READ, bloque, 1, 0, null, null, null, null));
        }
        for (long ciclo = 1; ciclo < 20000 && disk.getCompletedRequests() < ANEXO.length; ciclo++) {
            disk.onTick(ciclo);
        }
        return disk.getTotalHeadMovement();
    }

    private static void verificar(String descripcion, boolean ok) {
        comprobaciones++;
        if (!ok) {
            fallos++;
        }
        System.out.println((ok ? "OK    " : "FALLO ") + descripcion);
    }
}
