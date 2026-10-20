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

public class EDFPolicy implements SchedulingPolicy {

    @Override
    public PCB selectNext(LinkedQueue<PCB> readyQueue) {
        Object[] procesos = readyQueue.toArray();

        if (procesos.length == 0) {
            return null;
        }

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

    @Override
    public boolean shouldPreempt(
            PCB running,
            LinkedQueue<PCB> readyQueue,
            int cyclesInCpu) {
        return false;
    }

    @Override
    public SchedulingPolicyType getType() {
        return SchedulingPolicyType.EDF;
    }
}