/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.kernel.disk;
import avilaos.kernel.process.IoOperation;
import avilaos.kernel.process.PCB;
import avilaos.kernel.fs.FsNode;
import avilaos.kernel.fs.Permission;

/**
  * Representa una solicitud de entrada/salida dirigida al disco.
 * @author alexandraloynaz
 */
public class DiskRequest {
        // Información principal de la solicitud
    private final PCB process;
    private final IoOperation operation;
    private final int targetBlock;
    private final int blockCount;
    private final long arrivalCycle;

    // Información adicional según la operación
    private final String targetPath;
    private final String newName;
    private final String owner;
    private final Permission permission;

    /**
     * CREATE de un archivo: el elemento que se reservó al generar la solicitud (los bloques ya
     * están asignados). Si la solicitud falla o se cancela, el kernel lo libera.
     */
    private FsNode reservedNode;

    /**
     * Construye una solicitud de entrada/salida.
     */
    public DiskRequest(PCB process, IoOperation operation,
                       int targetBlock, int blockCount,
                       long arrivalCycle, String targetPath,
                       String newName, String owner,
                       Permission permission) {

        this.process = process;
        this.operation = operation;
        this.targetBlock = targetBlock;
        this.blockCount = blockCount;
        this.arrivalCycle = arrivalCycle;
        this.targetPath = targetPath;
        this.newName = newName;
        this.owner = owner;
        this.permission = permission;
    }

    public PCB getProcess() {
        return process;
    }

    public FsNode getReservedNode() {
        return reservedNode;
    }

    public void setReservedNode(FsNode reservedNode) {
        this.reservedNode = reservedNode;
    }

    public IoOperation getOperation() {
        return operation;
    }

    public int getTargetBlock() {
        return targetBlock;
    }

    public int getBlockCount() {
        return blockCount;
    }

    public long getArrivalCycle() {
        return arrivalCycle;
    }

    public String getTargetPath() {
        return targetPath;
    }

    public String getNewName() {
        return newName;
    }

    public String getOwner() {
        return owner;
    }

    public Permission getPermission() {
        return permission;
    }
}
    

