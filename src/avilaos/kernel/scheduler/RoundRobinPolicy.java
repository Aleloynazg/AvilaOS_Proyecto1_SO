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

public class RoundRobinPolicy implements SchedulingPolicy {
    private int quantum;
    public RoundRobinPolicy(int quantum) {
        if (quantum <= 0) {
            throw new IllegalArgumentException(
                "El quantum debe ser mayor que cero."
            );
        }
        this.quantum = quantum;
    }

    public int getQuantum() {
        return quantum;
    }

    public void setQuantum(int quantum) {
        if (quantum > 0) {
            this.quantum = quantum;
        }}

    @Override
    public PCB selectNext(LinkedQueue<PCB> readyQueue) {
        return readyQueue.dequeue();
    }

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

    @Override
    public SchedulingPolicyType getType() {
        return SchedulingPolicyType.ROUND_ROBIN;
    }
}