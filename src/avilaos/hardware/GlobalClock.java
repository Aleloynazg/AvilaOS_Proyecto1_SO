/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.hardware;

/**
 * * Representa el reloj global del simulador AvilaOS.
 *
 * Es responsable de generar los ciclos que permiten sincronizar
 * la ejecución de los diferentes componentes del sistema.
 * Los componentes registrados reciben una notificación cada vez
 * que se genera un nuevo ciclo.
 *
 * El reloj funciona en un hilo independiente y permite iniciar,
 * pausar, reanudar y detener su ejecución.
 *
 * @author alexandraloynaz
 */

import java.util.concurrent.Semaphore;

public class GlobalClock extends Thread {

    private volatile long cycle;
    private volatile int cycleDuration;
    private volatile boolean running;
    private volatile boolean paused;

    private boolean started;

    // Protege el estado del reloj y la lista de componentes.
    private final Semaphore controlSemaphore;

    // Componentes que reciben cada ciclo del reloj.
    private ClockListener[] listeners;
    private int numberOfListeners;
    
    /**
     * Construye el reloj global del simulador.
     *
     * Inicializa el contador de ciclos en cero, establece la duración
     * de cada ciclo y prepara la lista de componentes que recibirán
     * las notificaciones.
     *
     * @param cycleDuration duración de cada ciclo en milisegundos.
     */
    public GlobalClock(int cycleDuration) {
        this.cycle = 0;
        this.cycleDuration = cycleDuration;
        this.running = false;
        this.paused = false;
        this.started = false;

        this.controlSemaphore = new Semaphore(1);

        this.listeners = new ClockListener[4];
        this.numberOfListeners = 0;
    }
        /**
     * Obtiene el número del ciclo global actual.
     *
     * @return número del ciclo actual.
     */

    public long getCycle() {
        return cycle;
    }
        /**
     * Obtiene la duración configurada para cada ciclo del reloj.
     *
     * @return duración del ciclo en milisegundos.
     */

    public int getCycleDuration() {
        return cycleDuration;
    }
    /**
     * Modifica la duración de cada ciclo del reloj.
     *
     * El cambio solamente se realiza si el valor recibido
     * se encuentra entre 50 y 5000 milisegundos.
     * Utiliza el semáforo para proteger la modificación
     * del atributo.
     *
     * @param cycleDuration nueva duración del ciclo en milisegundos.
     */
    public void setCycleDuration(int cycleDuration) {
        if (cycleDuration >= 50 && cycleDuration <= 5000) {
            controlSemaphore.acquireUninterruptibly();

            try {
                this.cycleDuration = cycleDuration;
            } finally {
                controlSemaphore.release();
            }
        }
    }

    /**
     * Registra un componente para que reciba las notificaciones
     * de los ciclos del reloj global.
     *
     * Si el arreglo de listeners está lleno, aumenta su capacidad
     * al doble antes de almacenar el nuevo componente.
     * No permite registrar referencias null.
     *
     * @param listener componente que recibirá las notificaciones
     *                 de cada ciclo.
     */
    public void addListener(ClockListener listener) {
        if (listener == null) {
            return;
        }

        controlSemaphore.acquireUninterruptibly();

        try {
            if (numberOfListeners == listeners.length) {
                ClockListener[] newListeners =
                        new ClockListener[listeners.length * 2];

                for (int i = 0; i < numberOfListeners; i++) {
                    newListeners[i] = listeners[i];
                }

                listeners = newListeners;
            }

            listeners[numberOfListeners] = listener;
            numberOfListeners++;
        } finally {
            controlSemaphore.release();
        }
    }

