package avilaos.kernel;

import avilaos.kernel.memory.MemoryManager;
import avilaos.kernel.process.BlockReason;
import avilaos.kernel.process.PCB;
import avilaos.kernel.process.ProcessType;
import avilaos.kernel.process.TerminationCause;
import avilaos.structures.LinkedQueue;

/** Prueba manual del Kernel. Ejecutar con Run File. */
public class KernelPrueba {

    public static void main(String[] args) {
        MemoryManager ram = new MemoryManager(1000);
        Kernel kernel = new Kernel(1, ram);
        kernel.addEventListener((cycle, message) ->
                System.out.println("  [ciclo " + cycle + "] " + message));

        //                 nombre, computador, tipo, prioridad, memoria, deadline, instrucciones
        PCB p1 = new PCB("Editor", 1, ProcessType.CPU_BOUND, 3, 400, 50, 10);
        PCB p2 = new PCB("Navegador", 1, ProcessType.CPU_BOUND, 1, 500, 50, 10);
        PCB p3 = new PCB("Juego", 1, ProcessType.CPU_BOUND, 2, 300, 50, 10);
        PCB gigante = new PCB("Gigante", 1, ProcessType.CPU_BOUND, 1, 5000, 50, 10);

        System.out.println("--- 1. Llegan 3 procesos y uno imposible ---");
        kernel.submitProcess(p1, 1);
        kernel.submitProcess(p2, 1);
        kernel.submitProcess(p3, 1);
        System.out.println("submit(Gigante): " + kernel.submitProcess(gigante, 1) + " (esperado: false)");
        mostrar(kernel, "Nuevos: P1 P2 P3 | Listos: - | Bloqueados: - | Terminados: - | RAM usada: 0");

        System.out.println("--- 2. Admisión por memoria ---");
        System.out.println("Admitidos: " + kernel.admitNewProcesses(2) + " (esperado: 2)");
        mostrar(kernel, "Nuevos: P3 | Listos: P1 P2 | Bloqueados: - | Terminados: - | RAM usada: 900");

        System.out.println("--- 3. Terminar libera memoria y admite al que esperaba ---");
        kernel.terminateProcess(p1, TerminationCause.COMPLETED, 3);
        mostrar(kernel, "Nuevos: - | Listos: P2 P3 | Bloqueados: - | Terminados: P1 | RAM usada: 800");

        System.out.println("--- 4. Bloqueo por E/S de 2 ciclos ---");
        kernel.getReadyQueue().remove(p2); // (esto lo hará la CPU de Ale al ejecutarlo)
        kernel.blockProcess(p2, BlockReason.IO, 2, 4);
        mostrar(kernel, "Nuevos: - | Listos: P3 | Bloqueados: P2 | Terminados: P1 | RAM usada: 800");
        kernel.tickTimedBlocks(5);
        mostrar(kernel, "Nuevos: - | Listos: P3 | Bloqueados: P2 | Terminados: P1 | RAM usada: 800");
        kernel.tickTimedBlocks(6);
        mostrar(kernel, "Nuevos: - | Listos: P3 P2 | Bloqueados: - | Terminados: P1 | RAM usada: 800");

        System.out.println("--- 5. Deadline vencido ---");
        PCB urgente = new PCB("Urgente", 1, ProcessType.CPU_BOUND, 1, 100, 2, 10);
        kernel.submitProcess(urgente, 7);
        kernel.admitNewProcesses(7);
        kernel.tickDeadlines(8);
        kernel.tickDeadlines(9);
        System.out.println("Causa: " + urgente.getTerminationCause() + " (esperado: DEADLINE_MISSED)");
        mostrar(kernel, "Nuevos: - | Listos: P3 P2 | Bloqueados: - | Terminados: P1 P5 | RAM usada: 800");
    }

    private static void mostrar(Kernel k, String esperado) {
        System.out.println("Nuevos: " + ids(k.getNewQueue())
                + " | Listos: " + ids(k.getReadyQueue())
                + " | Bloqueados: " + ids(k.getBlockedQueue())
                + " | Terminados: " + ids(k.getTerminatedQueue())
                + " | RAM usada: " + k.getMemory().getUsedMemory());
        System.out.println("(esperado: " + esperado + ")");
    }

    private static String ids(LinkedQueue<PCB> cola) {
        Object[] elementos = cola.toArray();
        if (elementos.length == 0) {
            return "-";
        }
        String texto = "";
        for (Object o : elementos) {
            texto = texto + "P" + ((PCB) o).getId() + " ";
        }
        return texto.trim();
    }
}