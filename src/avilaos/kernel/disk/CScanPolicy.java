/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.kernel.disk;
import avilaos.hardware.disk.HeadDirection;
import avilaos.structures.LinkedQueue;

/**
 * Implementa la política C-SCAN (Circular SCAN).
 * Atiende las solicitudes en una sola dirección.
 * Al llegar al extremo del disco, regresa al extremo contrario sin atender solicitudes durante el retorno.
 *
 * @author alexandraloynaz
 */
public class CScanPolicy implements DiskSchedulingPolicy{
    private static final int MIN_BLOCK = 0;
    private static final int MAX_BLOCK = 299;

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

        // Atender primero una solicitud en la posición actual.
        for (int i = 0; i < queue.size(); i++) {

            DiskRequest request = queue.get(i);

            if (request.getTargetBlock() == currentHead) {
                selectedRequest = request;
                break;
            }
        }

        // Si avanza hacia UP, buscar el bloque más cercano
        // que esté por encima de la posición actual.
        if (selectedRequest == null && direction == HeadDirection.UP) {

            closestBlock = Integer.MAX_VALUE;

            for (int i = 0; i < queue.size(); i++) {

                DiskRequest request = queue.get(i);
                int targetBlock = request.getTargetBlock();

                if (targetBlock > currentHead
                        && targetBlock <= MAX_BLOCK
                        && targetBlock < closestBlock) {

                    closestBlock = targetBlock;
                    selectedRequest = request;
                }
            }
        }

        // Si avanza hacia DOWN, buscar el bloque más cercano
        // que esté por debajo de la posición actual.
        if (selectedRequest == null && direction == HeadDirection.DOWN) {

            closestBlock = Integer.MIN_VALUE;

            for (int i = 0; i < queue.size(); i++) {

                DiskRequest request = queue.get(i);
                int targetBlock = request.getTargetBlock();

                if (targetBlock < currentHead
                        && targetBlock >= MIN_BLOCK
                        && targetBlock > closestBlock) {

                    closestBlock = targetBlock;
                    selectedRequest = request;
                }
            }
        }

        // Si no hay solicitudes en la dirección actual,
        // el disco deberá realizar el recorrido circular
        // en F1.4. La dirección se mantiene.
        if (selectedRequest == null) {
            return null;
        }

        queue.remove(selectedRequest);

        return selectedRequest;
    }

    @Override
    public DiskPolicyType getType() {
        return DiskPolicyType.CSCAN;
    }

    @Override
    public HeadDirection getNextDirection() {
        return nextDirection;
    }
}
    
