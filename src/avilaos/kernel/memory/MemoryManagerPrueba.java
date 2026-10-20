package avilaos.kernel.memory;

/** Prueba manual de MemoryManager. Ejecutar con Run File. */
public class MemoryManagerPrueba {

    public static void main(String[] args) {
        MemoryManager ram = new MemoryManager(1000);

        System.out.println("allocate(300): " + ram.allocate(300) + " (esperado: true)");
        System.out.println("allocate(500): " + ram.allocate(500) + " (esperado: true)");
        System.out.println("allocate(250): " + ram.allocate(250) + " (esperado: false)");
        System.out.println("allocate(200): " + ram.allocate(200) + " (esperado: true)");
        ram.free(300);
        System.out.println("allocate(250): " + ram.allocate(250) + " (esperado: true)");
        System.out.println("allocate(0): " + ram.allocate(0) + " (esperado: false)");
        
        System.out.println("Usados: " + ram.getUsedMemory() + " (esperado: 950)");
        System.out.println("Libres: " + ram.getFreeMemory() + " (esperado: 50)");
        System.out.println("Porcentaje: " + ram.getUsagePercent() + " (esperado: 95)");
        System.out.println("fitsInTotal(1001): " + ram.fitsInTotal(1001) + " (esperado: false)");
    }
}