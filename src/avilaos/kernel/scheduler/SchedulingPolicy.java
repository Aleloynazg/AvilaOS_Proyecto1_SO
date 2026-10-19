package avilaos.kernel.scheduler;

import avilaos.kernel.process.PCB;
import avilaos.structures.LinkedQueue;

/**
 * Comportamiento intercambiable del planificador. Agregar una política
 * nueva = crear otra clase que implemente esta interfaz, sin tocar el
 * planificador. Requisito obligatorio.
 */
public interface SchedulingPolicy {

    /**
     * Saca de la cola de listos el próximo proceso a ejecutar.
     * Cada política ordena la cola a su manera (el enunciado exige que
     * el ordenamiento lo programen ustedes).
     */
    PCB selectNext(LinkedQueue<PCB> readyQueue);

    /**
     * ¿Hay que expulsar al proceso en ejecución?
     *
     * @param running      proceso en la CPU (puede ser null)
     * @param readyQueue   cola de listos actual
     * @param cyclesInCpu  ciclos seguidos que lleva el proceso en la CPU
     */
    boolean shouldPreempt(PCB running, LinkedQueue<PCB> readyQueue, int cyclesInCpu);

    SchedulingPolicyType getType();
}
