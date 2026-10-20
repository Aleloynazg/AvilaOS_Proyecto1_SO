package avilaos.structures;

/** Prueba manual de LinkedQueue. Ejecutar con Run File (Shift + F6). */
public class LinkedQueuePrueba {

    public static void main(String[] args) {
        LinkedQueue<String> cola = new LinkedQueue<>();

        System.out.println("--- 1. Cola vacía ---");
        System.out.println("¿Vacía? " + cola.isEmpty() + " (esperado: true)");
        System.out.println("dequeue: " + cola.dequeue() + " (esperado: null)");

        System.out.println("--- 2. enqueue y dequeue ---");
        cola.enqueue("A");
        cola.enqueue("B");
        cola.enqueue("C");
        System.out.println("Tamaño: " + cola.size() + " (esperado: 3)");
        System.out.println("peek: " + cola.peek() + " (esperado: A)");
        System.out.println("dequeue: " + cola.dequeue() + " (esperado: A)");
        System.out.println("Tamaño: " + cola.size() + " (esperado: 2)");
    }
}