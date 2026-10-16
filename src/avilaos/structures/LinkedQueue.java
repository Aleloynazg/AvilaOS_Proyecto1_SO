package avilaos.structures;

/**
 * Cola propia (prohibido usar java.util). CONTRATO acordado en la Fase 0:
 * las firmas no se cambian sin avisar al equipo.
 *
 * TODO (Persona B): implementar con nodos enlazados.
 */
public class LinkedQueue<T> {

    /** Agrega al final. */
    public void enqueue(T item) {
        throw new UnsupportedOperationException("TODO");
    }

    /** Saca y devuelve el primero, o null si está vacía. */
    public T dequeue() {
        throw new UnsupportedOperationException("TODO");
    }

    /** Devuelve el primero sin sacarlo, o null si está vacía. */
    public T peek() {
        throw new UnsupportedOperationException("TODO");
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
        throw new UnsupportedOperationException("TODO");
    }

    public boolean isEmpty() {
        return size() == 0;
    }
}
