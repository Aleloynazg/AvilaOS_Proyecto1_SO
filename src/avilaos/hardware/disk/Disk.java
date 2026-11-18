/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.hardware.disk;
import avilaos.kernel.process.PCB;
import avilaos.kernel.disk.DiskScheduler;
import avilaos.kernel.disk.DiskRequest;
import avilaos.kernel.disk.DiskPolicyType;
import avilaos.hardware.ClockListener;
import avilaos.kernel.Kernel;
/**
 * Representa el disco físico y administra sus bloques, la posición del cabezal y la atención de solicitudes.
 * @author alexandraloynaz
 */
public class Disk implements ClockListener{

    private DiskState state = DiskState.IDLE;
    private DiskRequest currentRequest;
    private int remainingBlocks;
    private long requestStartCycle;
    private Kernel kernel;
    private boolean changingDirection;
    private boolean circularMovement;
    private long totalHeadMovement;
    private long totalWaitingTime;
    private long currentRequestWaitingTime;
    private int completedRequests;
    private int fifoCompleted;
    private int sstfCompleted;
    private int scanCompleted;
    private int cscanCompleted;
    private int lookCompleted;
    private DiskPolicyType currentPolicyType;

    /** Tamaños por defecto: los del JSON de ejemplo del enunciado. */
    public static final int DEFAULT_FILE_BLOCKS = 200;
    public static final int DEFAULT_SWAP_BLOCKS = 100;

    // Zonas del disco. Se calculan en el constructor a partir de
    // totalBlocks (JSON del sistema de archivos) y swapSizeBlocks (JSON de procesos):
    // archivos = 0 .. fileBlocks-1, swap = fileBlocks .. fileBlocks+swapBlocks-1.
    private final int totalBlocks;
    private final int fileSystemStart = 0;
    private final int fileSystemEnd;
    private final int swapStart;
    private final int swapEnd;

    private DiskBlock[] blocks;

    private int currentHead;
    private HeadDirection headDirection;

    private DiskScheduler scheduler;

    /** Disco con los tamaños por defecto: 200 bloques de archivos y 100 de swap. */
    public Disk() {
        this(DEFAULT_FILE_BLOCKS, DEFAULT_SWAP_BLOCKS);
    }

