package avilaos.hardware;

import avilaos.hardware.disk.Disk;
import avilaos.kernel.Kernel;
import avilaos.kernel.disk.DiskPolicyType;
import avilaos.kernel.disk.DiskRequest;
import avilaos.kernel.fs.FileEntry;
import avilaos.kernel.fs.FileIoManager;
import avilaos.kernel.fs.FileSystem;
import avilaos.kernel.fs.FileSystemException;
import avilaos.kernel.fs.FsNode;
import avilaos.kernel.fs.NodeType;
import avilaos.kernel.fs.Permission;
import avilaos.kernel.memory.MemoryManager;
import avilaos.kernel.process.BlockReason;
import avilaos.kernel.process.IoOperation;
import avilaos.kernel.process.PCB;
import avilaos.kernel.process.ProcessFactory;
import avilaos.kernel.process.ProcessState;
import avilaos.kernel.process.TerminationCause;

/**
 * Prueba de integración de la F3.2: el computador, el kernel, el disco y el sistema de
 * archivos trabajando juntos con procesos de E/S de archivos. El reloj se maneja a mano
 * (un ciclo = Computer.onTick + Disk.onTick, en el mismo orden que el reloj real), así el
 * resultado no depende de hilos ni de tiempos. Termina con código 1 si algo falla.
 */
public class ComputerArchivosPrueba {

    private static int total;
    private static int fallos;

    private static long cycle;
    private static StringBuilder log;

