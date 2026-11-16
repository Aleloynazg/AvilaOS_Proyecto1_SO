/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.kernel.disk;

/**
 *
 * Crea las políticas de planificación de solicitudes del disco.

 * @author alexandraloynaz
 */
public class DiskPolicyFactory {
        /**
     * Crea una política según el tipo seleccionado.
     *
     * @param type tipo de política solicitada
     * @return política correspondiente
     */
    public static DiskSchedulingPolicy create(DiskPolicyType type) {

        if (type == null) {
            throw new IllegalArgumentException(
                    "El tipo de política no puede ser null."
            );
        }

        switch (type) {
            case FIFO:
                return new FifoPolicy();

            case SSTF:
                return new SstfPolicy();

            case SCAN:
            case CSCAN:
            case LOOK:
                throw new UnsupportedOperationException(
                        "La política " + type + " todavía no está implementada."
                );

            default:
                throw new IllegalArgumentException(
                        "Tipo de política desconocido: " + type
                );
        }
    }
}
