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

}