    /**
     * Crea un disco con una zona de archivos y una zona de intercambio.
     *
     * @param fileBlocks bloques para archivos (totalBlocks del JSON), al menos 1
     * @param swapBlocks bloques de swap (swapSizeBlocks del JSON), 0 o más
     */
    public Disk(int fileBlocks, int swapBlocks) {
        if (fileBlocks < 1) {
            throw new IllegalArgumentException("El disco necesita al menos 1 bloque para archivos");
        }
        if (swapBlocks < 0) {
            throw new IllegalArgumentException("El área de intercambio no puede ser negativa");
        }
        this.totalBlocks = fileBlocks + swapBlocks;
        this.fileSystemEnd = fileBlocks - 1;
        this.swapStart = fileBlocks;
        this.swapEnd = totalBlocks - 1;

        blocks = new DiskBlock[totalBlocks];
        for (int i = 0; i < totalBlocks; i++) {
            blocks[i] = new DiskBlock(i, i >= swapStart);
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
     * Conecta el disco con el Kernel.
     */
    public void setKernel(Kernel kernel) {
        this.kernel = kernel;
    }

    /**
     * Asigna bloques consecutivos para archivos.
     */
    public int allocateFileBlocks(PCB process, int numberOfBlocks) {
        return allocateBlocks(process, numberOfBlocks,
                fileSystemStart, fileSystemEnd);
    }

    /**
     * Asigna bloques consecutivos para swap.
     */
    public int allocateSwapBlocks(PCB process, int numberOfBlocks) {
        return allocateBlocks(process, numberOfBlocks,
                swapStart, swapEnd);
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
                || firstBlock + numberOfBlocks > totalBlocks) {
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

        for (int i = 0; i < totalBlocks; i++) {

            if (blocks[i].getOwner() == process) {
                blocks[i].setOwner(null);
            }
        }
    }

    /**
     * Cuenta los bloques libres para archivos.
     */
    public int countFreeFileBlocks() {
        return countFreeBlocks(fileSystemStart, fileSystemEnd);
    }

    /**
     * Cuenta los bloques libres para swap.
     */
    public int countFreeSwapBlocks() {
        return countFreeBlocks(swapStart, swapEnd);
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
    * Mueve el cabezal un bloque en la dirección indicada.
    */
    private void moveHead(HeadDirection direction) {

        if (direction == HeadDirection.UP
                && currentHead < totalBlocks - 1) {

            currentHead++;
            totalHeadMovement++;

        } else if (direction == HeadDirection.DOWN
                && currentHead > 0) {

            currentHead--;
            totalHeadMovement++;
        }
    }



    /**
    * Selecciona la siguiente solicitud que atenderá el disco.
    */
    private void selectNextRequest(long cycle) {

       if (scheduler == null || !scheduler.hasRequests()) {
           return;
       }

       DiskRequest request = scheduler.getNextRequest(
               currentHead, headDirection
       );

       if (request == null) {
           handleDirectionChange();
           return;
       }

       currentRequest = request;
       remainingBlocks = request.getBlockCount();
       requestStartCycle = cycle;

       // Guardar la política utilizada para esta solicitud.
       currentPolicyType = scheduler.getPolicy().getType();

       // Calcular el tiempo que la solicitud esperó en la cola.
       currentRequestWaitingTime = cycle - request.getArrivalCycle();

       // Determinar hacia dónde debe moverse el cabezal.
       if (request.getTargetBlock() > currentHead) {
           headDirection = HeadDirection.UP;
           state = DiskState.SEEKING;

       } else if (request.getTargetBlock() < currentHead) {
           headDirection = HeadDirection.DOWN;
           state = DiskState.SEEKING;

       } else {
           state = DiskState.TRANSFERRING;
       }
   }

    /**
     * Maneja los cambios de dirección según la política activa.
     */
    private void handleDirectionChange() {

        DiskPolicyType policyType = scheduler.getPolicy().getType();

        if (policyType == DiskPolicyType.SCAN) {

            // SCAN llega al extremo antes de invertir.
            changingDirection = true;
            state = DiskState.SEEKING;

        } else if (policyType == DiskPolicyType.LOOK) {

            // LOOK invierte la dirección sin llegar al extremo.
            headDirection = scheduler.getNextDirection();

        } else if (policyType == DiskPolicyType.CSCAN) {

            // C-SCAN llega al extremo antes de regresar al opuesto.
            circularMovement = true;
            state = DiskState.SEEKING;
        }
    }
    /**
    * Procesa el movimiento del cabezal cuando está buscando un bloque.
    */
    private void processHeadMovement() {

        // Si el cabezal llegó al extremo durante SCAN.
        if (changingDirection) {

            if (headDirection == HeadDirection.UP
                    && currentHead == totalBlocks - 1) {

                headDirection = scheduler.getNextDirection();
                changingDirection = false;
                state = DiskState.IDLE;

            } else if (headDirection == HeadDirection.DOWN
                    && currentHead == 0) {

                headDirection = scheduler.getNextDirection();
                changingDirection = false;
                state = DiskState.IDLE;
            }

            if (changingDirection) {
                moveHead(headDirection);
            }

            return;
        }

        // Movimiento normal hacia el bloque solicitado.
        if (currentRequest != null) {

            int targetBlock = currentRequest.getTargetBlock();

            if (currentHead < targetBlock) {
                moveHead(HeadDirection.UP);

            } else if (currentHead > targetBlock) {
                moveHead(HeadDirection.DOWN);
            }

            // Si llegó al bloque solicitado, comienza la transferencia.
            if (currentHead == targetBlock) {
                state = DiskState.TRANSFERRING;
            }
        }
    }
    
    /**
    * Procesa el movimiento circular del cabezal para C-SCAN.
    */
    private void processCircularMovement() {

        // Si el cabezal todavía no llegó al extremo.
        if (headDirection == HeadDirection.UP
                && currentHead < totalBlocks - 1) {

            moveHead(HeadDirection.UP);
            return;
        }

        if (headDirection == HeadDirection.DOWN
                && currentHead > 0) {

            moveHead(HeadDirection.DOWN);
            return;
        }

        // Cuando llega al extremo, regresa al extremo opuesto.
        if (headDirection == HeadDirection.UP) {
            currentHead = 0;
        } else {
            currentHead = totalBlocks - 1;
        }

        circularMovement = false;
        state = DiskState.IDLE;
    }
    
    /**
    * Procesa la transferencia de bloques de una solicitud.
    */
   private void processTransfer(long cycle) {

       if (currentRequest == null) {
           return;
       }

       // Transferir un bloque por ciclo.
       remainingBlocks--;

       // Si todavía faltan bloques, continuar en el siguiente ciclo.
       if (remainingBlocks > 0) {
           return;
       }

       // La solicitud terminó.
       completedRequests++;
       totalWaitingTime += currentRequestWaitingTime;
        currentRequestWaitingTime = 0;

       // Registrar qué política completó la solicitud.
       if (currentPolicyType == DiskPolicyType.FIFO) {
           fifoCompleted++;
       } else if (currentPolicyType == DiskPolicyType.SSTF) {
           sstfCompleted++;
       } else if (currentPolicyType == DiskPolicyType.SCAN) {
           scanCompleted++;
       } else if (currentPolicyType == DiskPolicyType.CSCAN) {
           cscanCompleted++;
       } else if (currentPolicyType == DiskPolicyType.LOOK) {
           lookCompleted++;
       }

       // Desbloquear el proceso que solicitó la operación.
       if (kernel != null) {
           kernel.unblockProcess(currentRequest.getProcess(), cycle);
       }

       // Dejar el disco disponible para otra solicitud.
       currentRequest = null;
       currentPolicyType = null;
       remainingBlocks = 0;
       state = DiskState.IDLE;
   }

    public DiskBlock getBlock(int blockNumber) {

        if (blockNumber < 0 || blockNumber >= totalBlocks) {
            return null;
        }

        return blocks[blockNumber];
    }

    public int getTotalBlocks() {
        return totalBlocks;
    }

    /** Número del último bloque del disco: SCAN y C-SCAN llegan hasta él. */
    public int getLastBlock() {
        return totalBlocks - 1;
    }

    /** Cantidad de bloques de la zona de archivos. */
    public int getFileBlockCount() {
        return fileSystemEnd + 1;
    }

    /** Cantidad de bloques del área de intercambio. */
    public int getSwapBlockCount() {
        return swapEnd - swapStart + 1;
    }

    /** Primer bloque del área de intercambio. */
    public int getSwapStart() {
        return swapStart;
    }

    public int getCurrentHead() {
        return currentHead;
    }

    public void setCurrentHead(int currentHead) {

        if (currentHead >= 0 && currentHead < totalBlocks) {
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
    @Override
    public void onTick(long cycle) {

        // Si el disco está libre, buscar una nueva solicitud.
        if (state == DiskState.IDLE) {
            selectNextRequest(cycle);
            return;
        }

        // Si está buscando un bloque mediante C-SCAN.
        if (circularMovement) {
            processCircularMovement();
            return;
        }

        // Si está buscando un bloque mediante SCAN o LOOK.
        if (state == DiskState.SEEKING) {
            processHeadMovement();
            return;
        }

        // Si llegó al bloque solicitado, transferir los datos.
        if (state == DiskState.TRANSFERRING) {
            processTransfer(cycle);
        }
    }
    public int getCompletedRequests() {
        return completedRequests;
        }/**
         * Obtiene el desplazamiento total del cabezal.
         *
         * @return cantidad total de bloques recorridos.
         */
        public long getTotalHeadMovement() {
            return totalHeadMovement;
        }

        /**
         * Calcula el tiempo promedio de espera de las solicitudes completadas.
         *
         * @return tiempo promedio de espera en ciclos.
         */
        public double getAverageWaitingTime() {
            if (completedRequests == 0) {
                return 0;
            }

            return (double) totalWaitingTime / completedRequests;
        }

        /**
         * Obtiene las solicitudes completadas mediante FIFO.
         */
        public int getFifoCompleted() {
            return fifoCompleted;
        }

        /**
         * Obtiene las solicitudes completadas mediante SSTF.
         */
        public int getSstfCompleted() {
            return sstfCompleted;
        }

        /**
         * Obtiene las solicitudes completadas mediante SCAN.
         */
        public int getScanCompleted() {
            return scanCompleted;
        }

        /**
         * Obtiene las solicitudes completadas mediante C-SCAN.
         */
        public int getCscanCompleted() {
            return cscanCompleted;
        }

        /**
         * Obtiene las solicitudes completadas mediante LOOK.
         */
        public int getLookCompleted() {
            return lookCompleted;
        }
}