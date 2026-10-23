package avilaos.kernel.sync;

import avilaos.kernel.Kernel;
import avilaos.kernel.memory.MemoryManager;
import avilaos.kernel.process.BufferSemaphore;
import avilaos.kernel.process.PCB;
import avilaos.kernel.process.ProcessFactory;

/** Prueba manual de AvilaSemaphore. Ejecutar con Run File. */
public class AvilaSemaphorePrueba {

    public static void main(String[] args) {
        PCB a = ProcessFactory.cpuBound("A", 1, 1, 100, 50, 5);
        PCB b = ProcessFactory.cpuBound("B", 1, 1, 100, 50, 5);
        PCB c = ProcessFactory.cpuBound("C", 1, 1, 100, 50, 5);

        System.out.println("--- 1. Mutex (s = 1): solo uno entra ---");
        AvilaSemaphore s = new AvilaSemaphore("B1.s", BufferSemaphore.MUTEX, 1);
        System.out.println("A hace semWait -> bloqueado? " + s.semWait(a) + " (esperado: false) | " + s);
        System.out.println("B hace semWait -> bloqueado? " + s.semWait(b) + " (esperado: true) | " + s);
        System.out.println("C hace semWait -> bloqueado? " + s.semWait(c) + " (esperado: true) | " + s);
        System.out.println("Esperando: " + nombres(s) + " (esperado: B C) | valor -2 = 2 procesos esperando");

        System.out.println("--- 2. semSignal despierta en orden de llegada (FIFO) ---");
        System.out.println("semSignal despierta a: " + nombre(s.semSignal()) + " (esperado: B) | " + s);
        System.out.println("semSignal despierta a: " + nombre(s.semSignal()) + " (esperado: C) | " + s);
        System.out.println("semSignal despierta a: " + nombre(s.semSignal()) + " (esperado: nadie) | " + s);

        System.out.println("--- 3. Contador: llenos (n = 0) ---");
        AvilaSemaphore n = new AvilaSemaphore("B1.n", BufferSemaphore.FULL, 0);
        System.out.println("semSignal (un productor agrega) -> despierta a: " + nombre(n.semSignal()) + " (esperado: nadie) | " + n);
        System.out.println("semSignal (otro elemento) -> despierta a: " + nombre(n.semSignal()) + " (esperado: nadie) | " + n);
        System.out.println("A hace semWait -> bloqueado? " + n.semWait(a) + " (esperado: false) | " + n);
        System.out.println("B hace semWait -> bloqueado? " + n.semWait(b) + " (esperado: false) | " + n);
        System.out.println("C hace semWait -> bloqueado? " + n.semWait(c) + " (esperado: true) | " + n);

        System.out.println("--- 4. cancelWait (a C se le vence el deadline esperando) ---");
        System.out.println("cancelWait(C): " + n.cancelWait(c) + " (esperado: true) | " + n + " (esperado: 0)");
        System.out.println("Esperando: " + nombres(n) + " (esperado: -)");

        System.out.println("--- 5. Con el Kernel: bloquear y despertar con el motivo correcto ---");
        Kernel kernel = new Kernel(1, new MemoryManager(1000));
        kernel.addEventListener((ciclo, msg) -> System.out.println("  [" + ciclo + "] " + msg));
        PCB productor = ProcessFactory.producer("Productor", 1, 1, 100, 50, 1, 1, 1);
        kernel.submitProcess(productor, 1);
        kernel.admitNewProcesses(1);
        kernel.getReadyQueue().remove(productor);   // (esto lo hace la CPU al ejecutarlo)

        AvilaSemaphore e = new AvilaSemaphore("B1.e", BufferSemaphore.EMPTY, 0);   // buffer lleno
        if (e.semWait(productor)) {
            kernel.blockProcess(productor, e.getBlockReason(), 0, 2);
        }
        System.out.println("Estado: " + productor.getState() + " | motivo: " + productor.getBlockReason()
                + " (esperado: BLOCKED | SEM_EMPTY)");
        PCB despertado = e.semSignal();             // un consumidor saca un elemento
        if (despertado != null) {
            kernel.unblockProcess(despertado, 3);
        }
        System.out.println("Estado: " + productor.getState() + " (esperado: READY) | " + e);

        System.out.println("--- 6. Valor inicial inválido ---");
        try {
            new AvilaSemaphore("X", BufferSemaphore.MUTEX, -1);
            System.out.println("No lanzó error (MAL)");
        } catch (IllegalArgumentException ex) {
            System.out.println("Error esperado: " + ex.getMessage());
        }
    }

    private static String nombre(PCB p) {
        return p == null ? "nadie" : p.getName();
    }

    private static String nombres(AvilaSemaphore sem) {
        Object[] lista = sem.getWaitingProcesses();
        if (lista.length == 0) {
            return "-";
        }
        String texto = "";
        for (Object o : lista) {
            texto += ((PCB) o).getName() + " ";
        }
        return texto.trim();
    }
}