    public static void main(String[] args) {
        leerArchivo();
        crearRenombrarYEliminar();
        operacionesInvalidas();
        revalidacionAlTerminar();
        deadlineCancelaSolicitud();
        createReservaYSeLibera();
        reservaConElGestorDeArchivos();
        procesoDeUnaInstruccion();
        cambioDePoliticaEnMarcha();
        modoUsuarioYKernelSinDisco();

        System.out.println();
        System.out.println(total + " comprobaciones, " + fallos + " fallos");
        if (fallos > 0) {
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------

    private static void leerArchivo() {
        System.out.println("--- 1. READ de un archivo: el proceso se bloquea, el disco lo atiende y despierta ---");
        Computer c = nuevoComputador();
        PCB p = ProcessFactory.fileIo("Lectura", 1, 5, 16, 500, 2, IoOperation.READ, "/sistema/boot.bin");
        c.getKernel().submitProcess(p, 0);

        boolean bloqueadoPorDisco = false;
        boolean cpuLibreMientrasEspera = false;
        while (cycle < 300 && p.getState() != ProcessState.TERMINATED) {
            step(c);
            if (p.getState() == ProcessState.BLOCKED && p.getBlockReason() == BlockReason.DISK_IO) {
                bloqueadoPorDisco = true;
                cpuLibreMientrasEspera = c.getCPU().isIdle();
            }
        }
        check("el proceso se bloqueó con motivo E/S del disco", bloqueadoPorDisco);
        check("la CPU quedó libre mientras esperaba", cpuLibreMientrasEspera);
        check("terminó COMPLETED", p.getTerminationCause() == TerminationCause.COMPLETED);
        check("el disco atendió 1 solicitud", c.getDisk().getCompletedRequests() == 1);
        check("el cabezal quedó en el bloque 10 (inicio de boot.bin)", c.getDisk().getCurrentHead() == 10);
        check("desplazamiento total 40 (de 50 a 10)", c.getDisk().getTotalHeadMovement() == 40);
        check("el log cuenta la ejecución en modo kernel",
                log.indexOf("Modo KERNEL: " + p.getTag() + " ejecuta DISK_REQUEST (READ /sistema/boot.bin)") >= 0);
        check("el log cuenta la solicitud (bloque 10, 8 bloques)",
                log.indexOf(p.getTag() + " pide al disco READ /sistema/boot.bin (bloque 10, 8 bloques)") >= 0);
        check("el log cuenta la decisión del disco con su política",
                log.indexOf("El disco atiende la solicitud de " + p.getTag()
                        + " (READ /sistema/boot.bin) en el bloque 10 (FIFO)") >= 0);
        check("el log cuenta que el disco terminó",
                log.indexOf("El disco termina la solicitud de " + p.getTag() + ": lee /sistema/boot.bin (8 bloques)") >= 0);
        check("READ no cambia el sistema de archivos", c.getFileSystem().findByPath("/sistema/boot.bin") != null);
        check("liberó su memoria", c.getMemoryManager().getUsedMemory() == 0);
    }

    private static void crearRenombrarYEliminar() {
        System.out.println("--- 2. CREATE, UPDATE y DELETE cambian el sistema de archivos al terminar el disco ---");
        Computer c = nuevoComputador();
        FileSystem fs = c.getFileSystem();
        int primerLibre = c.getDisk().getFirstFreeFileBlock();
        int libresAntes = c.getDisk().countFreeFileBlocks();

        PCB crear = ProcessFactory.fileIo("Crear", 1, 5, 16, 900, 2, IoOperation.CREATE, "/usuarios/tesis.pdf",
                null, NodeType.FILE, 7, "user1", Permission.PRIVATE);
        PCB renombrar = ProcessFactory.fileIo("Renombrar", 1, 5, 16, 900, 2, IoOperation.UPDATE,
                "/usuarios/notas.txt");
        PCB eliminar = ProcessFactory.fileIo("Eliminar", 1, 5, 16, 900, 2, IoOperation.DELETE,
                "/usuarios/img1.png");
        PCB carpeta = ProcessFactory.fileIo("Carpeta", 1, 5, 16, 900, 2, IoOperation.CREATE, "/usuarios/docs",
                null, NodeType.DIRECTORY, 0, "user1", Permission.PUBLIC);
        PCB[] todos = {crear, renombrar, eliminar, carpeta};
        for (PCB p : todos) {
            c.getKernel().submitProcess(p, 0);
        }
        while (cycle < 200 && crear.getState() != ProcessState.BLOCKED) {
            step(c);
        }
        check("CREATE reserva los bloques al generar la solicitud: el archivo ya está en el árbol y el disco aún no terminó",
                fs.findByPath("/usuarios/tesis.pdf") != null && c.getDisk().getCompletedRequests() == 0
                        && c.getDisk().countFreeFileBlocks() == libresAntes - 7);
        runUntilAllTerminated(c, todos, 2000);

        boolean todosCompleted = true;
        for (PCB p : todos) {
            todosCompleted &= p.getTerminationCause() == TerminationCause.COMPLETED;
        }
        check("los 4 procesos terminaron COMPLETED", todosCompleted);
        check("el disco atendió 4 solicitudes", c.getDisk().getCompletedRequests() == 4);

        FsNode tesis = fs.findByPath("/usuarios/tesis.pdf");
        check("tesis.pdf existe con 7 bloques", tesis instanceof FileEntry && tesis.getBlockCount() == 7);
        check("tesis.pdf empieza en el primer bloque libre", tesis != null && tesis.getFirstBlock() == primerLibre);
        check("tesis.pdf tiene el dueño y el permiso pedidos",
                tesis != null && "user1".equals(tesis.getOwner()) && tesis.getPermission() == Permission.PRIVATE);
        check("tesis.pdf recuerda qué proceso lo creó", tesis != null && crear.getTag().equals(tesis.getCreator()));

        check("notas.txt ya no existe con el nombre viejo", fs.findByPath("/usuarios/notas.txt") == null);
        FsNode notas = fs.findByPath("/usuarios/notas_v2.txt");
        check("notas_v2.txt existe (nombre elegido por el kernel) en los mismos bloques",
                notas != null && notas.getFirstBlock() == 40 && notas.getBlockCount() == 5);
        check("img1.png fue eliminado", fs.findByPath("/usuarios/img1.png") == null);
        check("los bloques libres cambian: +4 de img1 y -7 de tesis.pdf",
                c.getDisk().countFreeFileBlocks() == libresAntes + 4 - 7);
        FsNode docs = fs.findByPath("/usuarios/docs");
        check("el directorio docs existe", docs != null && docs.isDirectory());
        check("docs recuerda qué proceso lo creó", docs != null && carpeta.getTag().equals(docs.getCreator()));
    }

    private static void operacionesInvalidas() {
        System.out.println("--- 3. Operaciones que no se pueden hacer: el proceso termina con IO_FAILED sin tocar el disco ---");
        Computer c = nuevoComputador();
        PCB noExiste = ProcessFactory.fileIo("NoExiste", 1, 5, 16, 500, 2, IoOperation.READ, "/usuarios/nada.txt");
        PCB yaExiste = ProcessFactory.fileIo("YaExiste", 1, 5, 16, 500, 2, IoOperation.CREATE, "/usuarios/data.csv",
                null, NodeType.FILE, 2, "admin", Permission.PUBLIC);
        PCB sinEspacio = ProcessFactory.fileIo("SinEspacio", 1, 5, 16, 500, 2, IoOperation.CREATE,
                "/usuarios/enorme.bin", null, NodeType.FILE, 500, "admin", Permission.PUBLIC);
        PCB nombreRepetido = ProcessFactory.fileIo("Repetido", 1, 5, 16, 500, 2, IoOperation.UPDATE,
                "/usuarios/notas.txt", "data.csv", NodeType.FILE, 0, null, null);
        PCB sinCarpeta = ProcessFactory.fileIo("SinCarpeta", 1, 5, 16, 500, 2, IoOperation.CREATE,
                "/noexiste/a.txt", null, NodeType.FILE, 2, "admin", Permission.PUBLIC);
        PCB[] todos = {noExiste, yaExiste, sinEspacio, nombreRepetido, sinCarpeta};
        for (PCB p : todos) {
            c.getKernel().submitProcess(p, 0);
        }
        runUntilAllTerminated(c, todos, 300);

        boolean todosFallaron = true;
        for (PCB p : todos) {
            todosFallaron &= p.getTerminationCause() == TerminationCause.IO_FAILED;
        }
        check("los 5 procesos terminaron IO_FAILED", todosFallaron);
        check("el disco no atendió ninguna solicitud", c.getDisk().getCompletedRequests() == 0
                && !c.getDiskScheduler().hasRequests());
        check("motivo: la ruta no existe", log.indexOf(noExiste.getTag()
                + " no puede hacer READ /usuarios/nada.txt: La ruta no existe: /usuarios/nada.txt") >= 0);
        check("motivo: ya existe", log.indexOf(yaExiste.getTag() + " no puede hacer CREATE /usuarios/data.csv: Ya existe") >= 0);
        check("motivo: no hay bloques libres", log.indexOf(sinEspacio.getTag()
                + " no puede hacer CREATE /usuarios/enorme.bin: No hay bloques libres suficientes") >= 0);
        check("motivo: nombre repetido", log.indexOf(nombreRepetido.getTag()
                + " no puede hacer UPDATE /usuarios/notas.txt: Ya existe 'data.csv'") >= 0);
        check("motivo: la carpeta no existe", log.indexOf(sinCarpeta.getTag()
                + " no puede hacer CREATE /noexiste/a.txt: La carpeta /noexiste no existe") >= 0);
        check("el log dice que terminó con error de E/S", log.indexOf(noExiste.getTag() + " termina con error de E/S") >= 0);
        check("el sistema de archivos no cambió", c.getFileSystem().countFiles() == 5
                && c.getFileSystem().getUsedBlocks() == 33);
        check("liberaron su memoria", c.getMemoryManager().getUsedMemory() == 0);
    }

    private static void revalidacionAlTerminar() {
        System.out.println("--- 4. La operación se revisa otra vez al terminar el disco ---");
        Computer c = nuevoComputador();
        PCB eliminar = ProcessFactory.fileIo("Elimina", 1, 5, 16, 900, 2, IoOperation.DELETE, "/usuarios/img1.png");
        PCB leer = ProcessFactory.fileIo("Lee", 1, 5, 16, 900, 2, IoOperation.READ, "/usuarios/img1.png");
        PCB renombrar = ProcessFactory.fileIo("Renombra", 1, 5, 16, 900, 2, IoOperation.UPDATE,
                "/usuarios/img1.png", "otra.png", NodeType.FILE, 0, null, null);
        PCB[] todos = {eliminar, leer, renombrar};
        for (PCB p : todos) {
            c.getKernel().submitProcess(p, 0);
        }
        runUntilAllTerminated(c, todos, 1000);

        check("el primero (DELETE) completa", eliminar.getTerminationCause() == TerminationCause.COMPLETED);
        check("el que lee falla porque el archivo ya no existe", leer.getTerminationCause() == TerminationCause.IO_FAILED);
        check("el que renombra también falla", renombrar.getTerminationCause() == TerminationCause.IO_FAILED);
        check("el log explica la falla", log.indexOf("La operación de " + leer.getTag()
                + " (READ /usuarios/img1.png) falla: La ruta no existe") >= 0);
        check("el disco sí atendió las 3 solicitudes", c.getDisk().getCompletedRequests() == 3);
        check("no quedó ningún proceso bloqueado", c.getKernel().getBlockedQueue().isEmpty());
    }

    private static void deadlineCancelaSolicitud() {
        System.out.println("--- 5. Si el proceso muere por deadline, su solicitud en cola se cancela ---");
        Computer c = nuevoComputador();
        PCB lento = ProcessFactory.fileIo("Lento", 1, 5, 16, 900, 2, IoOperation.READ, "/usuarios/video.mp4");
        PCB impaciente = ProcessFactory.fileIo("Impaciente", 1, 5, 16, 40, 2, IoOperation.DELETE,
                "/usuarios/notas.txt");
        c.getKernel().submitProcess(lento, 0);
        c.getKernel().submitProcess(impaciente, 0);
        runUntilAllTerminated(c, new PCB[] {lento, impaciente}, 1000);

        check("el impaciente murió por deadline", impaciente.getTerminationCause() == TerminationCause.DEADLINE_MISSED);
        check("el lento completó", lento.getTerminationCause() == TerminationCause.COMPLETED);
        check("el log cuenta la cancelación", log.indexOf("se cancela la solicitud de " + impaciente.getTag()
                + " en la cola del disco") >= 0);
        check("la cola del disco quedó vacía y solo se atendió 1 solicitud",
                !c.getDiskScheduler().hasRequests() && c.getDisk().getCompletedRequests() == 1);
        check("notas.txt sigue existiendo (el DELETE nunca se hizo)",
                c.getFileSystem().findByPath("/usuarios/notas.txt") != null);
    }

    private static void createReservaYSeLibera() {
        System.out.println("--- 5b. CREATE reserva bloques distintos; si el proceso muere, se liberan ---");
        Computer c = nuevoComputador();
        int libresAntes = c.getDisk().countFreeFileBlocks();
        PCB lento = ProcessFactory.fileIo("Lento", 1, 5, 16, 900, 2, IoOperation.READ, "/usuarios/video.mp4");
        PCB a = ProcessFactory.fileIo("A", 1, 5, 16, 900, 2, IoOperation.CREATE, "/usuarios/a.bin",
                null, NodeType.FILE, 3, "admin", Permission.PUBLIC);
        PCB b = ProcessFactory.fileIo("B", 1, 5, 16, 900, 2, IoOperation.CREATE, "/usuarios/b.bin",
                null, NodeType.FILE, 3, "admin", Permission.PUBLIC);
        PCB impaciente = ProcessFactory.fileIo("Impaciente", 1, 5, 16, 40, 2, IoOperation.CREATE,
                "/usuarios/c.bin", null, NodeType.FILE, 20, "admin", Permission.PUBLIC);
        PCB[] todos = {lento, a, b, impaciente};
        for (PCB p : todos) {
            c.getKernel().submitProcess(p, 0);
        }
        while (cycle < 100 && impaciente.getState() != ProcessState.BLOCKED) {
            step(c);
        }
        Object[] enCola = c.getDiskScheduler().getRequests();
        boolean distintos = enCola.length == 3
                && ((DiskRequest) enCola[0]).getTargetBlock() == 0
                && ((DiskRequest) enCola[1]).getTargetBlock() == 3
                && ((DiskRequest) enCola[2]).getTargetBlock() == 6;
        check("las 3 solicitudes CREATE en cola apuntan a bloques distintos (0, 3 y 6)", distintos);
        check("los 26 bloques ya están reservados", c.getDisk().countFreeFileBlocks() == libresAntes - 26);

        runUntilAllTerminated(c, todos, 1000);
        check("el impaciente murió por deadline", impaciente.getTerminationCause() == TerminationCause.DEADLINE_MISSED);
        check("su archivo se eliminó y sus 20 bloques quedaron libres",
                c.getFileSystem().findByPath("/usuarios/c.bin") == null
                        && c.getDisk().countFreeFileBlocks() == libresAntes - 6);
        check("el log cuenta que se liberaron los bloques reservados",
                log.indexOf("se liberan los 20 bloques reservados para /usuarios/c.bin") >= 0);
        check("A y B completaron y sus archivos están en los bloques reservados",
                a.getTerminationCause() == TerminationCause.COMPLETED
                        && b.getTerminationCause() == TerminationCause.COMPLETED
                        && c.getFileSystem().findByPath("/usuarios/a.bin").getFirstBlock() == 0
                        && c.getFileSystem().findByPath("/usuarios/b.bin").getFirstBlock() == 3);
    }

    private static void reservaConElGestorDeArchivos() {
        System.out.println("--- 5c. FileIoManager: reservar, confirmar y liberar ---");
        FileSystem fs = new FileSystem(new Disk(200, 100));
        fs.createDirectory("/d", "admin", Permission.PUBLIC);
        FileIoManager io = new FileIoManager(fs);
        int libres = fs.getDisk().countFreeFileBlocks();

        PCB p = ProcessFactory.fileIo("X", 1, 5, 16, 900, 2, IoOperation.CREATE, "/d/x.bin",
                null, NodeType.FILE, 5, "admin", Permission.PUBLIC);
        DiskRequest r = io.buildRequest(p, 1);
        check("buildRequest reserva el archivo: está en el árbol, empieza en el bloque 0 y pide 5 bloques",
                r.getReservedNode() != null && fs.findByPath("/d/x.bin") == r.getReservedNode()
                        && r.getTargetBlock() == 0 && r.getBlockCount() == 5
                        && fs.getDisk().countFreeFileBlocks() == libres - 5);
        fs.delete("/d/x.bin");   // otro proceso lo elimina mientras la solicitud espera
        check("si lo eliminaron mientras esperaba, apply falla", falla(() -> io.apply(r), "ya no existe"));
        check("y release no libera nada (ya estaba libre)", io.release(r) == 0
                && fs.getDisk().countFreeFileBlocks() == libres);

        PCB q = ProcessFactory.fileIo("Y", 1, 5, 16, 900, 2, IoOperation.CREATE, "/d/y.bin",
                null, NodeType.FILE, 4, "admin", Permission.PUBLIC);
        DiskRequest r2 = io.buildRequest(q, 2);
        check("release de una solicitud cancelada elimina el archivo y libera sus 4 bloques",
                io.release(r2) == 4 && fs.findByPath("/d/y.bin") == null
                        && fs.getDisk().countFreeFileBlocks() == libres);

        PCB s = ProcessFactory.fileIo("Z", 1, 5, 16, 900, 2, IoOperation.CREATE, "/d/z.bin",
                null, NodeType.FILE, 2, "admin", Permission.PUBLIC);
        DiskRequest r3 = io.buildRequest(s, 3);
        fs.delete("/d");         // se elimina la carpeta que contenía el archivo reservado
        check("si eliminaron la carpeta que lo contenía, apply falla y release no libera nada",
                falla(() -> io.apply(r3), "ya no existe") && io.release(r3) == 0
                        && fs.getDisk().countFreeFileBlocks() == libres);

        PCB t = ProcessFactory.fileIo("W", 1, 5, 16, 900, 2, IoOperation.CREATE, "/d2/w.bin",
                null, NodeType.FILE, 2, "admin", Permission.PUBLIC);
        check("sin carpeta padre, buildRequest falla y no reserva nada",
                falla(() -> io.buildRequest(t, 4), "no existe") && fs.getDisk().countFreeFileBlocks() == libres);
    }

    private static boolean falla(Runnable action, String fragmento) {
        try {
            action.run();
        } catch (FileSystemException e) {
            return e.getMessage().contains(fragmento);
        }
        return false;
    }

    private static void procesoDeUnaInstruccion() {
        System.out.println("--- 6. Un proceso de una sola instrucción termina al despertar ---");
        Computer c = nuevoComputador();
        PCB p = ProcessFactory.fileIo("Una", 1, 5, 16, 500, 1, IoOperation.DELETE, "/usuarios/img1.png");
        c.getKernel().submitProcess(p, 0);
        runUntilAllTerminated(c, new PCB[] {p}, 400);
        check("terminó COMPLETED", p.getTerminationCause() == TerminationCause.COMPLETED);
        check("img1.png fue eliminado", c.getFileSystem().findByPath("/usuarios/img1.png") == null);
        check("liberó su memoria", c.getMemoryManager().getUsedMemory() == 0);
    }

    private static void cambioDePoliticaEnMarcha() {
        System.out.println("--- 7. Cambiar la política de disco con el sistema en marcha ---");
        // Tres lecturas que llegan en este orden: video (170), boot (10), data (100).
        // Con el cabezal en 170 al terminar la primera: FIFO sigue con boot; SSTF con data (más cerca).
        Computer fifo = nuevoComputador();
        PCB[] a = lecturasDeLaPrueba(fifo);
        runUntilAllTerminated(fifo, a, 1500);
        int bootAntesQueDataFifo = Integer.compare(log.indexOf(atiende(a[1])), log.indexOf(atiende(a[2])));
        check("con FIFO se atiende boot.bin antes que data.csv", bootAntesQueDataFifo < 0);

        Computer sstf = nuevoComputador();
        check("empieza en FIFO", sstf.getDiskPolicyType() == DiskPolicyType.FIFO);
        PCB[] b = lecturasDeLaPrueba(sstf);
        for (int i = 0; i < 6; i++) {
            step(sstf);
        }
        sstf.setDiskPolicy(DiskPolicyType.SSTF);
        check("la política cambió a SSTF", sstf.getDiskPolicyType() == DiskPolicyType.SSTF);
        check("el log registra el cambio de política", log.indexOf("La política de disco cambia de FIFO a SSTF") >= 0);
        runUntilAllTerminated(sstf, b, 1500);
        boolean dataAntesQueBoot = log.indexOf(atiende(b[2])) < log.indexOf(atiende(b[1]));
        check("tras cambiar a SSTF se atiende data.csv antes que boot.bin", dataAntesQueBoot);
        check("las 3 lecturas completaron", b[0].getTerminationCause() == TerminationCause.COMPLETED
                && b[1].getTerminationCause() == TerminationCause.COMPLETED
                && b[2].getTerminationCause() == TerminationCause.COMPLETED);
        check("el log dice (SSTF) en las decisiones posteriores", log.indexOf("en el bloque 100 (SSTF)") >= 0);
    }

    private static void modoUsuarioYKernelSinDisco() {
        System.out.println("--- 8. Modo usuario y kernel sin disco ---");
        Computer c = nuevoComputador();
        check("en modo administrador se permite eliminar",
                c.checkFileOperation(IoOperation.DELETE, "/usuarios/notas.txt", null) == null);
        c.getAccessControl().setUserMode("user2");
        check("user2 no puede leer el archivo privado de user1",
                c.checkFileOperation(IoOperation.READ, "/usuarios/notas.txt", null) != null);
        check("user2 sí puede leer un archivo público",
                c.checkFileOperation(IoOperation.READ, "/usuarios/data.csv", null) == null);
        check("user2 no puede crear directorios",
                c.checkFileOperation(IoOperation.CREATE, "/usuarios/x", NodeType.DIRECTORY) != null);

        Kernel kernel = new Kernel(1, new MemoryManager(1000));
        PCB p = ProcessFactory.fileIo("SinDisco", 1, 5, 16, 500, 2, IoOperation.READ, "/a");
        kernel.submitProcess(p, 0);
        kernel.admitNewProcesses(1);
        kernel.getReadyQueue().remove(p);
        p.setState(ProcessState.RUNNING);
        boolean ok = kernel.submitFileRequest(p, 2);
        check("sin disco la solicitud falla", !ok && p.getTerminationCause() == TerminationCause.IO_FAILED);
        check("y libera la memoria", kernel.getMemory().getUsedMemory() == 0);
    }

    // ------------------------------------------------------------------
    // Armado y ayudas
    // ------------------------------------------------------------------

    /** Computador de 1024 de RAM con un disco de 200 + 100 bloques y unos archivos de prueba. */
    private static Computer nuevoComputador() {
        cycle = 0;
        log = new StringBuilder();
        Disk disk = new Disk(200, 100);
        disk.setCurrentHead(50);
        Computer c = new Computer(1, 1024, disk);
        c.addEventListener((ciclo, mensaje) -> log.append(mensaje).append('\n'));

        FileSystem fs = c.getFileSystem();
        fs.createDirectory("/sistema", "admin", Permission.PUBLIC);
        fs.createDirectory("/usuarios", "admin", Permission.PUBLIC);
        fs.loadFile("/sistema/boot.bin", 10, 8, "admin", Permission.PUBLIC, FsNode.LOADED_FROM_JSON);
        fs.loadFile("/usuarios/notas.txt", 40, 5, "user1", Permission.PRIVATE, FsNode.LOADED_FROM_JSON);
        fs.loadFile("/usuarios/data.csv", 100, 6, "user2", Permission.PUBLIC, FsNode.LOADED_FROM_JSON);
        fs.loadFile("/usuarios/img1.png", 150, 4, "user1", Permission.PRIVATE, FsNode.LOADED_FROM_JSON);
        fs.loadFile("/usuarios/video.mp4", 170, 10, "user1", Permission.PUBLIC, FsNode.LOADED_FROM_JSON);
        return c;
    }

    private static PCB[] lecturasDeLaPrueba(Computer c) {
        PCB video = ProcessFactory.fileIo("LeeVideo", 1, 5, 16, 900, 2, IoOperation.READ, "/usuarios/video.mp4");
        PCB boot = ProcessFactory.fileIo("LeeBoot", 1, 5, 16, 900, 2, IoOperation.READ, "/sistema/boot.bin");
        PCB data = ProcessFactory.fileIo("LeeData", 1, 5, 16, 900, 2, IoOperation.READ, "/usuarios/data.csv");
        c.getKernel().submitProcess(video, 0);
        c.getKernel().submitProcess(boot, 0);
        c.getKernel().submitProcess(data, 0);
        return new PCB[] {video, boot, data};
    }

    private static String atiende(PCB p) {
        return "El disco atiende la solicitud de " + p.getTag();
    }

    /** Un ciclo de reloj: primero el computador y después el disco, como el reloj real. */
    private static void step(Computer c) {
        cycle++;
        c.onTick(cycle);
        c.getDisk().onTick(cycle);
    }

    private static void runUntilAllTerminated(Computer c, PCB[] processes, int maxCycles) {
        long limit = cycle + maxCycles;
        while (cycle < limit) {
            boolean all = true;
            for (PCB p : processes) {
                all &= p.getState() == ProcessState.TERMINATED;
            }
            if (all) {
                return;
            }
            step(c);
        }
    }

    private static void check(String name, boolean ok) {
        total++;
        if (!ok) {
            fallos++;
        }
        System.out.println((ok ? "  OK   " : "  FALLA ") + name);
        if (!ok && log != null) {
            System.out.println("    ---- log ----");
            System.out.println("    " + log.toString().replace("\n", "\n    "));
        }
    }
}
