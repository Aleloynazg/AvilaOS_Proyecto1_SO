package avilaos.hardware.disk;

import avilaos.kernel.fs.FileEntry;
import avilaos.kernel.fs.Permission;

/**
 * Prueba de la asignación encadenada (F1.1): los bloques de un archivo
 * no tienen que estar juntos; cada bloque apunta al siguiente.
 */
public class DiskEncadenadoPrueba {

    public static void main(String[] args) {
        Disk disk = new Disk(20, 5);
        FileEntry a = new FileEntry("a.txt", Permission.PUBLIC, null);
        FileEntry b = new FileEntry("b.txt", Permission.PUBLIC, null);
        FileEntry c = new FileEntry("c.txt", Permission.PUBLIC, null);
        FileEntry d = new FileEntry("d.txt", Permission.PUBLIC, null);

        System.out.println("--- 1. Asignar en un disco vacío ---");
        int primeroA = disk.allocateFileChain(a, 3);
        int primeroB = disk.allocateFileChain(b, 2);
        System.out.println("a.txt: " + cadena(disk, primeroA) + " (esperado: 0 -> 1 -> 2)");
        System.out.println("b.txt: " + cadena(disk, primeroB) + " (esperado: 3 -> 4)");

        System.out.println("--- 2. Asignar en una posición fija (como el JSON) ---");
        System.out.println("c.txt en 10, 3 bloques: " + disk.allocateFileAt(c, 10, 3)
                + " -> " + cadena(disk, 10) + " (esperado: true -> 10 -> 11 -> 12)");
        System.out.println("Encima de c.txt (11, 2 bloques): " + disk.allocateFileAt(d, 11, 2)
                + " (esperado: false)");
        System.out.println("Fuera de la zona de archivos (19, 3 bloques): " + disk.allocateFileAt(d, 19, 3)
                + " (esperado: false)");

        System.out.println("--- 3. Borrar y reutilizar huecos ---");
        System.out.println("Liberados de a.txt: " + disk.freeFileChain(primeroA) + " (esperado: 3)");
        int primeroD = disk.allocateFileChain(d, 5);
        System.out.println("d.txt (5 bloques): " + cadena(disk, primeroD)
                + " (esperado: 0 -> 1 -> 2 -> 5 -> 6)");
        System.out.println("Dueño del bloque 5: " + disk.getBlock(5).getFile().getName()
                + " | siguiente del 2: " + disk.getBlock(2).getNext()
                + " (esperado: d.txt | 5)");

        System.out.println("--- 4. Sin espacio suficiente ---");
        int libresAntes = disk.countFreeFileBlocks();
        System.out.println("Pedir 50 bloques: " + disk.allocateFileChain(a, 50) + " (esperado: -1)");
        System.out.println("Libres antes y después: " + libresAntes + " y " + disk.countFreeFileBlocks()
                + " (esperado: iguales, 10 y 10)");

        System.out.println("--- 5. Los archivos no usan el swap ---");
        Disk lleno = new Disk(3, 5);
        lleno.allocateFileChain(a, 3);
        System.out.println("Disco de 3 bloques lleno, pedir 1 más: " + lleno.allocateFileChain(b, 1)
                + " | swap libre: " + lleno.countFreeSwapBlocks() + " (esperado: -1 | 5)");
    }

    private static String cadena(Disk disk, int primero) {
        int[] bloques = disk.getChain(primero);
        String texto = "";
        for (int i = 0; i < bloques.length; i++) {
            texto += (i == 0 ? "" : " -> ") + bloques[i];
        }
        return texto.isEmpty() ? "(vacía)" : texto;
    }
}
