package avilaos.kernel.swap;

import avilaos.hardware.disk.Disk;
import avilaos.hardware.disk.DiskBlock;
import avilaos.kernel.process.PCB;

/**
 * Área de intercambio (swap): la parte del disco donde viven las memorias de los procesos
 * suspendidos. Es la zona que va después de los bloques de archivos (por defecto, los
 * bloques 200 a 299).
 *
 * Un proceso suspendido guarda su memoria en bloques CONSECUTIVOS del swap: 1 bloque por
 * cada MB de memoria (decisión del equipo, ver DECISIONES.md). Los bloques se reservan
 * cuando se genera la solicitud SUSPEND y se liberan cuando el proceso vuelve a la RAM
 * o termina.
 */
public class SwapArea {

    private final Disk disk;

    public SwapArea(Disk disk) {
        if (disk == null) {
            throw new IllegalArgumentException("Falta el disco");
        }
        this.disk = disk;
    }

    /** Cuántos bloques ocupa en el swap la memoria de un proceso (1 por cada MB). */
    public static int blocksFor(PCB process) {
        return process.getMemoryRequired();
    }

    /** Total de bloques del área de swap. */
    public int getTotalBlocks() {
        return disk.getSwapBlockCount();
    }

    /** Bloques libres del swap (no tienen que estar juntos). */
    public int getFreeBlocks() {
        return disk.countFreeSwapBlocks();
    }

    public int getUsedBlocks() {
        return getTotalBlocks() - getFreeBlocks();
    }

    /** Longitud de la tirada de bloques libres consecutivos más larga: el proceso más grande que cabría ahora. */
    public int getLargestFreeRun() {
        int best = 0;
        int run = 0;
        int first = disk.getSwapStart();
        for (int i = first; i < first + getTotalBlocks(); i++) {
            if (disk.getBlock(i).isFree()) {
                run++;
                if (run > best) {
                    best = run;
                }
            } else {
                run = 0;
            }
        }
        return best;
    }

    /** true si hay {@code blocks} bloques libres consecutivos en el swap. */
    public boolean canReserve(int blocks) {
        return blocks > 0 && getLargestFreeRun() >= blocks;
    }

    /**
     * Reserva en el swap los bloques de la memoria del proceso y anota en su PCB dónde quedaron.
     * Un proceso solo puede tener una reserva a la vez.
     *
     * @return el primer bloque reservado, o -1 si no hay espacio consecutivo suficiente (o el
     *         proceso ya tiene una reserva)
     */
    public int reserve(PCB process) {
        if (process == null || process.getSwapStartBlock() >= 0) {
            return -1;
        }
        int first = disk.allocateSwapBlocks(process, blocksFor(process));
        if (first >= 0) {
            process.setSwapStartBlock(first);
        }
        return first;
    }

    /**
     * Libera los bloques que el proceso tenía en el swap.
     *
     * @return cuántos bloques se liberaron (0 si no tenía)
     */
    public int release(PCB process) {
        if (process == null) {
            return 0;
        }
        int released = blocksOf(process);
        if (released > 0) {
            disk.freeBlocks(process);
        }
        process.setSwapStartBlock(-1);
        return released;
    }

    /** Cuántos bloques del swap tiene reservados el proceso. */
    public int blocksOf(PCB process) {
        int count = 0;
        int first = disk.getSwapStart();
        for (int i = first; i < first + getTotalBlocks(); i++) {
            if (disk.getBlock(i).getOwner() == process) {
                count++;
            }
        }
        return count;
    }

    /** El proceso dueño del bloque (número de bloque del DISCO, no del swap), o null si está libre o no es de swap. */
    public PCB ownerOf(int block) {
        DiskBlock b = disk.getBlock(block);
        return b != null && b.isSwapBlock() ? b.getOwner() : null;
    }

    public Disk getDisk() {
        return disk;
    }
}