    /**
     * Elimina un componente de la lista de listeners del reloj.
     *
     * Cuando encuentra el componente indicado, desplaza los elementos
     * siguientes una posición hacia la izquierda y actualiza
     * la cantidad de componentes registrados.
     *
     * @param listener componente que se desea eliminar.
     */
    public void removeListener(ClockListener listener) {
        controlSemaphore.acquireUninterruptibly();

        try {
            for (int i = 0; i < numberOfListeners; i++) {
                if (listeners[i] == listener) {
                    for (int j = i; j < numberOfListeners - 1; j++) {
                        listeners[j] = listeners[j + 1];
                    }

                    numberOfListeners--;
                    listeners[numberOfListeners] = null;
                    return;
                }
            }
        } finally {
            controlSemaphore.release();
        }
    }
    /**
     * Inicia la ejecución del reloj global.
     *
     * El hilo solamente se inicia si todavía no ha sido iniciado.
     * Al comenzar, establece el estado del reloj como activo
     * y despausado.
     */
    public void startClock() {
        controlSemaphore.acquireUninterruptibly();

        try {
            if (!started) {
                running = true;
                paused = false;
                started = true;
                start();
            }
        } finally {
            controlSemaphore.release();
        }
    }
    /**
     * Pausa temporalmente la generación de ciclos del reloj.
     *
     * El hilo permanece activo, pero deja de generar nuevos ciclos
     * hasta que se invoque resumeClock().
     */
    public void pauseClock() {
        controlSemaphore.acquireUninterruptibly();

        try {
            paused = true;
        } finally {
            controlSemaphore.release();
        }
    }
    /**
     * Reanuda la generación de ciclos después de una pausa.
     *
     * Cambia el estado del reloj para permitir que continúe
     * la ejecución de su hilo.
     */
    public void resumeClock() {
        controlSemaphore.acquireUninterruptibly();

        try {
            paused = false;
        } finally {
            controlSemaphore.release();
        }
    }
    /**
     * Detiene la ejecución del reloj global.
     *
     * Desactiva la generación de ciclos, elimina el estado de pausa
     * e interrumpe el hilo para permitir que finalice su ejecución.
     */
    public void stopClock() {
        controlSemaphore.acquireUninterruptibly();

        try {
            running = false;
            paused = false;
        } finally {
            controlSemaphore.release();
        }

        interrupt();
    }
    /**
     * Ejecuta el ciclo principal del hilo del reloj global.
     *
     * Mientras el reloj permanezca activo, espera la duración
     * configurada para cada ciclo. Si no está pausado, incrementa
     * el contador global y notifica a todos los componentes registrados.
     *
     * Antes de enviar las notificaciones, obtiene una copia de la lista
     * de listeners para evitar mantener el semáforo bloqueado durante
     * la ejecución de sus métodos onTick().
     *
     * Si un componente genera una excepción de tipo RuntimeException,
     * registra el error y continúa notificando a los demás componentes.
     *
     * @throws RuntimeException las excepciones de este tipo generadas
     *                          por los listeners son capturadas y
     *                          registradas individualmente.
     */
    @Override
    public void run() {
        while (running) {

            // Mientras esté pausado, espera brevemente sin usar wait().
            if (paused) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    if (!running) {
                        break;
                    }
                }

                continue;
            }

            try {
                Thread.sleep(cycleDuration);

                if (!running || paused) {
                    continue;
                }

                ClockListener[] listenersToNotify;
                int totalListeners;
                long cicloActual;

                controlSemaphore.acquire();

                try {
                    if (!running || paused) {
                        continue;
                    }

                    cycle++;
                    cicloActual = cycle;

                    totalListeners = numberOfListeners;
                    listenersToNotify =
                            new ClockListener[totalListeners];

                    for (int i = 0; i < totalListeners; i++) {
                        listenersToNotify[i] = listeners[i];
                    }
                } finally {
                    controlSemaphore.release();
                }

                System.out.println("Ciclo global: " + cicloActual);

                // Avisar a cada componente del nuevo ciclo.
                for (int i = 0; i < totalListeners; i++) {
                    try {
                        listenersToNotify[i].onTick(cicloActual);
                    } catch (RuntimeException e) {
                        System.err.println(
                                "Error al notificar el ciclo "
                                + cicloActual + ": " + e.getMessage()
                        );
                    }
                }

            } catch (InterruptedException e) {
                if (!running) {
                    break;
                }
            }
        }
    }
}