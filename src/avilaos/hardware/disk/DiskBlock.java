/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.hardware.disk;
import avilaos.kernel.process.PCB;
/**
 *
 * Representa el bloque fisico del proceso 
 * @author alexandraloynaz
 */
public class DiskBlock {
    private final int blockNumber;
    private final boolean swapBlock;
    private PCB owner;

    public DiskBlock(int blockNumber) {
        this.blockNumber = blockNumber;
        this.swapBlock = blockNumber >= 200;
        this.owner = null;
    }

    public int getBlockNumber() {
        return blockNumber;
    }

    public boolean isSwapBlock() {
        return swapBlock;
    }

    public boolean isFree() {
        return owner == null;
    }

    public PCB getOwner() {
        return owner;
    }

    public void setOwner(PCB owner) {
        this.owner = owner;
    }
}
