package avilaos.kernel.sync;

import avilaos.kernel.memory.MemoryManager;
import avilaos.kernel.process.BufferSemaphore;
import avilaos.kernel.process.IdGenerator;
import avilaos.kernel.process.PCB;
import avilaos.kernel.process.ProcessFactory;

/** Prueba manual de Buffer. Ejecutar con Run File. */
public class BufferPrueba {

    public static void main(String[] args) {
        IdGenerator.reset();

        System.out.println("--- 1. Crear un buffer reserva RAM en su anfitrión ---");
        MemoryManager ram = new MemoryManager(1000);
        Buffer b = Buffer.create(5, 1, ram);
        System.out.println(b + "   (esperado: B1 (H1) [0/5] s=1 n=0 e=5)");
        System.out.println("RAM usada: " + ram.getUsedMemory() + " (esperado: 5)");
        System.out.println("¿Local a H1? " + b.isLocalTo(1) + " | ¿local a H2? " + b.isLocalTo(2)
                + "   (esperado: true | false)");

        System.out.println("--- 2. getSemaphore según la instrucción ---");
        System.out.println(b.getSemaphore(BufferSemaphore.MUTEX).getName() + " "
                + b.getSemaphore(BufferSemaphore.FULL).getName() + " "
                + b.getSemaphore(BufferSemaphore.EMPTY).getName()
                + "   (esperado: B1.s B1.n B1.e)");

        System.out.println("--- 3. Un productor agrega 2 elementos (protocolo completo) ---");
        PCB prod = ProcessFactory.producer("Prod", 1, 1, 50, 500, b.getId(), 2, 2);
        for (int i = 0; i < 2; i++) {
            b.getEmpty().semWait(prod);
            b.getMutex().semWait(prod);
            b.append();
            b.getMutex().semSignal();
            b.getFull().semSignal();
        }
        System.out.println(b + "   (esperado: B1 (H1) [2/5] s=1 n=2 e=3)");

        System.out.println("--- 4. Un consumidor saca 1 elemento ---");
        PCB cons = ProcessFactory.consumer("Cons", 2, 1, 50, 500, b.getId(), 2, 1);
        b.getFull().semWait(cons);
        b.getMutex().semWait(cons);
        b.take();
        b.getMutex().semSignal();
        b.getEmpty().semSignal();
        System.out.println(b + "   (esperado: B1 (H1) [1/5] s=1 n=1 e=4)");
        System.out.println("Producidos: " + b.getProduced() + " | consumidos: " + b.getConsumed()
                + "   (esperado: 2 | 1)");

        System.out.println("--- 5. Consumidor con el buffer vacío se bloquea en n ---");
        b.getFull().semWait(cons);   // saca el que quedaba: n = 0
        b.getMutex().semWait(cons);
        b.take();
        b.getMutex().semSignal();
        b.getEmpty().semSignal();
        boolean bloqueado = b.getFull().semWait(cons);
        System.out.println("¿Bloqueado? " + bloqueado + " | " + b
                + "   (esperado: true | B1 (H1) [0/5] s=1 n=-1 e=5)");

        System.out.println("--- 6. Errores ---");
        try {
            b.take();
        } catch (IllegalStateException e) {
            System.out.println("Error esperado: " + e.getMessage());
        }
        Buffer chico = Buffer.create(2, 1, new MemoryManager(1000));
        chico.append();
        chico.append();
        try {
            chico.append();
        } catch (IllegalStateException e) {
            System.out.println("Error esperado: " + e.getMessage());
        }
        try {
            Buffer.create(0, 1, ram);
        } catch (IllegalArgumentException e) {
            System.out.println("Error esperado: " + e.getMessage());
        }
        Buffer sinRam = Buffer.create(50, 1, new MemoryManager(10));
        System.out.println("Sin RAM suficiente: " + sinRam + "   (esperado: null)");
    }
}