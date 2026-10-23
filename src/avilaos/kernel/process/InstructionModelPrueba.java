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

        System.out.println("--- 4. Productor: 2 elementos, 2 ciclos por elemento ---");
        PCB prod = ProcessFactory.producer("Productor", 1, 1, 100, 80, 1, 2, 2);
        System.out.println("Total de instrucciones: " + prod.getTotalInstructions() + " (esperado: 14)");
        recorrer(prod, "PRODUCE PRODUCE SEM_WAIT(e) SEM_WAIT(s) APPEND SEM_SIGNAL(s) SEM_SIGNAL(n) "
                + "PRODUCE PRODUCE SEM_WAIT(e) SEM_WAIT(s) APPEND SEM_SIGNAL(s) SEM_SIGNAL(n)");

        System.out.println("--- 5. Consumidor: 1 elemento, 1 ciclo por elemento ---");
        recorrer(ProcessFactory.consumer("Consumidor", 1, 1, 100, 80, 1, 1, 1),
                "SEM_WAIT(n) SEM_WAIT(s) TAKE SEM_SIGNAL(s) SEM_SIGNAL(e) CONSUME");

        System.out.println("--- 6. Modo de ejecución ---");
        Instruction w = new Instruction(InstructionType.SEM_WAIT, BufferSemaphore.EMPTY);
        System.out.println("SEM_WAIT en modo SO: " + w.isKernelMode() + " (esperado: true)");
        System.out.println("Motivo si bloquea: " + w.getSemaphore().getBlockReason() + " (esperado: SEM_EMPTY)");
        System.out.println("PRODUCE en modo SO: " + new Instruction(InstructionType.PRODUCE).isKernelMode()
                + " (esperado: false)");

        System.out.println("--- 7. Valores inválidos ---");
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