/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.hardware.disk;
import avilaos.kernel.process.PCB; 
import avilaos.kernel.process.ProcessType;
/**
 *Prueba para disk
 * @author alexandraloynaz
 */
public class DiskPrueba {
    public static void main(String[] args) {

        Disk disk = new Disk();

        PCB process1 = new PCB(
                "Proceso1", 1, ProcessType.FILE_IO, 1, 10, 100, 5
        );

        PCB process2 = new PCB(
                "Proceso2", 1, ProcessType.FILE_IO, 2, 10, 100, 5
        );

        System.out.println("=== PRUEBAS DEL DISCO ===");

        // PRUEBA 1: Estado inicial.
        System.out.println("\n--- PRUEBA 1: Estado inicial ---");

        System.out.println("Total de bloques: "
                + disk.getTotalBlocks());

        System.out.println("Bloques libres para archivos: "
                + disk.countFreeFileBlocks());

        System.out.println("Bloques libres para swap: "
                + disk.countFreeSwapBlocks());

        // PRUEBA 2: Asignación de bloques consecutivos.
        System.out.println("\n--- PRUEBA 2: Asignación de archivos ---");

        int firstBlock = disk.allocateFileBlocks(process1, 3);

        System.out.println("Primer bloque asignado: " + firstBlock);

        for (int i = firstBlock; i < firstBlock + 3; i++) {
            System.out.println("Bloque " + i
                    + " ocupado: " + !disk.getBlock(i).isFree());
        }

        System.out.println("Bloques libres: "
                + disk.countFreeFileBlocks());

        // PRUEBA 3: Asignación a un segundo proceso.
        System.out.println("\n--- PRUEBA 3: Segundo proceso ---");

        int secondBlock = disk.allocateFileBlocks(process2, 2);

        System.out.println("Primer bloque del segundo proceso: "
                + secondBlock);

        System.out.println("Bloques libres: "
                + disk.countFreeFileBlocks());

        // PRUEBA 4: Liberación selectiva.
        System.out.println("\n--- PRUEBA 4: Liberación selectiva ---");

        disk.freeBlocks(process1, firstBlock, 3);

        System.out.println("Bloques 0, 1 y 2 libres: "
                + (disk.getBlock(0).isFree()
                && disk.getBlock(1).isFree()
                && disk.getBlock(2).isFree()));

        System.out.println("Bloque 3 sigue ocupado: "
                + !disk.getBlock(3).isFree());

        System.out.println("Bloques libres: "
                + disk.countFreeFileBlocks());

        // PRUEBA 5: Solicitud mayor que la capacidad.
        System.out.println("\n--- PRUEBA 5: Solicitud excesiva ---");

        int excessiveRequest =
                disk.allocateFileBlocks(process1, 201);

        System.out.println("Resultado de la asignación: "
                + excessiveRequest);

        System.out.println("Bloques libres: "
                + disk.countFreeFileBlocks());

        // PRUEBA 6: Cantidades inválidas.
        System.out.println("\n--- PRUEBA 6: Cantidades inválidas ---");

        System.out.println("Asignación de cero bloques: "
                + disk.allocateFileBlocks(process1, 0));

        System.out.println("Asignación de cantidad negativa: "
                + disk.allocateFileBlocks(process1, -2));

        System.out.println("Bloques libres: "
                + disk.countFreeFileBlocks());

        // PRUEBA 7: Separación entre archivos y swap.
        System.out.println("\n--- PRUEBA 7: Separación de zonas ---");

        int fileBlock = disk.allocateFileBlocks(process1, 2);
        int swapBlock = disk.allocateSwapBlocks(process1, 2);

        System.out.println("Primer bloque de archivos: " + fileBlock);
        System.out.println("Primer bloque de swap: " + swapBlock);

        System.out.println("Bloques libres para archivos: "
                + disk.countFreeFileBlocks());

        System.out.println("Bloques libres para swap: "
                + disk.countFreeSwapBlocks());

        // PRUEBA 8: Solicitud excesiva en swap.
        System.out.println("\n--- PRUEBA 8: Solicitud excesiva en swap ---");

        int excessiveSwap =
                disk.allocateSwapBlocks(process2, 101);

        System.out.println("Resultado de la asignación: "
                + excessiveSwap);

        System.out.println("Bloques libres en swap: "
                + disk.countFreeSwapBlocks());

        // PRUEBA 9: Liberación final.
        System.out.println("\n--- PRUEBA 9: Liberación final ---");

        disk.freeBlocks(process1);
        disk.freeBlocks(process2);

        System.out.println("Bloques libres para archivos: "
                + disk.countFreeFileBlocks());

        System.out.println("Bloques libres para swap: "
                + disk.countFreeSwapBlocks());

        System.out.println("\n=== FIN DE LAS PRUEBAS ===");
    }
}
