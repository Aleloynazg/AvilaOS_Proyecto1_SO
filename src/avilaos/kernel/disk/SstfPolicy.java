/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.kernel.disk;

import avilaos.hardware.disk.HeadDirection;
import avilaos.structures.LinkedQueue;

/**
 * Implementa la política SSTF (Shortest Seek Time First).
 * Selecciona la solicitud cuya posición está más cerca
 * de la posición actual del cabezal.
 *
 * @author alexandraloynaz
 */
public class SstfPolicy implements DiskSchedulingPolicy {

    private HeadDirection nextDirection = HeadDirection.UP;

    @Override
    public DiskRequest next(LinkedQueue<DiskRequest> queue,
                            int currentHead,
                            HeadDirection direction) {

        nextDirection = direction;

        if (queue == null || queue.isEmpty()) {
            return null;
        }

        DiskRequest selectedRequest = null;
        int shortestDistance = Integer.MAX_VALUE;

        for (int i = 0; i < queue.size(); i++) {

            DiskRequest request = queue.get(i);

            int distance = Math.abs(
                    request.getTargetBlock() - currentHead
            );

            if (distance < shortestDistance) {
                shortestDistance = distance;
                selectedRequest = request;
            }
        }

        if (selectedRequest != null) {
            queue.remove(selectedRequest);
        }

        return selectedRequest;
    }

    @Override
    public DiskPolicyType getType() {
        return DiskPolicyType.SSTF;
    }

    @Override
    public HeadDirection getNextDirection() {
        return nextDirection;
    }
}