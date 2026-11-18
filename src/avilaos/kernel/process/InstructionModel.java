package avilaos.kernel.process;

/**
 * Dice qué instrucción ejecuta un proceso en este ciclo, según su tipo.
 * No guarda estado propio: la posición sale del program counter del PCB
 * (el PC cuenta cuántas instrucciones ya ejecutó el proceso).
 */
public final class InstructionModel {

    private InstructionModel() { }

    /** La próxima instrucción que ejecutará el proceso. */
    public static Instruction next(PCB p) {
        int n = p.getProgramCounter();   // índice de la instrucción que toca (0 = la primera)
        switch (p.getType()) {
            case IO_BOUND:
                return nextIoBound(p, n);
            default:
                return new Instruction(InstructionType.NORMAL);
        }
    }

    /** Cada K instrucciones pide E/S (nunca en la última instrucción). */
    private static Instruction nextIoBound(PCB p, int n) {
        int every = p.getIoEveryCycles();
        int number = n + 1;                       // 1 = primera instrucción
        boolean isLast = number >= p.getTotalInstructions();
        if (every > 0 && number % every == 0 && !isLast) {
            return new Instruction(InstructionType.IO_REQUEST);
        }
        return new Instruction(InstructionType.NORMAL);
    }
}
