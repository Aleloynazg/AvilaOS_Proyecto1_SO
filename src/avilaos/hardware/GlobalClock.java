/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.hardware;

/**
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

    public long getCycle() {
        return cycle;
    }

    public int getCycleDuration() {
        return cycleDuration;
    }

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

    // Registra un componente que recibirá los ciclos.
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

    // Elimina un componente registrado.
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

    public void pauseClock() {
        controlSemaphore.acquireUninterruptibly();

        try {
            paused = true;
        } finally {
            controlSemaphore.release();
        }
    }

    public void resumeClock() {
        controlSemaphore.acquireUninterruptibly();

        try {
            paused = false;
        } finally {
            controlSemaphore.release();
        }
    }

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