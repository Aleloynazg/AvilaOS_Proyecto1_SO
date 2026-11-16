/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.kernel.disk;
import avilaos.hardware.disk.HeadDirection;
import avilaos.structures.LinkedQueue;

/**
 * Implementa la política FIFO para la planificación del disco.
 * Atiende las solicitudes en el mismo orden en que llegan.
 *
 * @author alexandraloynaz
 */
public class FifoPolicy implements DiskSchedulingPolicy {

    private HeadDirection nextDirection = HeadDirection.UP;

    @Override
    public DiskRequest next(LinkedQueue<DiskRequest> queue,
                            int currentHead,
                            HeadDirection direction) {

        nextDirection = direction;

        if (queue == null || queue.isEmpty()) {
            return null;
        }

        return queue.dequeue();
    }

    @Override
    public DiskPolicyType getType() {
        return DiskPolicyType.FIFO;
    }

    @Override
    public HeadDirection getNextDirection() {
        return nextDirection;
    }
}