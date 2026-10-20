package avilaos.kernel.memory;

/**
 * Memoria principal (RAM) de un computador. No hace particiones: solo
 * lleva la cuenta de cuánta memoria está usada y cuánta libre.
 */
public class MemoryManager {
    
    private final int totalMemory;   // MB que tiene la RAM (no cambia)
    private int usedMemory;          // MB ocupados ahora

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
        if (usedMemory + amount > totalMemory) {
            return false;
        }
        usedMemory += amount;
        return true;
    }

    /** Libera memoria que se había reservado (cuando un proceso termina). */
    public void free(int amount) {
        if (amount <= 0) {
            return;
        }
        usedMemory -= amount;
        if (usedMemory < 0) {
            usedMemory = 0;
        }
    }

}