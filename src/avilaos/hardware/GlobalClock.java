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
            this.cycleDuration = cycleDuration;
        }
    }

    // Registra un componente que recibirá los ciclos.
    public synchronized void addListener(ClockListener listener) {
        if (listener == null) {
            return;
        }

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
    }

    // Elimina un componente registrado.
    public synchronized void removeListener(ClockListener listener) {
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
    }

    public synchronized void startClock() {
        if (!started) {
            running = true;
            paused = false;
            started = true;
            start();
        }
    }

    public synchronized void pauseClock() {
        paused = true;
    }

    public synchronized void resumeClock() {
        paused = false;
        notifyAll();
    }

    public synchronized void stopClock() {
        running = false;
        paused = false;
        notifyAll();
        interrupt();
    }

    @Override
    public void run() {
        while (running) {

            synchronized (this) {
                while (paused && running) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        if (!running) {
                            return;
                        }
                    }
                }
            }

            if (!running) {
                break;
            }

            try {
                Thread.sleep(cycleDuration);

                if (!running || paused) {
                    continue;
                }

                controlSemaphore.acquire();

                try {
                    cycle++;
                } finally {
                    controlSemaphore.release();
                }

                System.out.println("Ciclo global: " + cycle);

                // Avisar a cada componente del nuevo ciclo.
                ClockListener[] listenersToNotify;
                int totalListeners;

                synchronized (this) {
                    totalListeners = numberOfListeners;
                    listenersToNotify =
                            new ClockListener[totalListeners];

                    for (int i = 0; i < totalListeners; i++) {
                        listenersToNotify[i] = listeners[i];
                    }
                }

                for (int i = 0; i < totalListeners; i++) {
                    try {
                        listenersToNotify[i].onTick(cycle);
                    } catch (RuntimeException e) {
                        System.err.println(
                                "Error al notificar el ciclo "
                                + cycle + ": " + e.getMessage()
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