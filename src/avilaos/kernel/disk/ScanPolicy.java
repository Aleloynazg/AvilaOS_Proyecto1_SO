/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.kernel.disk;
import avilaos.hardware.disk.HeadDirection;
import avilaos.structures.LinkedQueue;


/**
 * Implementa la política SCAN para la planificación del disco.
 * Selecciona solicitudes según la dirección del cabezal.
 *
 * @author alexandraloynaz
 */
public class ScanPolicy implements DiskSchedulingPolicy {


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
        int closestBlock;

        // Buscar primero una solicitud en la posición actual.
        for (int i = 0; i < queue.size(); i++) {

            DiskRequest request = queue.get(i);

            if (request.getTargetBlock() == currentHead) {
                selectedRequest = request;
                break;
            }
        }

        // Buscar la solicitud más cercana hacia arriba.
        if (selectedRequest == null && direction == HeadDirection.UP) {

            closestBlock = Integer.MAX_VALUE;

            for (int i = 0; i < queue.size(); i++) {

                DiskRequest request = queue.get(i);
                int targetBlock = request.getTargetBlock();

                if (targetBlock > currentHead
                        && targetBlock < closestBlock) {

                    closestBlock = targetBlock;
                    selectedRequest = request;
                }
            }
        }

        // Buscar la solicitud más cercana hacia abajo.
        if (selectedRequest == null && direction == HeadDirection.DOWN) {

            closestBlock = Integer.MIN_VALUE;

            for (int i = 0; i < queue.size(); i++) {

                DiskRequest request = queue.get(i);
                int targetBlock = request.getTargetBlock();

                if (targetBlock < currentHead
                        && targetBlock > closestBlock) {

                    closestBlock = targetBlock;
                    selectedRequest = request;
                }
            }
        }

        // Si no hay solicitudes en el sentido actual,
        // indicar que debe cambiarse la dirección.
        if (selectedRequest == null) {

            if (direction == HeadDirection.UP) {
                nextDirection = HeadDirection.DOWN;
            } else {
                nextDirection = HeadDirection.UP;
            }

            return null;
        }

        queue.remove(selectedRequest);

        return selectedRequest;
    }

    @Override
    public DiskPolicyType getType() {
        return DiskPolicyType.SCAN;
    }

    @Override
    public HeadDirection getNextDirection() {
        return nextDirection;
    }
}