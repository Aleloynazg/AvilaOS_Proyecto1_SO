/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.kernel.disk;

/**
 * * Administra las solicitudes de entrada y salida del disco y coordina su atención mediante una política de planificación.
 * @author alexandraloynaz
 */

import avilaos.kernel.process.PCB;
import avilaos.structures.LinkedQueue;
import java.util.concurrent.Semaphore;
import avilaos.hardware.disk.HeadDirection;


public class DiskScheduler {
 
    private final LinkedQueue<DiskRequest> requests;
    private final Semaphore availableRequests;

    private DiskSchedulingPolicy policy;

    /**
     * Construye el planificador con una política inicial.
     *
     * @param policy política de planificación utilizada
     */
    public DiskScheduler(DiskSchedulingPolicy policy) {

        if (policy == null) {
            throw new IllegalArgumentException(
                    "La política de planificación no puede ser null."
            );
        }

        this.requests = new LinkedQueue<>();
        this.availableRequests = new Semaphore(0);
        this.policy = policy;
    }

    /**
     * Agrega una solicitud a la cola.
     *
     * @param request solicitud de entrada y salida
     */
    public void addRequest(DiskRequest request) {

        if (request == null) {
            return;
        }

        requests.enqueue(request);
        availableRequests.release();
    }

    /**
     * Saca de la cola todas las solicitudes pendientes de un proceso (por ejemplo, porque
     * el proceso terminó por deadline mientras esperaba). No toca la solicitud que el disco
     * ya está atendiendo, porque esa ya salió de la cola.
     *
     * @return cuántas solicitudes se cancelaron
     */
    public int cancelRequestsOf(PCB process) {
        int cancelled = 0;
        Object[] pending = requests.toArray();
        for (Object o : pending) {
            DiskRequest request = (DiskRequest) o;
            if (request.getProcess() == process && requests.remove(request)) {
                availableRequests.tryAcquire();   // el semáforo cuenta las solicitudes en cola
                cancelled++;
            }
        }
        return cancelled;
    }

    /**
     * Indica si existen solicitudes pendientes.
     *
     * @return true si hay solicitudes pendientes
     */
    public boolean hasRequests() {
        return !requests.isEmpty();
    }

    /**
     * Devuelve la cantidad de solicitudes pendientes.
     *
     * @return cantidad de solicitudes
     */
    public int getRequestCount() {
        return requests.size();
    }

    /**
     * Selecciona la siguiente solicitud según la política activa.
     *
     * @param currentHead posición actual del cabezal
     * @param direction dirección actual del cabezal
     * @return solicitud seleccionada o null si no se seleccionó ninguna
     */
    public DiskRequest getNextRequest(int currentHead,
                                      HeadDirection direction) {

        if (!availableRequests.tryAcquire()) {
            return null;
        }

        DiskRequest nextRequest = policy.next(
                requests, currentHead, direction
        );

        if (nextRequest == null) {
            availableRequests.release();
        }

        return nextRequest;
    }

    /**
     * Cambia la política de planificación.
     *
     * @param policy nueva política
     */
    public void setPolicy(DiskSchedulingPolicy policy) {

        if (policy != null) {
            this.policy = policy;
        }
    }

    /**
     * Obtiene la política de planificación activa.
     *
     * @return política actual
     */
    public DiskSchedulingPolicy getPolicy() {
        return policy;
    }

    /**
     * Obtiene la dirección indicada por la política activa.
     *
     * @return dirección del cabezal para la siguiente selección
     */
    public HeadDirection getNextDirection() {
        return policy.getNextDirection();
    }

    /**
     * Obtiene una copia de las solicitudes pendientes.
     *
     * @return arreglo con las solicitudes
     */
    public Object[] getRequests() {
        return requests.toArray();
    }
}