/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.kernel.scheduler;

/**
 * * Implementa la política de planificación por prioridad con desalojo.
 *
 * Selecciona para ejecución el proceso listo que tenga el valor de prioridad
 * más bajo. Si durante la ejecución aparece un proceso con mayor prioridad,
 * el proceso actual puede ser desalojado para permitir su ejecución.
 *
 * @author alexandraloynaz
 */
import avilaos.kernel.process.PCB;
import avilaos.structures.LinkedQueue;

public class PriorityPolicy implements SchedulingPolicy {
    /**
     * Selecciona el proceso listo con mayor prioridad.
     *
     * Recorre los procesos de la cola, identifica aquel cuyo valor numérico
     * de prioridad sea menor y lo elimina de la cola para devolverlo como
     * siguiente proceso a ejecutar.
     *
     * @param readyQueue cola que contiene los procesos en estado LISTO
     * @return proceso con mayor prioridad, o {@code null} si la cola está vacía
     */
    @Override
    public PCB selectNext(LinkedQueue<PCB> readyQueue) {
        if (readyQueue.isEmpty()) {
            return null;
        }

        Object[] procesos = readyQueue.toArray();
        PCB procesoSeleccionado = (PCB) procesos[0];

        for (int i = 1; i < procesos.length; i++) {
            PCB procesoActual = (PCB) procesos[i];

            if (procesoActual.getPriority()
                    < procesoSeleccionado.getPriority()) {
                procesoSeleccionado = procesoActual;
            }
        }

        readyQueue.remove(procesoSeleccionado);
        return procesoSeleccionado;
    }
        /**
     * Determina si el proceso que se encuentra en ejecución debe ser desalojado.
     *
     * Revisa los procesos de la cola de listos y comprueba si alguno tiene
     * una prioridad mayor que la del proceso actual. En esta implementación,
     * un valor numérico de prioridad menor representa una prioridad mayor.
     *
     * @param running proceso que se encuentra actualmente en ejecución
     * @param readyQueue cola de procesos listos que pueden ejecutarse
     * @param cyclesInCpu cantidad de ciclos que el proceso actual ha utilizado
     * @return {@code true} si existe un proceso listo con mayor prioridad;
     *         {@code false} en caso contrario o si no hay proceso en ejecución
     */

    @Override
    public boolean shouldPreempt(
            PCB running,
            LinkedQueue<PCB> readyQueue,
            int cyclesInCpu) {

        if (running == null || readyQueue.isEmpty()) {
            return false;
        }

        Object[] procesos = readyQueue.toArray();

        for (int i = 0; i < procesos.length; i++) {
            PCB procesoListo = (PCB) procesos[i];

            if (procesoListo.getPriority() < running.getPriority()) {
                return true;
            }
        }

        return false;
    }
    /**
     * Obtiene el tipo de política de planificación implementada.
     *
     * @return constante {@code PRIORITY_PREEMPTIVE}, que identifica
     *         la planificación por prioridad con desalojo
     */
    @Override
    public SchedulingPolicyType getType() {
        return SchedulingPolicyType.PRIORITY_PREEMPTIVE;
    }
}