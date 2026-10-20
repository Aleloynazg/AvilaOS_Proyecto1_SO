package avilaos.structures;
import java.util.concurrent.Semaphore;

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
    private final Semaphore mutex = new Semaphore(1);  // Exclusión mutua: solo un hilo a la vez puede tocar la cola.

    /** Agrega al final. */
    public void enqueue(T item) {
        mutex.acquireUninterruptibly();
        try {
            Node<T> newNode = new Node<>(item);
            if (head == null) {
                head = newNode;
                tail = newNode;
            } else {
                tail.next = newNode;
                tail = newNode;
            }
            size++;
        } finally {
            mutex.release();
        }    
    }
    

    /** Saca y devuelve el primero, o null si está vacía. */
    public T dequeue() {
        mutex.acquireUninterruptibly();
        try {
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
         } finally {
            mutex.release();
        }
    }

    /** Devuelve el primero sin sacarlo, o null si está vacía. */
    public T peek() {
        mutex.acquireUninterruptibly();
        try {
            if (head == null) {
                return null;
            }
            return head.data;
        }finally {
            mutex.release();
            
        }
    }

    /** Devuelve el elemento en la posición i (0 = primero). */
    public T get(int index) {
        mutex.acquireUninterruptibly();
        try {
            if (index < 0 || index >= size) {
                return null;
            }
            Node<T> current = head;
            for (int i = 0; i < index; i++) {
                current = current.next;
            }
            return current.data;
        } finally {
            mutex.release();
        }    
    }

    /** Quita un elemento concreto (por ejemplo, un proceso cuyo deadline llegó a 0). */
    public boolean remove(T item) {
        mutex.acquireUninterruptibly();
        try {
            Node<T> previous = null;
            Node<T> current = head;
            while (current != null) {
                if (current.data == item) {
                    if (previous == null) {
                        head = current.next;
                    } else {
                        previous.next = current.next;
                    }
                    if (current == tail) {
                        tail = previous;
                    }
                    size--;
                    return true;
            
            }
            previous = current;
            current = current.next;
        }
        return false;
        
        } finally {
            mutex.release();
        }
        
      
    }

    /** Inserta respetando el orden dado por el comparador (para EDF y prioridades). */
    public void insertSorted(T item, ItemComparator<T> comparator) {
        mutex.acquireUninterruptibly();
        try {
            Node<T> newNode = new Node<>(item);
            Node<T> previous = null;
            Node<T> current = head;
            while (current != null && comparator.compare(item, current.data) >= 0) {
                previous = current;
                current = current.next;
            }
            newNode.next = current;
            if (previous == null) {
                head = newNode;
            } else {
                previous.next = newNode;
            }
            if (current == null) {
                tail = newNode;
            }
            size++;
          } finally {
            mutex.release();
        }
    }
    
     /** Copia de la cola en un arreglo normal (para que la GUI la dibuje). */
    public Object[] toArray() {
        mutex.acquireUninterruptibly();
        try {
            Object[] result = new Object[size];
            Node<T> current = head;
            int i = 0;
            while (current != null) {
                result[i] = current.data;
                i++;
                current = current.next;
            }
            return result;
         } finally {
            mutex.release();
        }
        
    }

    public int size() {
         mutex.acquireUninterruptibly();
        try {
            return size;
        } finally {
            mutex.release();
        }
    }

    public boolean isEmpty() {
        mutex.acquireUninterruptibly();
        try {
            return size == 0;
         } finally {
            mutex.release();
        }    
    }
}
