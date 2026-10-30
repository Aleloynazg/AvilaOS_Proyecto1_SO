
package avilaos.kernel.scheduler;

import avilaos.kernel.process.PCB;
import avilaos.structures.LinkedQueue;


/**
 * Implementa la política de planificación Earliest Deadline First (EDF).
 *
 * Esta política selecciona para ejecución el proceso listo que tenga
 * el deadline más próximo. También permite interrumpir el proceso
 * actualmente en ejecución cuando existe otro proceso listo con
 * un deadline anterior.
 *
 * @author alexandraloynaz
 */
public class EDFPolicy implements SchedulingPolicy {
    /**
     * Selecciona el proceso listo cuyo deadline sea el más próximo.
     *
     * Recorre los procesos almacenados en la cola de listos, identifica
     * aquel que tenga el menor valor de deadline y lo elimina de la cola
     * para devolverlo como el siguiente proceso a ejecutar.
     *
     * Si la cola está vacía, no selecciona ningún proceso.
     *
     * @param readyQueue cola que contiene los procesos en estado READY.
     * @return proceso con el deadline más próximo, o null si la cola
     *         está vacía.
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

            if (procesoActual.getDeadline()
                    < procesoSeleccionado.getDeadline()) {
                procesoSeleccionado = procesoActual;
            }
        }

        readyQueue.remove(procesoSeleccionado);
        return procesoSeleccionado;
    }
    /**
     * Determina si el proceso que está ejecutándose debe ser interrumpido.
     *
     * La interrupción se produce cuando existe al menos un proceso
     * en la cola de listos cuyo deadline sea menor que el del proceso
     * actualmente en ejecución.
     *
     * Si no existe un proceso en ejecución o la cola de listos está vacía,
     * no se realiza la interrupción.
     *
     * @param running proceso que se encuentra actualmente en ejecución.
     * @param readyQueue cola de procesos que están listos para ejecutarse.
     * @param cyclesInCpu cantidad de ciclos que el proceso actual ha
     *                    utilizado la CPU. Este parámetro no interviene
     *                    en la decisión de esta política.
     * @return true si existe un proceso listo con un deadline anterior
     *         al del proceso en ejecución; false en caso contrario.
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

            if (procesoListo.getDeadline() < running.getDeadline()) {
                return true;
            }
        }

        return false;
    }
    
    /**
     * Obtiene el tipo de política de planificación implementada.
     *
     * @return tipo de planificación EDF.
     */
    @Override
    public SchedulingPolicyType getType() {
        return SchedulingPolicyType.EDF;
    }
}
