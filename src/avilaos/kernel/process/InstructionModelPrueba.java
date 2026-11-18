package avilaos.kernel.process;

/** Prueba manual del modelo de instrucciones. Ejecutar con Run File. */
public class InstructionModelPrueba {

    public static void main(String[] args) {
        System.out.println("--- 1. CPU bound, 4 instrucciones ---");
        recorrer(ProcessFactory.cpuBound("Calculo", 1, 1, 100, 50, 4),
                "NORMAL NORMAL NORMAL NORMAL");

        System.out.println("--- 2. I/O bound, 7 instrucciones, E/S cada 3 ---");
        recorrer(ProcessFactory.ioBound("Lector", 1, 1, 100, 50, 7, 3, 2),
                "NORMAL NORMAL IO_REQUEST NORMAL NORMAL IO_REQUEST NORMAL");

        System.out.println("--- 3. I/O bound, 6 instrucciones, E/S cada 3 (la última nunca es E/S) ---");
        recorrer(ProcessFactory.ioBound("Lector2", 1, 1, 100, 50, 6, 3, 2),
                "NORMAL NORMAL IO_REQUEST NORMAL NORMAL NORMAL");

        System.out.println("--- 4. Modo de ejecución ---");
        System.out.println("NORMAL en modo SO: " + new Instruction(InstructionType.NORMAL).isKernelMode()
                + " (esperado: false)");

        System.out.println("--- 5. Valores inválidos ---");
        try {
            ProcessFactory.ioBound("Malo", 1, 1, 100, 50, 5, 0, 2);
            System.out.println("No lanzó error (MAL)");
        } catch (IllegalArgumentException e) {
            System.out.println("Error esperado: " + e.getMessage());
        }
    }

    /** Pide la instrucción, la "ejecuta" (avanza el PC) y repite hasta terminar. */
    private static void recorrer(PCB p, String esperado) {
        StringBuilder texto = new StringBuilder();
        while (!p.isFinished()) {
            texto.append(InstructionModel.next(p)).append(" ");
            p.advance();
        }
        System.out.println("Obtenido: " + texto.toString().trim());
        System.out.println("Esperado: " + esperado);
        System.out.println("¿Igual? " + texto.toString().trim().equals(esperado));
    }
}