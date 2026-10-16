package avilaos.kernel.process;

import java.util.concurrent.Semaphore;

/**
 * Genera IDs únicos en todo el clúster. Varios hilos (computadores, GUI)
 * pueden crear procesos a la vez, así que el contador se protege con un
 * semáforo de Java.
 */
public final class IdGenerator {

    private static final Semaphore MUTEX = new Semaphore(1);
    private static int nextProcessId = 1;
    private static int nextBufferId = 1;

    private IdGenerator() { }

    public static int nextProcessId() {
        MUTEX.acquireUninterruptibly();
        try {
            return nextProcessId++;
        } finally {
            MUTEX.release();
        }
    }

    public static int nextBufferId() {
        MUTEX.acquireUninterruptibly();
        try {
            return nextBufferId++;
        } finally {
            MUTEX.release();
        }
    }

    /** Solo para reiniciar una simulación nueva. */
    public static void reset() {
        MUTEX.acquireUninterruptibly();
        try {
            nextProcessId = 1;
            nextBufferId = 1;
        } finally {
            MUTEX.release();
        }
    }
}
