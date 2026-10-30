/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.kernel.scheduler;

/**
 /**
 * Implementa la política de planificación First Come, First Served (FCFS).
 *
 * Esta política selecciona los procesos según el orden en que ingresaron
 * a la cola de listos. El primer proceso en entrar es el primero en
 * ejecutarse.
 *
 * No utiliza interrupción preventiva, por lo que un proceso continúa
 * ejecutándose hasta que finaliza o es bloqueado por otro componente
 * del sistema.
 *
 * @author alexandraloynaz
 */
import avilaos.kernel.process.PCB;
import avilaos.structures.LinkedQueue;

public class FCFSPolicy implements SchedulingPolicy {
    /**
     * Selecciona el siguiente proceso de la cola de listos.
     *
     * Extrae y devuelve el primer proceso de la cola, respetando
     * el orden de llegada.
     *
     * @param readyQueue cola que contiene los procesos en estado READY.
     * @return primer proceso de la cola, o el valor devuelto por dequeue()
     *         cuando la cola está vacía.
     */
    @Override
    public PCB selectNext(LinkedQueue<PCB> readyQueue) {
        return readyQueue.dequeue();
   
    }
    /**
     * Determina si el proceso actualmente en ejecución debe ser interrumpido.
     *
     * La política FCFS no utiliza planificación preventiva, por lo que
     * nunca interrumpe un proceso para ejecutar otro que se encuentre
     * en la cola de listos.
     *
     * @param running proceso que se encuentra actualmente en ejecución.
     * @param readyQueue cola de procesos que están listos para ejecutarse.
     * @param cyclesInCpu cantidad de ciclos que el proceso actual ha
     *                    utilizado la CPU. Este parámetro no interviene
     *                    en esta política.
     * @return false, porque FCFS no realiza interrupciones preventivas.
     */
    @Override
    public boolean shouldPreempt(
            PCB running,
            LinkedQueue<PCB> readyQueue,
            int cyclesInCpu) {
        return false;
    }
    /**
     * Obtiene el tipo de política de planificación implementada.
     *
     * @return tipo de planificación FCFS.
     */
    @Override
    public SchedulingPolicyType getType() {
        return SchedulingPolicyType.FCFS;
    }
}