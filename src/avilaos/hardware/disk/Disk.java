/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.hardware.disk;
import avilaos.kernel.process.PCB;
import avilaos.kernel.disk.DiskScheduler;
import avilaos.kernel.disk.DiskRequest;
import avilaos.kernel.disk.DiskPolicyType;
/**
 * Representa el disco físico y administra sus bloques, la posición del cabezal y la atención de solicitudes.
 * @author alexandraloynaz
 */
public class Disk {

    private static final int TOTAL_BLOCKS = 300;
    private static final int FILE_SYSTEM_START = 0;
    private static final int FILE_SYSTEM_END = 199;
    private static final int SWAP_START = 200;
    private static final int SWAP_END = 299;

    private DiskBlock[] blocks;

    private int currentHead;
    private HeadDirection headDirection;

    private DiskScheduler scheduler;

    public Disk() {

        blocks = new DiskBlock[TOTAL_BLOCKS];

        for (int i = 0; i < TOTAL_BLOCKS; i++) {
            blocks[i] = new DiskBlock(i);
        }

        currentHead = 0;
        headDirection = HeadDirection.UP;
    }

    /**
     * Conecta el disco con su planificador.
     */
    public void setScheduler(DiskScheduler scheduler) {
        this.scheduler = scheduler;
    }
    public DiskScheduler getScheduler() {
    return scheduler;
}

    /**
     * Asigna bloques consecutivos para archivos.
     */
    public int allocateFileBlocks(PCB process, int numberOfBlocks) {
        return allocateBlocks(process, numberOfBlocks,
                FILE_SYSTEM_START, FILE_SYSTEM_END);
    }

    /**
     * Asigna bloques consecutivos para swap.
     */
    public int allocateSwapBlocks(PCB process, int numberOfBlocks) {
        return allocateBlocks(process, numberOfBlocks,
                SWAP_START, SWAP_END);
    }

    /**
     * Busca y asigna una cadena de bloques libres.
     */
    private int allocateBlocks(PCB process, int numberOfBlocks,
                               int start, int end) {

        if (process == null || numberOfBlocks <= 0
                || numberOfBlocks > end - start + 1) {
            return -1;
        }

        int consecutiveFree = 0;

        for (int i = start; i <= end; i++) {

            if (blocks[i].isFree()) {
                consecutiveFree++;
            } else {
                consecutiveFree = 0;
            }

            if (consecutiveFree == numberOfBlocks) {

                int firstBlock = i - numberOfBlocks + 1;

                for (int j = firstBlock; j <= i; j++) {
                    blocks[j].setOwner(process);
                }

                return firstBlock;
            }
        }

        return -1;
    }

    /**
     * Libera una cadena específica de bloques.
     */
    public void freeBlocks(PCB process, int firstBlock,
                           int numberOfBlocks) {

        if (process == null || numberOfBlocks <= 0
                || firstBlock < 0
                || firstBlock + numberOfBlocks > TOTAL_BLOCKS) {
            return;
        }

        for (int i = firstBlock;
                i < firstBlock + numberOfBlocks; i++) {

            if (blocks[i].getOwner() == process) {
                blocks[i].setOwner(null);
            }
        }
    }

    /**
     * Libera todos los bloques de un proceso.
     */
    public void freeBlocks(PCB process) {

        if (process == null) {
            return;
        }

        for (int i = 0; i < TOTAL_BLOCKS; i++) {

            if (blocks[i].getOwner() == process) {
                blocks[i].setOwner(null);
            }
        }
    }

    /**
     * Cuenta los bloques libres para archivos.
     */
    public int countFreeFileBlocks() {
        return countFreeBlocks(FILE_SYSTEM_START, FILE_SYSTEM_END);
    }

    /**
     * Cuenta los bloques libres para swap.
     */
    public int countFreeSwapBlocks() {
        return countFreeBlocks(SWAP_START, SWAP_END);
    }

    private int countFreeBlocks(int start, int end) {

        int freeBlocks = 0;

        for (int i = start; i <= end; i++) {
            if (blocks[i].isFree()) {
                freeBlocks++;
            }
        }

        return freeBlocks;
    }

    /**
     * Procesa la siguiente solicitud del disco.
     *
     * @return solicitud atendida o null si no se pudo seleccionar
     */

    public DiskRequest processNextRequest() {

    if (scheduler == null || !scheduler.hasRequests()) {
        return null;
    }

    // Guardar la dirección antes de consultar la política.
    HeadDirection previousDirection = headDirection;

    DiskRequest request = scheduler.getNextRequest(
            currentHead, headDirection
    );

    if (request == null) {

        if (!scheduler.hasRequests()) {
            return null;
        }

        DiskPolicyType policyType = scheduler.getPolicy().getType();

        if (policyType == DiskPolicyType.SCAN) {

            // Llegar primero al extremo físico.
            if (previousDirection == HeadDirection.UP) {
                currentHead = TOTAL_BLOCKS - 1;
            } else {
                currentHead = 0;
            }

            // Después invertir la dirección.
            headDirection = scheduler.getNextDirection();
        }

        if (policyType == DiskPolicyType.CSCAN) {

            // Llegar al extremo físico antes de regresar.
            if (previousDirection == HeadDirection.UP) {
                currentHead = TOTAL_BLOCKS - 1;
                currentHead = 0;
            } else {
                currentHead = 0;
                currentHead = TOTAL_BLOCKS - 1;
            }
        }

        if (policyType == DiskPolicyType.LOOK) {

            // LOOK invierte la dirección sin llegar al extremo.
            headDirection = scheduler.getNextDirection();
        }

        return null;
    }

    // Actualizar la posición del cabezal.
    currentHead = request.getTargetBlock();

    // Mantener la dirección indicada por la política.
    headDirection = scheduler.getNextDirection();

    return request;
}

    public DiskBlock getBlock(int blockNumber) {

        if (blockNumber < 0 || blockNumber >= TOTAL_BLOCKS) {
            return null;
        }

        return blocks[blockNumber];
    }

    public int getTotalBlocks() {
        return TOTAL_BLOCKS;
    }

    public int getCurrentHead() {
        return currentHead;
    }

    public void setCurrentHead(int currentHead) {

        if (currentHead >= 0 && currentHead < TOTAL_BLOCKS) {
            this.currentHead = currentHead;
        }
    }

    public HeadDirection getHeadDirection() {
        return headDirection;
    }

    public void setHeadDirection(HeadDirection headDirection) {

        if (headDirection != null) {
            this.headDirection = headDirection;
        }
    }
}