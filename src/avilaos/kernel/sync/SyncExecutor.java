package avilaos.kernel.sync;

import avilaos.hardware.Cluster;
import avilaos.hardware.Computer;
import avilaos.kernel.Kernel;
import avilaos.kernel.SemaphoreWaitCanceller;
import avilaos.kernel.process.BlockReason;
import avilaos.kernel.process.Instruction;
import avilaos.kernel.process.PCB;

/**
 * Ejecuta las instrucciones de sincronización (modo SO) de productores y
 * consumidores: SEM_WAIT, SEM_SIGNAL, APPEND y TAKE.
 * Bloquea o despierta a cada proceso en el Kernel de SU computador, aunque
 * el buffer viva en otro.
 */
public class SyncExecutor implements SemaphoreWaitCanceller {

    private final Cluster cluster;

    public SyncExecutor(Cluster cluster) {
        if (cluster == null) {
            throw new IllegalArgumentException("Falta el clúster");
        }
        this.cluster = cluster;
        // F3.7: cada Kernel nos avisará cuando termine un proceso que esperaba en un semáforo.
        Computer[] computers = cluster.getComputers();
        for (int i = 0; i < computers.length; i++) {
            computers[i].getKernel().setWaitCanceller(this);
        }
    }

    /**
     * Ejecuta una instrucción de sincronización del proceso que está en la CPU.
     * El PC ya debe haber avanzado (la instrucción cuenta como ejecutada).
     * Devuelve true si el proceso quedó BLOQUEADO: quien llama debe sacarlo de la CPU.
     */
    public boolean execute(PCB process, Instruction instruction, long cycle) {
        if (!instruction.isKernelMode()) {
            throw new IllegalArgumentException(instruction + " no es una instrucción de sincronización");
        }
        Buffer buffer = cluster.getBuffer(process.getBufferId());
        if (buffer == null) {
            throw new IllegalStateException(process.getTag() + " usa el buffer B"
                    + process.getBufferId() + ", que no existe");
        }
        Kernel ownKernel = cluster.getKernelOf(process);

        boolean blockedOnSemaphore;
        switch (instruction.getType()) {
            case SEM_WAIT:
                blockedOnSemaphore = semWait(process, buffer,
                        buffer.getSemaphore(instruction.getSemaphore()), ownKernel, cycle);
                break;
            case SEM_SIGNAL:
                semSignal(process, buffer, buffer.getSemaphore(instruction.getSemaphore()), ownKernel, cycle);
                blockedOnSemaphore = false;
                break;
            case APPEND:
                buffer.append();
                ownKernel.reportEvent(cycle, process.getTag() + " agrega un elemento a "
                        + buffer.getTag() + " [" + buffer.getCount() + "/" + buffer.getCapacity() + "]");
                blockedOnSemaphore = false;
                break;
            case TAKE:
                buffer.take();
                ownKernel.reportEvent(cycle, process.getTag() + " saca un elemento de "
                        + buffer.getTag() + " [" + buffer.getCount() + "/" + buffer.getCapacity() + "]");
                blockedOnSemaphore = false;
                break;
            default:
                throw new IllegalArgumentException("Instrucción no soportada: " + instruction);
        }

        if (blockedOnSemaphore) {
            return true;   // ya espera en el semáforo: no se suma la latencia
        }
        return payNetworkLatency(process, buffer, ownKernel, cycle);
    }
    
    /**
     * F3.6: si el buffer vive en otro computador, el acceso fue remoto y el proceso
     * espera la respuesta de la red: se bloquea "latencia" ciclos con motivo NETWORK.
     * Devuelve true si lo bloqueó.
     */
    private boolean payNetworkLatency(PCB process, Buffer buffer, Kernel ownKernel, long cycle) {
        if (!cluster.isRemoteAccess(process, buffer)) {
            return false;
        }
        int latency = cluster.getNetworkLatency();
        ownKernel.reportEvent(cycle, process.getTag() + " accede al buffer " + buffer.getTag()
                + " de forma remota (H" + buffer.getHostComputerId() + ")");
        // Sin latencia, o si era su última instrucción, no hay nada que esperar.
        if (latency == 0 || process.isFinished()) {
            return false;
        }
        ownKernel.blockProcess(process, BlockReason.NETWORK, latency, cycle);
        return true;
    }

    private boolean semWait(PCB process, Buffer buffer, AvilaSemaphore sem, Kernel ownKernel, long cycle) {
        boolean mustBlock = sem.semWait(process);
        if (mustBlock) {
            ownKernel.reportEvent(cycle, process.getTag() + " hace semWait(" + sem.getRole().getSymbol()
                    + ") en " + buffer.getTag() + " y espera (" + sem + ")");
            ownKernel.blockProcess(process, sem.getBlockReason(), 0, cycle);
        } else {
            ownKernel.reportEvent(cycle, process.getTag() + " hace semWait(" + sem.getRole().getSymbol()
                    + ") en " + buffer.getTag() + " y pasa (" + sem + ")");
        }
        return mustBlock;
    }

    private void semSignal(PCB process, Buffer buffer, AvilaSemaphore sem, Kernel ownKernel, long cycle) {
        PCB woken = sem.semSignal();
        String text = process.getTag() + " hace semSignal(" + sem.getRole().getSymbol()
                + ") en " + buffer.getTag() + " (" + sem + ")";
        if (woken != null) {
            text += " y despierta a " + woken.getTag();
        }
        ownKernel.reportEvent(cycle, text);
        if (woken != null) {
            // El despertado puede ser de OTRO computador: se usa el Kernel de SU computador.
            cluster.getKernelOf(woken).unblockProcess(woken, cycle);
        }
    }

    /** F3.7: saca al proceso de la cola del semáforo donde espera (lo llama el Kernel). */
    @Override
    public void cancelWait(PCB process) {
        Buffer buffer = cluster.getBuffer(process.getBufferId());
        if (buffer == null) {
            return;
        }
        if (!buffer.getMutex().cancelWait(process)
                && !buffer.getFull().cancelWait(process)) {
            buffer.getEmpty().cancelWait(process);
        }
    }
}