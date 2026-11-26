/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.hardware.disk;
import avilaos.kernel.fs.FileEntry;
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

    /** Límites de la velocidad del cabezal (posiciones por ciclo). */
    public static final int MIN_HEAD_SPEED = 1;
    public static final int MAX_HEAD_SPEED = 100;

    /** Posiciones que recorre el cabezal por ciclo de reloj (configurable, por defecto 1). */
    private int headSpeed = MIN_HEAD_SPEED;

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
     * Primer bloque libre de la zona de archivos (donde empezaría un archivo nuevo).
     *
     * @return el número de bloque, o -1 si la zona de archivos está llena
     */
    public int getFirstFreeFileBlock() {
        for (int i = fileSystemStart; i <= fileSystemEnd; i++) {
            if (blocks[i].isFree()) {
                return i;
            }
        }
        return -1;
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
    * Mueve el cabezal en la dirección indicada, headSpeed posiciones por ciclo
    * sin salirse del disco.
    */
    private void moveHead(HeadDirection direction) {
        moveHead(direction, headSpeed);
    }

    /**
    * Mueve el cabezal en la dirección indicada, como máximo maxSteps posiciones
    * (para no pasarse del bloque destino) y nunca más de headSpeed ni más allá
    * de los extremos del disco. Cada posición recorrida suma al desplazamiento total.
    */
    private void moveHead(HeadDirection direction, int maxSteps) {

        int steps = Math.min(headSpeed, maxSteps);

        if (direction == HeadDirection.UP) {
            steps = Math.min(steps, totalBlocks - 1 - currentHead);
            if (steps > 0) {
                currentHead += steps;
                totalHeadMovement += steps;
            }

        } else if (direction == HeadDirection.DOWN) {
            steps = Math.min(steps, currentHead);
            if (steps > 0) {
                currentHead -= steps;
                totalHeadMovement += steps;
            }
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

       // Avisar al kernel qué solicitud eligió el disco (queda en el log del sistema).
       if (kernel != null) {
           kernel.onDiskRequestStarted(request, currentPolicyType, cycle);
       }

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
                moveHead(HeadDirection.UP, targetBlock - currentHead);

            } else if (currentHead > targetBlock) {
                moveHead(HeadDirection.DOWN, currentHead - targetBlock);
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

       // Dejar el disco disponible antes de avisar al kernel: así el kernel puede mandar
       // otra solicitud (por ejemplo, de swap) sin chocar con la que acaba de terminar.
       DiskRequest finished = currentRequest;
       currentRequest = null;
       currentPolicyType = null;
       remainingBlocks = 0;
       state = DiskState.IDLE;

       // El kernel aplica la operación sobre el sistema de archivos y despierta al proceso.
       if (kernel != null) {
           kernel.onDiskRequestCompleted(finished, cycle);
       }
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

    // ------------------------------------------------------------------
    // Asignación ENCADENADA de archivos (la que pide el enunciado).
    // Cada bloque guarda el número del siguiente; el archivo solo guarda
    // el primero. Los bloques de un archivo no tienen que estar juntos.
    // ------------------------------------------------------------------

    /**
     * Asigna a un archivo los primeros bloques libres de la zona de archivos,
     * aunque no estén seguidos, y los encadena.
     *
     * @return el primer bloque de la cadena, o -1 si no hay suficientes libres
     *         (en ese caso no se toca ningún bloque)
     */
    public int allocateFileChain(FileEntry file, int numberOfBlocks) {
        if (file == null || numberOfBlocks <= 0
                || numberOfBlocks > countFreeFileBlocks()) {
            return -1;
        }
        int first = -1;
        int previous = -1;
        int assigned = 0;
        for (int i = fileSystemStart; i <= fileSystemEnd && assigned < numberOfBlocks; i++) {
            if (blocks[i].isFree()) {
                blocks[i].setFile(file);
                blocks[i].setNext(-1);
                if (previous == -1) {
                    first = i;
                } else {
                    blocks[previous].setNext(i);
                }
                previous = i;
                assigned++;
            }
        }
        return first;
    }

    /**
     * Asigna a un archivo los bloques startBlock .. startBlock + n - 1 y los
     * encadena. Lo usa la carga del JSON, que trae "startBlock" y "blocks".
     *
     * @return false si algún bloque está fuera de la zona de archivos o ocupado
     *         (en ese caso no se toca ningún bloque)
     */
    public boolean allocateFileAt(FileEntry file, int startBlock, int numberOfBlocks) {
        if (file == null || numberOfBlocks <= 0 || startBlock < fileSystemStart
                || startBlock + numberOfBlocks - 1 > fileSystemEnd) {
            return false;
        }
        for (int i = startBlock; i < startBlock + numberOfBlocks; i++) {
            if (!blocks[i].isFree()) {
                return false;
            }
        }
        for (int i = startBlock; i < startBlock + numberOfBlocks; i++) {
            blocks[i].setFile(file);
            blocks[i].setNext(i + 1 < startBlock + numberOfBlocks ? i + 1 : -1);
        }
        return true;
    }

    /**
     * Asigna a un archivo exactamente los bloques indicados, enlazados en ese orden
     * (asignación encadenada con bloques que no tienen que ser consecutivos).
     * Sirve para volver a cargar un archivo que quedó fragmentado.
     *
     * @return true si todos los bloques existen en la zona de archivos, no se repiten
     *         y están libres; si no, no cambia nada y devuelve false
     */
    public boolean allocateFileOn(FileEntry file, int[] chain) {
        if (file == null || chain == null || chain.length == 0) {
            return false;
        }
        for (int i = 0; i < chain.length; i++) {
            if (chain[i] < fileSystemStart || chain[i] > fileSystemEnd || !blocks[chain[i]].isFree()) {
                return false;
            }
            for (int j = 0; j < i; j++) {
                if (chain[j] == chain[i]) {
                    return false;
                }
            }
        }
        for (int i = 0; i < chain.length; i++) {
            blocks[chain[i]].setFile(file);
            blocks[chain[i]].setNext(i + 1 < chain.length ? chain[i + 1] : -1);
        }
        return true;
    }

    /**
     * Libera todos los bloques de la cadena que empieza en firstBlock.
     *
     * @return cuántos bloques se liberaron
     */
    public int freeFileChain(int firstBlock) {
        int freed = 0;
        int current = firstBlock;
        // El límite de pasos evita un ciclo infinito si la cadena estuviera dañada.
        while (current >= fileSystemStart && current <= fileSystemEnd
                && blocks[current].getFile() != null && freed < totalBlocks) {
            int next = blocks[current].getNext();
            blocks[current].setFile(null);
            blocks[current].setNext(-1);
            freed++;
            current = next;
        }
        return freed;
    }

    /**
     * Los números de bloque de la cadena, en orden. Sirve para la tabla de
     * asignación y para dibujar el mapa del disco.
     */
    public int[] getChain(int firstBlock) {
        int length = 0;
        int current = firstBlock;
        while (current >= 0 && current < totalBlocks && length < totalBlocks
                && blocks[current].getFile() != null) {
            length++;
            current = blocks[current].getNext();
        }
        int[] chain = new int[length];
        current = firstBlock;
        for (int i = 0; i < length; i++) {
            chain[i] = current;
            current = blocks[current].getNext();
        }
        return chain;
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

    public DiskRequest getCurrentRequest() {
        return currentRequest;
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

    /** Velocidad del cabezal: posiciones que recorre por ciclo de reloj. */
    public int getHeadSpeed() {
        return headSpeed;
    }

    /**
     * Cambia la velocidad del cabezal.
     *
     * @param headSpeed posiciones por ciclo, entre MIN_HEAD_SPEED y MAX_HEAD_SPEED
     * @throws IllegalArgumentException si está fuera de rango
     */
    public void setHeadSpeed(int headSpeed) {
        if (headSpeed < MIN_HEAD_SPEED || headSpeed > MAX_HEAD_SPEED) {
            throw new IllegalArgumentException("La velocidad del cabezal debe estar entre "
                    + MIN_HEAD_SPEED + " y " + MAX_HEAD_SPEED + " posiciones por ciclo");
        }
        this.headSpeed = headSpeed;
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