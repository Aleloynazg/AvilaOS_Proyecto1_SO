package avilaos.kernel.sync;

import avilaos.kernel.memory.MemoryManager;
import avilaos.kernel.process.BufferSemaphore;
import avilaos.kernel.process.IdGenerator;
import java.util.concurrent.Semaphore;

/**
 * Buffer de capacidad limitada para productores y consumidores.
 * Vive en la RAM de un computador (su anfitrión) y tiene los 3 semáforos
 * del algoritmo de Stallings: s (mutex), n (llenos) y e (vacíos).
 */
public class Buffer {

    /** Memoria que ocupa: 1 MB por cada elemento de capacidad (DECISIONES.md). */
    public static final int MB_PER_SLOT = 1;

    private final int id;
    private final int hostComputerId;
    private final int capacity;

    private int count = 0;       // elementos que hay ahora
    private int produced = 0;    // total de elementos agregados
    private int consumed = 0;    // total de elementos sacados

    private final AvilaSemaphore mutex;   // s = 1
    private final AvilaSemaphore full;    // n = 0
    private final AvilaSemaphore empty;   // e = capacidad

    /** Protege count, produced y consumed (hilo del reloj y hilo de la GUI). */
    private final Semaphore lock = new Semaphore(1);

    private Buffer(int id, int hostComputerId, int capacity) {
        this.id = id;
        this.hostComputerId = hostComputerId;
        this.capacity = capacity;
        this.mutex = new AvilaSemaphore(getTag() + ".s", BufferSemaphore.MUTEX, 1);
        this.full = new AvilaSemaphore(getTag() + ".n", BufferSemaphore.FULL, 0);
        this.empty = new AvilaSemaphore(getTag() + ".e", BufferSemaphore.EMPTY, capacity);
    }

    /**
     * Crea un buffer y reserva su memoria en el computador anfitrión.
     * Devuelve null si el anfitrión no tiene RAM libre suficiente.
     */
    public static Buffer create(int capacity, int hostComputerId, MemoryManager hostMemory) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("La capacidad del buffer debe ser mayor que 0");
        }
        if (hostMemory == null) {
            throw new IllegalArgumentException("Falta la memoria del computador anfitrión");
        }
        if (!hostMemory.allocate(capacity * MB_PER_SLOT)) {
            return null;
        }
        return new Buffer(IdGenerator.nextBufferId(), hostComputerId, capacity);
    }
    
     /** append: agrega un elemento. Antes hay que haber pasado semWait(e) y semWait(s). */
    public void append() {
        lock.acquireUninterruptibly();
        try {
            if (count == capacity) {
                throw new IllegalStateException(getTag() + " está lleno: falta semWait(e) antes de append");
            }
            count++;
            produced++;
        } finally {
            lock.release();
        }
    }

    /** take: saca un elemento. Antes hay que haber pasado semWait(n) y semWait(s). */
    public void take() {
        lock.acquireUninterruptibly();
        try {
            if (count == 0) {
                throw new IllegalStateException(getTag() + " está vacío: falta semWait(n) antes de take");
            }
            count--;
            consumed++;
        } finally {
            lock.release();
        }
    }

    /** Devuelve el semáforo que indica una instrucción SEM_WAIT(x) o SEM_SIGNAL(x). */
    public AvilaSemaphore getSemaphore(BufferSemaphore role) {
        switch (role) {
            case MUTEX: return mutex;
            case FULL:  return full;
            case EMPTY: return empty;
            default:    throw new IllegalArgumentException("Semáforo desconocido: " + role);
        }
    }

    public int getId() { return id; }
    public String getTag() { return "B" + id; }
}

