package avilaos.hardware;

import avilaos.kernel.Kernel;
import avilaos.kernel.process.PCB;
import avilaos.kernel.sync.Buffer;
import avilaos.structures.LinkedQueue;
import java.util.concurrent.Semaphore;

/**
 * El clúster: todos los computadores, todos los buffers y la latencia de red.
 * Es el único lugar donde se puede buscar un computador o un buffer por su id.
 */
public class Cluster {

    public static final int MIN_COMPUTERS = 2;
    public static final int MAX_COMPUTERS = 4;

    private final Computer[] computers;
    private final LinkedQueue<Buffer> buffers = new LinkedQueue<>();
    private int networkLatency;

    /** Protege networkLatency (la GUI la cambia, el reloj la lee). */
    private final Semaphore lock = new Semaphore(1);

    /**
     * Crea un computador por cada tamaño de RAM. El primero es el H1, el segundo el H2...
     * Ejemplo: new Cluster(new int[] {1024, 512}, 3) crea H1 con 1024 MB y H2 con 512 MB.
     */
    public Cluster(int[] ramPerComputer, int networkLatency) {
        if (ramPerComputer == null
                || ramPerComputer.length < MIN_COMPUTERS
                || ramPerComputer.length > MAX_COMPUTERS) {
            throw new IllegalArgumentException(
                    "El clúster debe tener entre " + MIN_COMPUTERS + " y " + MAX_COMPUTERS + " computadores");
        }
        if (networkLatency < 0) {
            throw new IllegalArgumentException("La latencia de red no puede ser negativa");
        }
        this.computers = new Computer[ramPerComputer.length];
        for (int i = 0; i < ramPerComputer.length; i++) {
            computers[i] = new Computer(i + 1, ramPerComputer[i]);
        }
        this.networkLatency = networkLatency;
    }

    // ---------------- Computadores ----------------

    /** Devuelve el computador con ese id (H1 = 1), o null si no existe. */
    public Computer getComputer(int id) {
        if (id < 1 || id > computers.length) {
            return null;
        }
        return computers[id - 1];
    }

    /** Copia del arreglo: quien la recibe no puede cambiar el clúster. */
    public Computer[] getComputers() {
        Computer[] copia = new Computer[computers.length];
        for (int i = 0; i < computers.length; i++) {
            copia[i] = computers[i];
        }
        return copia;
    }

    public int getComputerCount() {
        return computers.length;
    }

    /** El Kernel del computador donde vive el proceso (para despertarlo o bloquearlo). */
    public Kernel getKernelOf(PCB process) {
        Computer c = getComputer(process.getComputerId());
        return c == null ? null : c.getKernel();
    }

    /** Conecta todos los computadores al reloj global. */
    public void connectClock(GlobalClock clock) {
        for (int i = 0; i < computers.length; i++) {
            computers[i].conectarReloj(clock);
        }
    }
    
    // ---------------- Buffers ----------------

    /**
     * Crea un buffer en el computador anfitrión y lo registra en el clúster.
     * Devuelve null si el anfitrión no tiene RAM libre suficiente.
     */
    public Buffer createBuffer(int capacity, int hostComputerId) {
        Computer host = getComputer(hostComputerId);
        if (host == null) {
            throw new IllegalArgumentException("No existe el computador H" + hostComputerId);
        }
        Buffer buffer = Buffer.create(capacity, hostComputerId, host.getMemoryManager());
        if (buffer != null) {
            buffers.enqueue(buffer);
        }
        return buffer;
    }

    /** Devuelve el buffer con ese id, o null si no existe. */
    public Buffer getBuffer(int id) {
        Object[] lista = buffers.toArray();
        for (int i = 0; i < lista.length; i++) {
            Buffer b = (Buffer) lista[i];
            if (b.getId() == id) {
                return b;
            }
        }
        return null;
    }

    /** Todos los buffers, en orden de creación (para la GUI). */
    public Object[] getBuffers() {
        return buffers.toArray();
    }

    public int getBufferCount() {
        return buffers.size();
    }
    
    
    // ---------------- Red ----------------

    public int getNetworkLatency() {
        lock.acquireUninterruptibly();
        try {
            return networkLatency;
        } finally {
            lock.release();
        }
    }

    public void setNetworkLatency(int latency) {
        if (latency < 0) {
            throw new IllegalArgumentException("La latencia de red no puede ser negativa");
        }
        lock.acquireUninterruptibly();
        try {
            networkLatency = latency;
        } finally {
            lock.release();
        }
    }

    /** ¿Este proceso accede a este buffer desde otro computador? */
    public boolean isRemoteAccess(PCB process, Buffer buffer) {
        return !buffer.isLocalTo(process.getComputerId());
    }
}