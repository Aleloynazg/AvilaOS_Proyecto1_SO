package avilaos.structures;

/**
 * Cola propia (prohibido usar java.util). CONTRATO acordado en la Fase 0:
 * las firmas no se cambian sin avisar al equipo.
 *
 * TODO (Persona B): implementar con nodos enlazados.
 */
public class LinkedQueue<T> {
     /** Una "cajita" de la cola: guarda un dato y apunta a la siguiente cajita. */
    private static class Node<T> {
        T data;
        Node<T> next;

        Node(T data) {
            this.data = data;
        }
    }

    private Node<T> head;   // primer nodo (el que sale primero)
    private Node<T> tail;   // último nodo (donde entran los nuevos)
    private int size;       // cuántos elementos hay

    /** Agrega al final. */
    public void enqueue(T item) {
        Node<T> newNode = new Node<>(item);
        if (head == null) {
            head = newNode;
            tail = newNode;
        } else {
            tail.next = newNode;
            tail = newNode;
        }
        size++;
    }
    

    /** Saca y devuelve el primero, o null si está vacía. */
    public T dequeue() {
        if (head == null) {
            return null;
        }
        T data = head.data;
        head = head.next;
        if (head == null) {
            tail = null;
        }
        size--;
        return data;
    }

    /** Devuelve el primero sin sacarlo, o null si está vacía. */
    public T peek() {
        if (head == null) {
            return null;
        }
        return head.data;
    }

    /** Devuelve el elemento en la posición i (0 = primero). */
    public T get(int index) {
        throw new UnsupportedOperationException("TODO");
    }

    /** Quita un elemento concreto (por ejemplo, un proceso cuyo deadline llegó a 0). */
    public boolean remove(T item) {
        throw new UnsupportedOperationException("TODO");
    }

    /** Inserta respetando el orden dado por el comparador (para EDF y prioridades). */
    public void insertSorted(T item, ItemComparator<T> comparator) {
        throw new UnsupportedOperationException("TODO");
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }
}
