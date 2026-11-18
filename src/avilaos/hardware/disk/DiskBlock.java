/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.hardware.disk;
import avilaos.kernel.fs.FileEntry;
import avilaos.kernel.process.PCB;
/**
 *
 * Representa el bloque fisico del proceso 
 * @author alexandraloynaz
 */
public class DiskBlock {
    private final int blockNumber;
    private final boolean swapBlock;
    private PCB owner;          // proceso suspendido dueño del bloque (zona de swap)
    private FileEntry file;     // archivo dueño del bloque (zona de archivos)
    private int next = -1;      // asignación encadenada: siguiente bloque del archivo, -1 = último

    /**
     * @param blockNumber número del bloque
     * @param swapBlock   true si pertenece al área de intercambio (lo decide el Disk)
     */
    public DiskBlock(int blockNumber, boolean swapBlock) {
        this.blockNumber = blockNumber;
        this.swapBlock = swapBlock;
        this.owner = null;
    }

    public int getBlockNumber() {
        return blockNumber;
    }

    public boolean isSwapBlock() {
        return swapBlock;
    }

    /** Libre = no es de ningún proceso ni de ningún archivo. */
    public boolean isFree() {
        return owner == null && file == null;
    }

    public FileEntry getFile() {
        return file;
    }

    public void setFile(FileEntry file) {
        this.file = file;
    }

    /** Número del siguiente bloque del archivo, o -1 si este es el último. */
    public int getNext() {
        return next;
    }

    public void setNext(int next) {
        this.next = next;
    }

    public PCB getOwner() {
        return owner;
    }

    public void setOwner(PCB owner) {
        this.owner = owner;
    }
}
