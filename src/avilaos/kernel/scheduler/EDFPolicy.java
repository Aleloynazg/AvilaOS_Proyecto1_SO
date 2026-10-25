
package avilaos.kernel.scheduler;

import avilaos.kernel.process.PCB;
import avilaos.structures.LinkedQueue;
import avilaos.structures.ItemComparator;

public class EDFPolicy implements SchedulingPolicy {

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

    @Override
    public SchedulingPolicyType getType() {
        return SchedulingPolicyType.EDF;
    }
}
