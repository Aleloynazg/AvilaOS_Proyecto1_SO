/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.kernel.disk;
import avilaos.hardware.disk.HeadDirection;
import avilaos.structures.LinkedQueue;
/**
 *
 * @author alexandraloynaz
 */
public class FifoPolicy implements DiskSchedulingPolicy{
   
    /**
     * Selecciona la primera solicitud de la cola.
     */
    @Override
    public DiskRequest next(LinkedQueue<DiskRequest> queue,
                            int currentHead,
                            HeadDirection direction) {

        return queue.dequeue();
    }

    /**
     * Obtiene el tipo de política.
     */
    @Override
    public DiskPolicyType getType() {
        return DiskPolicyType.FIFO;
    }
}
