/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.kernel.disk;

import avilaos.hardware.disk.HeadDirection;
import avilaos.structures.LinkedQueue;

/**
 * Implementa la política LOOK para la planificación del disco.
 * Cambia de dirección cuando no existen más solicitudes pendientes en el sentido actual.
 *
 * @author alexandraloynaz
 */
public class LookPolicy implements DiskSchedulingPolicy {

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

        // Si no hay solicitudes en el sentido actual, cambiar la dirección para la siguiente selección.
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
        return DiskPolicyType.LOOK;
    }

    @Override
    public HeadDirection getNextDirection() {
        return nextDirection;
    }
}