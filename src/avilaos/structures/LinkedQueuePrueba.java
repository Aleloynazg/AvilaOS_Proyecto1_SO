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
        
        System.out.println("--- 3. get ---");
        System.out.println("get(0): " + cola.get(0) + " (esperado: B)");
        System.out.println("get(1): " + cola.get(1) + " (esperado: C)");
        System.out.println("get(5): " + cola.get(5) + " (esperado: null)");

        System.out.println("--- 4. remove ---");
        cola.enqueue("D");
        System.out.println("remove(C): " + cola.remove("C") + " (esperado: true)");
        System.out.println("remove(Z): " + cola.remove("Z") + " (esperado: false)");
        System.out.println("Quedan: " + cola.get(0) + " " + cola.get(1) + " (esperado: B D)");
        cola.remove("D");
        cola.enqueue("E");
        System.out.println("Quedan: " + cola.get(0) + " " + cola.get(1) + " (esperado: B E)");
        
        System.out.println("--- 5. insertSorted (número menor primero) ---");
        LinkedQueue<Integer> prioridades = new LinkedQueue<>();
        ItemComparator<Integer> menorPrimero = (a, b) -> a - b;
        prioridades.insertSorted(5, menorPrimero);
        prioridades.insertSorted(2, menorPrimero);
        prioridades.insertSorted(8, menorPrimero);
        prioridades.insertSorted(1, menorPrimero);
        prioridades.insertSorted(5, menorPrimero);
        String texto = "";
        for (int i = 0; i < prioridades.size(); i++) {
            texto = texto + prioridades.get(i) + " ";
        }
        System.out.println("Cola: " + texto + "(esperado: 1 2 5 5 8)");
    }
}