package avilaos.kernel.memory;

/** Prueba manual de MemoryManager. Ejecutar con Run File. */
public class MemoryManagerPrueba {

    public static void main(String[] args) {
        MemoryManager ram = new MemoryManager(1000);

        System.out.println("--- 1. RAM nueva ---");
        mostrar(ram, "0 usados, 1000 libres, 0%");

        System.out.println("--- 2. Reservar ---");
        System.out.println("allocate(300): " + ram.allocate(300) + " (esperado: true)");
        System.out.println("allocate(500): " + ram.allocate(500) + " (esperado: true)");
        mostrar(ram, "800 usados, 200 libres, 80%");

        System.out.println("--- 3. No cabe ---");
        System.out.println("allocate(250): " + ram.allocate(250) + " (esperado: false)");
        mostrar(ram, "800 usados, 200 libres, 80%");
        System.out.println("allocate(200): " + ram.allocate(200) + " (esperado: true)");
        mostrar(ram, "1000 usados, 0 libres, 100%");

        System.out.println("--- 4. Liberar ---");
        ram.free(300);
        mostrar(ram, "700 usados, 300 libres, 70%");
        System.out.println("allocate(250): " + ram.allocate(250) + " (esperado: true)");

        System.out.println("--- 5. Valores inválidos ---");
        System.out.println("allocate(0): " + ram.allocate(0) + " (esperado: false)");
        System.out.println("allocate(-5): " + ram.allocate(-5) + " (esperado: false)");
        System.out.println("fitsInTotal(1000): " + ram.fitsInTotal(1000) + " (esperado: true)");
        System.out.println("fitsInTotal(1001): " + ram.fitsInTotal(1001) + " (esperado: false)");
        ram.free(5000);
        mostrar(ram, "0 usados, 1000 libres, 0%");
    }

    private static void mostrar(MemoryManager ram, String esperado) {
        System.out.println("RAM: " + ram.getUsedMemory() + " usados, "
                + ram.getFreeMemory() + " libres, " + ram.getUsagePercent()
                + "% (esperado: " + esperado + ")");
    }
}
