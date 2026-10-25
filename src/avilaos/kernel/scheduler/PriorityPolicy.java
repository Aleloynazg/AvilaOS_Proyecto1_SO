/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.kernel.scheduler;

/**
 *
 * @author alexandraloynaz
 */
import avilaos.kernel.process.PCB;
import avilaos.structures.LinkedQueue;

public class PriorityPolicy implements SchedulingPolicy {

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

    @Override
    public SchedulingPolicyType getType() {
        return SchedulingPolicyType.PRIORITY_PREEMPTIVE;
    }
}