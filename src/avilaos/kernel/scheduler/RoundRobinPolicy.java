/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.kernel.scheduler;

/**
 * Implementa la política de planificación Round Robin.
 *
 * Esta política distribuye el tiempo de ejecución entre los procesos listos.
 * Cada proceso puede utilizar la CPU durante una cantidad máxima de ciclos
 * definida por el quantum. Cuando alcanza ese límite y existen otros procesos
 * listos, el proceso actual puede ser desalojado.
 *
 * @author alexandraloynaz
 */

import avilaos.kernel.process.PCB;
import avilaos.structures.LinkedQueue;

 
public class RoundRobinPolicy implements SchedulingPolicy {
    private int quantum;
    
   /**
     * Construye una política Round Robin con el quantum indicado.
     *
     * @param quantum cantidad máxima de ciclos permitidos por turno
     * @throws IllegalArgumentException si el quantum es menor o igual a cero
     */
    public RoundRobinPolicy(int quantum) {
        if (quantum <= 0) {
            throw new IllegalArgumentException(
                "El quantum debe ser mayor que cero."
            );
        }
        this.quantum = quantum;
    }
    /**
     * Obtiene el quantum configurado para esta política.
     *
     * @return cantidad máxima de ciclos permitidos por turno
     */
    public int getQuantum() {
        return quantum;
    }
    /**
     * Actualiza el quantum de la política.
     *
     * Solo modifica el valor actual si el nuevo quantum es mayor que cero.
     *
     * @param quantum nueva cantidad máxima de ciclos por turno
     */
    public void setQuantum(int quantum) {
        if (quantum > 0) {
            this.quantum = quantum;
        }}
    /**
     * Selecciona el siguiente proceso de la cola de listos.
     *
     * Retira y devuelve el primer proceso de la cola, manteniendo
     * el orden de llegada de los procesos.
     *
     * @param readyQueue cola que contiene los procesos en estado LISTO
     * @return siguiente proceso de la cola, según el comportamiento
     *         del método {@code dequeue()}
     */
    @Override
    public PCB selectNext(LinkedQueue<PCB> readyQueue) {
        return readyQueue.dequeue();
    }
    /**
     * Determina si el proceso en ejecución debe ser desalojado.
     *
     * El desalojo ocurre cuando existe al menos otro proceso listo
     * y el proceso actual ha utilizado una cantidad de ciclos igual
     * o superior al quantum establecido.
     *
     * @param running proceso que se encuentra actualmente en ejecución
     * @param readyQueue cola de procesos listos
     * @param cyclesInCpu cantidad de ciclos que el proceso actual ha utilizado
     * @return {@code true} si se cumple el quantum y hay otros procesos listos;
     *         {@code false} si no hay proceso en ejecución o no corresponde
     *         realizar el desalojo
     */
    @Override
    public boolean shouldPreempt(
            PCB running,
            LinkedQueue<PCB> readyQueue,
            int cyclesInCpu) {
        if (running == null) {
            return false;
        }

        if (readyQueue.isEmpty()) {
            return false;
        }

        return cyclesInCpu >= quantum;
    }
    /**
     * Obtiene el tipo de política de planificación implementada.
     *
     * @return constante {@code ROUND_ROBIN}, que identifica
     *         la planificación Round Robin
     */
    @Override
    public SchedulingPolicyType getType() {
        return SchedulingPolicyType.ROUND_ROBIN;
    }
}