package avilaos.kernel;

import avilaos.kernel.process.PCB;

/**
 * Quien sabe sacar a un proceso de la cola del semáforo donde espera.
 * El Kernel lo llama cuando termina un proceso que estaba bloqueado en un
 * semáforo (por ejemplo, porque se le venció el deadline).
 */
public interface SemaphoreWaitCanceller {
    void cancelWait(PCB process);
}