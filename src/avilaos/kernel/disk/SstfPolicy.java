/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.kernel.disk;

import avilaos.hardware.disk.HeadDirection;
import avilaos.structures.LinkedQueue;

/**
 * Implementa la política SSTF para la planificación del disco.
 * Selecciona la solicitud cuyo bloque esté más cerca de la posición actual del cabezal.
 * @author alexandraloynaz
 */
public class SstfPolicy implements DiskSchedulingPolicy {

    /**
     * Selecciona la solicitud con menor distancia al cabezal.
     *
     * @param queue cola de solicitudes pendientes
     * @param currentHead posición actual del cabezal
     * @param direction dirección actual del cabezal
     * @return solicitud más cercana
     */
    @Override
    public DiskRequest next(LinkedQueue<DiskRequest> queue,
                            int currentHead,
                            HeadDirection direction) {

        if (queue == null || queue.isEmpty()) {
            return null;
        }

        DiskRequest closestRequest = null;
        int shortestDistance = Integer.MAX_VALUE;

        // Revisar todas las solicitudes pendientes.
        for (int i = 0; i < queue.size(); i++) {

            DiskRequest request = queue.get(i);

            int distance = Math.abs(
                    request.getTargetBlock() - currentHead
            );

            if (distance < shortestDistance) {
                shortestDistance = distance;
                closestRequest = request;
            }
        }

        // Eliminar de la cola la solicitud seleccionada.
        queue.remove(closestRequest);

        return closestRequest;
    }

    /**
     * Obtiene el tipo de política.
     *
     * @return SSTF
     */
    @Override
    public DiskPolicyType getType() {
        return DiskPolicyType.SSTF;
    }
}