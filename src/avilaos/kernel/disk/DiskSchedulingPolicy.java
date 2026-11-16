/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package avilaos.kernel.disk;

/**
 * Define el contrato para las políticas de planificación de solicitudes de entrada y salida del disco.
 * @author alexandraloynaz
 */
import avilaos.hardware.disk.HeadDirection;
import avilaos.structures.LinkedQueue;
/**
* Selecciona la siguiente solicitud que será atendida.
* @param queue cola de solicitudes pendientes
* @param currentHead posición actual del cabezal del disco
* @param direction dirección actual del cabezal
* @return solicitud seleccionada
*/

public interface DiskSchedulingPolicy {
    DiskRequest next(LinkedQueue<DiskRequest> queue, int currentHead,
                     HeadDirection direction);
    /**
     * Obtiene el tipo de política de planificación.
     *
     * @return tipo de política
     */
    DiskPolicyType getType();
}
