/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.hardware.disk;

import avilaos.kernel.disk.DiskScheduler;
import avilaos.kernel.disk.DiskRequest;
import avilaos.kernel.disk.DiskPolicyFactory;
import avilaos.kernel.disk.DiskPolicyType;
import avilaos.kernel.process.PCB;
import avilaos.kernel.process.ProcessType;
import avilaos.kernel.process.IoOperation;
import avilaos.kernel.fs.Permission;

/**
 * Prueba la integración del disco con su planificador
 * y las diferentes políticas de planificación.
 *
 * @author alexandraloynaz
 */
public class DiskIntegracionPrueba {

    private static PCB process;

    public static void main(String[] args) {

        process = new PCB(
                "ProcesoPrueba", 1, ProcessType.FILE_IO,
                1, 10, 100, 5
        );

        System.out.println("==================================");
        System.out.println(" PRUEBAS DE INTEGRACION DEL DISCO");
        System.out.println("==================================");

        probarFIFO();
        probarSSTF();
        probarSCAN();
        probarCSCAN();
        probarLOOK();

        System.out.println("\n==================================");
        System.out.println(" FIN DE LAS PRUEBAS");
        System.out.println("==================================");
    }

    /**
     * Crea un disco y conecta la política indicada.
     */
    private static Disk crearDisco(DiskPolicyType tipo) {

        Disk disk = new Disk();

        DiskScheduler scheduler = new DiskScheduler(
                DiskPolicyFactory.create(tipo)
        );

        disk.setScheduler(scheduler);

        return disk;
    }

    /**
     * Crea una solicitud de lectura para las pruebas.
     */
    private static DiskRequest crearSolicitud(int targetBlock,
                                               long arrivalCycle) {

        return new DiskRequest(
                process,
                IoOperation.READ,
                targetBlock,
                1,
                arrivalCycle,
                "/archivo",
                null,
                "usuario",
                Permission.PUBLIC
        );
    }

    /**
     * Prueba la política FIFO.
     */
    private static void probarFIFO() {

        System.out.println("\n--- PRUEBA FIFO ---");

        Disk disk = crearDisco(DiskPolicyType.FIFO);
        DiskScheduler scheduler = diskScheduler(disk);

        scheduler.addRequest(crearSolicitud(200, 1));
        scheduler.addRequest(crearSolicitud(50, 2));

            avanzarDisco(disk, 400);

     System.out.println("Posición final del cabezal: "
             + disk.getCurrentHead());

     mostrarMetricas(disk);}

    /**
     * Prueba la política SSTF.
     */
    private static void probarSSTF() {

        System.out.println("\n--- PRUEBA SSTF ---");

        Disk disk = crearDisco(DiskPolicyType.SSTF);
        disk.setCurrentHead(100);

        DiskScheduler scheduler = diskScheduler(disk);

        scheduler.addRequest(crearSolicitud(50, 1));
        scheduler.addRequest(crearSolicitud(130, 2));
        scheduler.addRequest(crearSolicitud(200, 3));

        avanzarDisco(disk, 400);

        System.out.println("Posición final del cabezal: "
                + disk.getCurrentHead());

        mostrarMetricas(disk);
    }

    /**
     * Prueba la política SCAN.
     */
    private static void probarSCAN() {

        System.out.println("\n--- PRUEBA SCAN ---");

        Disk disk = crearDisco(DiskPolicyType.SCAN);
        disk.setCurrentHead(100);

        DiskScheduler scheduler = diskScheduler(disk);

        scheduler.addRequest(crearSolicitud(50, 1));
        scheduler.addRequest(crearSolicitud(200, 2));

        avanzarDisco(disk, 500);

        System.out.println("Posición final del cabezal: "
                + disk.getCurrentHead());

        System.out.println("Dirección actual: "
                + disk.getHeadDirection());

        mostrarMetricas(disk);}

    /**
     * Prueba la política C-SCAN.
     */
    private static void probarCSCAN() {

        System.out.println("\n--- PRUEBA C-SCAN ---");

        Disk disk = crearDisco(DiskPolicyType.CSCAN);
        disk.setCurrentHead(100);

        DiskScheduler scheduler = diskScheduler(disk);
        scheduler.addRequest(crearSolicitud(50, 1));
        scheduler.addRequest(crearSolicitud(200, 2));

        avanzarDisco(disk, 400);

        System.out.println("Posición final del cabezal: "
                + disk.getCurrentHead());

        System.out.println("Dirección actual: "
                + disk.getHeadDirection());

        mostrarMetricas(disk);
    }

    /**
     * Prueba la política LOOK.
     */
    private static void probarLOOK() {

        System.out.println("\n--- PRUEBA LOOK ---");

        Disk disk = crearDisco(DiskPolicyType.LOOK);
        disk.setCurrentHead(100);

        DiskScheduler scheduler = diskScheduler(disk);
        scheduler.addRequest(crearSolicitud(50, 1));
        scheduler.addRequest(crearSolicitud(200, 2));

        avanzarDisco(disk, 400);

        System.out.println("Posición final del cabezal: "
                + disk.getCurrentHead());

        System.out.println("Dirección actual: "
                + disk.getHeadDirection());

        mostrarMetricas(disk);
    }

    /**
     * Obtiene el planificador conectado al disco.
     */
    private static DiskScheduler diskScheduler(Disk disk) {
        return disk.getScheduler();
    }
    private static void avanzarDisco(Disk disk, int ciclos) {

    for (int ciclo = 1; ciclo <= ciclos; ciclo++) {
        disk.onTick(ciclo);
    }
    
}
    private static void mostrarMetricas(Disk disk) {

    System.out.println("Solicitudes completadas: "
            + disk.getCompletedRequests());

    System.out.println("Desplazamiento total: "
            + disk.getTotalHeadMovement());

    System.out.println("Tiempo promedio de espera: "
            + disk.getAverageWaitingTime());
    System.out.println("Solicitudes FIFO: " + disk.getFifoCompleted());
System.out.println("Solicitudes SSTF: " + disk.getSstfCompleted());
System.out.println("Solicitudes SCAN: " + disk.getScanCompleted());
System.out.println("Solicitudes C-SCAN: " + disk.getCscanCompleted());
System.out.println("Solicitudes LOOK: " + disk.getLookCompleted());
}
}