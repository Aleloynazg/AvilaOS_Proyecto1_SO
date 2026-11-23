package avilaos.kernel.swap;

import avilaos.hardware.disk.Disk;
import avilaos.kernel.process.PCB;
import avilaos.kernel.process.ProcessType;

/**
 * Prueba del área de swap (F4.2): reservar y liberar bloques consecutivos para la memoria de
 * los procesos suspendidos. Ejecutar con Run File; termina con código 1 si algo falla.
 */
public class SwapAreaPrueba {

    private static int total;
    private static int fallos;

    public static void main(String[] args) {
        Disk disk = new Disk(200, 100);
        SwapArea swap = new SwapArea(disk);

        System.out.println("--- 1. Tamaño del área ---");
        check("100 bloques de swap, todos libres", swap.getTotalBlocks() == 100 && swap.getFreeBlocks() == 100);
        check("ninguno usado", swap.getUsedBlocks() == 0);
        check("el espacio libre consecutivo es todo el swap", swap.getLargestFreeRun() == 100);

        System.out.println("--- 2. Reservar ---");
        PCB a = proceso("A", 30);
        PCB b = proceso("B", 20);
        PCB c = proceso("C", 40);
        check("1 bloque por MB", SwapArea.blocksFor(a) == 30);
        int sa = swap.reserve(a);
        check("A empieza en el primer bloque del swap (200)", sa == 200 && a.getSwapStartBlock() == 200);
        int sb = swap.reserve(b);
        check("B va a continuación (230)", sb == 230 && b.getSwapStartBlock() == 230);
        int sc = swap.reserve(c);
        check("C va a continuación (250)", sc == 250);
        check("quedan 10 libres", swap.getFreeBlocks() == 10 && swap.getUsedBlocks() == 90);
        check("blocksOf cuenta lo de cada proceso", swap.blocksOf(a) == 30 && swap.blocksOf(b) == 20 && swap.blocksOf(c) == 40);
        check("ownerOf devuelve al dueño del bloque", swap.ownerOf(200) == a && swap.ownerOf(249) == b && swap.ownerOf(299) == null);
        check("ownerOf de un bloque de archivos es null", swap.ownerOf(10) == null);
        check("un proceso no puede reservar dos veces", swap.reserve(a) == -1 && swap.blocksOf(a) == 30);

        System.out.println("--- 3. Sin espacio ---");
        PCB d = proceso("D", 11);
        check("11 bloques no caben (quedan 10)", !swap.canReserve(11) && swap.reserve(d) == -1);
        check("el fallo no deja nada reservado ni cambia el PCB", swap.blocksOf(d) == 0 && d.getSwapStartBlock() == -1);
        PCB e = proceso("E", 10);
        check("10 bloques sí caben", swap.canReserve(10) && swap.reserve(e) == 290);
        check("swap lleno", swap.getFreeBlocks() == 0 && swap.getLargestFreeRun() == 0);

        System.out.println("--- 4. Liberar ---");
        check("release devuelve cuántos bloques liberó", swap.release(b) == 20);
        check("quedan 20 libres y B sin reserva", swap.getFreeBlocks() == 20 && b.getSwapStartBlock() == -1);
        check("liberar a quien no tiene devuelve 0", swap.release(b) == 0 && swap.release(null) == 0);
        check("A y C siguen intactos", swap.blocksOf(a) == 30 && swap.blocksOf(c) == 40);

        System.out.println("--- 5. Fragmentación: espacio libre pero no consecutivo ---");
        // Libres: el hueco de B (230-249, 20 bloques). Si se libera E (290-299) hay 30 libres en dos tiradas.
        swap.release(e);
        check("30 libres en total, la mayor tirada es 20", swap.getFreeBlocks() == 30 && swap.getLargestFreeRun() == 20);
        PCB f = proceso("F", 25);
        check("25 bloques no caben aunque haya 30 libres (no son consecutivos)", !swap.canReserve(25) && swap.reserve(f) == -1);
        PCB g = proceso("G", 20);
        check("20 caben en el hueco de B", swap.reserve(g) == 230);
        PCB h = proceso("H", 10);
        check("10 caben al final (el primer hueco que alcance)", swap.reserve(h) == 290);

        System.out.println("--- 6. Casos límite ---");
        check("canReserve(0) es false", !swap.canReserve(0));
        Disk sinSwap = new Disk(200, 0);
        SwapArea vacia = new SwapArea(sinSwap);
        check("swap de 0 bloques: nada cabe", vacia.getTotalBlocks() == 0 && vacia.getLargestFreeRun() == 0
                && vacia.reserve(proceso("Z", 1)) == -1);
        Disk pequeno = new Disk(200, 5);
        check("un proceso mayor que todo el swap no cabe", new SwapArea(pequeno).reserve(proceso("Y", 6)) == -1);
        boolean rechaza = false;
        try {
            new SwapArea(null);
        } catch (IllegalArgumentException ex) {
            rechaza = true;
        }
        check("el disco es obligatorio", rechaza);

        System.out.println();
        System.out.println(total + " comprobaciones, " + fallos + " fallos");
        if (fallos > 0) {
            System.exit(1);
        }
    }

    private static PCB proceso(String name, int memory) {
        return new PCB(name, 1, ProcessType.CPU_BOUND, 5, memory, 500, 10);
    }

    private static void check(String name, boolean ok) {
        total++;
        if (!ok) {
            fallos++;
        }
        System.out.println((ok ? "  OK   " : "  FALLA ") + name);
    }
}
