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

        DiskRequest first = disk.processNextRequest();
        DiskRequest second = disk.processNextRequest();

        System.out.println("Primera solicitud: "
                + first.getTargetBlock());

        System.out.println("Segunda solicitud: "
                + second.getTargetBlock());

        System.out.println("Posición final del cabezal: "
                + disk.getCurrentHead());
    }

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

        DiskRequest request = disk.processNextRequest();

        System.out.println("Bloque seleccionado: "
                + request.getTargetBlock());

        System.out.println("Posición del cabezal: "
                + disk.getCurrentHead());
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

        DiskRequest first = disk.processNextRequest();

        System.out.println("Primera solicitud: "
                + first.getTargetBlock());

        DiskRequest second = disk.processNextRequest();

        System.out.println("Solicitud durante el cambio de dirección: "
                + second);

        System.out.println("Posición del cabezal después del recorrido: "
                + disk.getCurrentHead());

        System.out.println("Dirección actual: "
                + disk.getHeadDirection());

        DiskRequest third = disk.processNextRequest();

        System.out.println("Siguiente solicitud: "
                + (third == null ? "Ninguna"
                        : third.getTargetBlock()));
    }

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

        DiskRequest first = disk.processNextRequest();

        System.out.println("Primera solicitud: "
                + first.getTargetBlock());

        disk.processNextRequest();

        System.out.println("Posición después del retorno circular: "
                + disk.getCurrentHead());

        System.out.println("Dirección actual: "
                + disk.getHeadDirection());

        DiskRequest second = disk.processNextRequest();

        System.out.println("Siguiente solicitud: "
                + (second == null ? "Ninguna"
                        : second.getTargetBlock()));
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

        DiskRequest first = disk.processNextRequest();

        System.out.println("Primera solicitud: "
                + first.getTargetBlock());

        disk.processNextRequest();

        System.out.println("Posición después del cambio de dirección: "
                + disk.getCurrentHead());

        System.out.println("Dirección actual: "
                + disk.getHeadDirection());

        DiskRequest second = disk.processNextRequest();

        System.out.println("Siguiente solicitud: "
                + (second == null ? "Ninguna"
                        : second.getTargetBlock()));
    }

    /**
     * Obtiene el planificador conectado al disco.
     */
    private static DiskScheduler diskScheduler(Disk disk) {
        return disk.getScheduler();
    }
}