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

public class FCFSPolicy implements SchedulingPolicy {

    @Override
    public PCB selectNext(LinkedQueue<PCB> readyQueue) {
        return readyQueue.dequeue();
   
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
        return SchedulingPolicyType.FCFS;
    }
}