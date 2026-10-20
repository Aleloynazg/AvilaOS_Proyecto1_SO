package avilaos.kernel.memory;

import java.util.concurrent.Semaphore;

/**
 * Memoria principal (RAM) de un computador. No hace particiones: solo
 * lleva la cuenta de cuánta memoria está usada y cuánta libre.
 * Es segura para hilos: un semáforo de Java protege los contadores.
 */
public class MemoryManager {

    private final int totalMemory;   // MB que tiene la RAM (no cambia)
    private int usedMemory;          // MB ocupados ahora
    private final Semaphore mutex = new Semaphore(1);   //Exclusión mutua: solo un hilo a la vez puede tocar los contadores
   

    public MemoryManager(int totalMemory) {
        if (totalMemory <= 0) {
            throw new IllegalArgumentException("La RAM debe ser mayor que 0");
        }
        this.totalMemory = totalMemory;
    }

    /**
     * Intenta reservar memoria. Si hay espacio, la reserva y devuelve true.
     * Si no hay espacio, no reserva nada y devuelve false.
     */
    public boolean allocate(int amount) {
        if (amount <= 0) {
            return false;
        }
        mutex.acquireUninterruptibly();
        try {
            if (usedMemory + amount > totalMemory) {
                return false;
            }
            usedMemory += amount;
            return true;
        } finally {
            mutex.release();
        }
    }

    /** Libera memoria que se había reservado (cuando un proceso termina). */
    public void free(int amount) {
        if (amount <= 0) {
            return;
        }
        mutex.acquireUninterruptibly();
        try {
            usedMemory -= amount;
            if (usedMemory < 0) {
                usedMemory = 0;
            }
        } finally {
            mutex.release();
        }
    }

    /** ¿Este tamaño podría caber alguna vez, aunque la RAM estuviera vacía? */
    public boolean fitsInTotal(int amount) {
        return amount > 0 && amount <= totalMemory;
    }

    public int getTotalMemory() {
        return totalMemory;
    }

    public int getUsedMemory() {
        mutex.acquireUninterruptibly();
        try {
            return usedMemory;
        } finally {
            mutex.release();
        }
    }

    public int getFreeMemory() {
        mutex.acquireUninterruptibly();
        try {
            return totalMemory - usedMemory;
        } finally {
            mutex.release();
        }
    }

    /** Porcentaje usado, de 0 a 100 (para la barra de la GUI). */
    public int getUsagePercent() {
        mutex.acquireUninterruptibly();
        try {
            return usedMemory * 100 / totalMemory;
        } finally {
            mutex.release();
        }
    }
}