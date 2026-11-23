package avilaos.kernel.fs;

import avilaos.config.ConfigStore;
import avilaos.config.FileSystemConfig;
import avilaos.hardware.disk.Disk;
import avilaos.kernel.process.IoOperation;
import java.io.File;
import java.io.IOException;

/**
 * Prueba del sistema de archivos (F2.1, F2.2 y F2.5): crear, renombrar y eliminar
 * archivos y directorios sobre el disco, con asignación encadenada. Las secciones 8 y 9
 * usan el JSON del enunciado (config/p2/sistema_archivos_enunciado.json, que llena los
 * 200 bloques del disco), así que hay que ejecutarla desde la carpeta del proyecto.
 *
 * Se ejecuta como programa normal. Termina con código 1 si algo falla.
 */
public class FileSystemPrueba {

    private static final String ENUNCIADO = "config/p2/sistema_archivos_enunciado.json";

    private static int fallos = 0;
    private static int comprobaciones = 0;

    public static void main(String[] args) throws IOException {
        arbolYCreacion();
        asignacionEncadenada();
        renombrar();
        eliminarRecursivo();
        discoLleno();
        archivosDelJson();
        recorridoYColores();
        operacionesConElJsonDelEnunciado();
        permisosConElJsonDelEnunciado();

        System.out.println();
        System.out.println("Comprobaciones: " + comprobaciones + " | Fallos: " + fallos);
        if (fallos > 0) {
            System.exit(1);
        }
    }

    private static void arbolYCreacion() {
        System.out.println("--- 1. Árbol y creación ---");
        FileSystem fs = new FileSystem(new Disk(50, 0));
        verificar("La raíz existe y es '/'", fs.findByPath("/") == fs.getRoot()
                && fs.pathOf(fs.getRoot()).equals("/"));
        verificar("Sistema vacío: 0 archivos y 0 directorios",
                fs.countFiles() == 0 && fs.countDirectories() == 0);

        fs.createDirectory("/sistema", "admin", Permission.PUBLIC);
        fs.createDirectory("/sistema/bin", "admin", Permission.PRIVATE);
        verificar("Directorios anidados", fs.findByPath("/sistema/bin") instanceof Directory
                && fs.countDirectories() == 2);
        verificar("pathOf devuelve la ruta completa",
                fs.pathOf(fs.findByPath("/sistema/bin")).equals("/sistema/bin"));
        verificar("El permiso y el dueño se guardan",
                fs.findByPath("/sistema/bin").getPermission() == Permission.PRIVATE
                && fs.findByPath("/sistema/bin").getOwner().equals("admin"));

        esperaError("Crear en una carpeta que no existe", "no existe",
                () -> fs.createDirectory("/nada/x", "admin", Permission.PUBLIC));
        esperaError("Crear un nombre repetido", "Ya existe",
                () -> fs.createDirectory("/sistema", "admin", Permission.PUBLIC));
        esperaError("Ruta sin barra inicial", "empezar con /",
                () -> fs.createDirectory("sistema2", "admin", Permission.PUBLIC));
        esperaError("Ruta que termina en /", "terminar en /",
                () -> fs.createDirectory("/otra/", "admin", Permission.PUBLIC));

        fs.createFile("/sistema/a.txt", 2, "admin", Permission.PUBLIC, "P1");
        esperaError("Crear dentro de un archivo", "es un archivo",
                () -> fs.createFile("/sistema/a.txt/b.txt", 1, "admin", Permission.PUBLIC, "P2"));
        esperaError("Archivo de 0 bloques", "al menos 1 bloque",
                () -> fs.createFile("/sistema/cero.txt", 0, "admin", Permission.PUBLIC, "P2"));
        verificar("Los errores no dejan nada creado", fs.findByPath("/sistema/cero.txt") == null
                && fs.countFiles() == 1);
    }

    private static void asignacionEncadenada() {
        System.out.println("--- 2. Asignación encadenada ---");
        Disk disk = new Disk(10, 0);
        FileSystem fs = new FileSystem(disk);
        FileEntry a = fs.createFile("/a.txt", 3, "admin", Permission.PUBLIC, "P1");
        FileEntry b = fs.createFile("/b.txt", 2, "admin", Permission.PUBLIC, "P2");
        verificar("a.txt empieza en el 0 y b.txt en el 3",
                a.getStartBlock() == 0 && b.getStartBlock() == 3);
        verificar("a.txt: cadena 0 -> 1 -> 2", cadena(disk, a).equals("0 1 2"));
        verificar("Bloques libres: 5", disk.countFreeFileBlocks() == 5 && fs.getUsedBlocks() == 5);

        int liberados = fs.delete("/a.txt");
        verificar("Eliminar a.txt libera 3 bloques", liberados == 3 && disk.countFreeFileBlocks() == 8);

        FileEntry c = fs.createFile("/c.txt", 4, "admin", Permission.PUBLIC, "P3");
        verificar("c.txt reutiliza los huecos: cadena 0 -> 1 -> 2 -> 5", cadena(disk, c).equals("0 1 2 5"));
        verificar("Cada bloque de la cadena apunta al archivo",
                disk.getBlock(5).getFile() == c && disk.getBlock(3).getFile() == b);
        verificar("El último bloque de la cadena no tiene siguiente", disk.getBlock(5).getNext() == -1);
        verificar("El tamaño y el primer bloque del archivo", c.getBlockCount() == 4 && c.getFirstBlock() == 0);
    }

    private static void renombrar() {
        System.out.println("--- 3. Renombrar ---");
        FileSystem fs = new FileSystem(new Disk(20, 0));
        fs.createDirectory("/docs", "admin", Permission.PUBLIC);
        fs.createFile("/docs/notas.txt", 2, "user1", Permission.PRIVATE, "P1");
        fs.createFile("/docs/otro.txt", 1, "user1", Permission.PRIVATE, "P2");
        int primerBloque = fs.findByPath("/docs/notas.txt").getFirstBlock();

        fs.rename("/docs/notas.txt", "notas_v2.txt");
        verificar("El nombre nuevo se encuentra y el viejo ya no",
                fs.findByPath("/docs/notas_v2.txt") != null && fs.findByPath("/docs/notas.txt") == null);
        verificar("Renombrar no cambia bloques, dueño ni permiso",
                fs.findByPath("/docs/notas_v2.txt").getFirstBlock() == primerBloque
                && fs.findByPath("/docs/notas_v2.txt").getOwner().equals("user1")
                && fs.findByPath("/docs/notas_v2.txt").getPermission() == Permission.PRIVATE);

        fs.rename("/docs", "documentos");
        verificar("Renombrar un directorio mueve su contenido",
                fs.findByPath("/documentos/notas_v2.txt") != null && fs.findByPath("/docs") == null);

        esperaError("Renombrar a un nombre que ya existe", "Ya existe",
                () -> fs.rename("/documentos/notas_v2.txt", "otro.txt"));
        esperaError("Nombre con /", "no puede contener /",
                () -> fs.rename("/documentos/otro.txt", "a/b"));
        esperaError("Nombre vacío", "vacío", () -> fs.rename("/documentos/otro.txt", "  "));
        esperaError("Nombre demasiado largo", "demasiado largo",
                () -> fs.rename("/documentos/otro.txt", "x".repeat(FileSystem.MAX_NAME_LENGTH + 1)));
        esperaError("Ruta inexistente", "no existe", () -> fs.rename("/nada", "algo"));
        esperaError("Renombrar la raíz", "raíz", () -> fs.rename("/", "algo"));
        verificar("Los rechazos no cambiaron nada", fs.findByPath("/documentos/otro.txt") != null);
    }

    private static void eliminarRecursivo() {
        System.out.println("--- 4. Eliminar (directorios en forma recursiva) ---");
        Disk disk = new Disk(30, 0);
        FileSystem fs = new FileSystem(disk);
        fs.createDirectory("/sistema", "admin", Permission.PUBLIC);
        fs.createDirectory("/sistema/lib", "admin", Permission.PUBLIC);
        fs.createFile("/sistema/a.bin", 4, "admin", Permission.PUBLIC, "P1");
        fs.createFile("/sistema/lib/b.so", 6, "admin", Permission.PUBLIC, "P1");
        fs.createFile("/fuera.txt", 3, "admin", Permission.PUBLIC, "P2");
        verificar("Antes: 13 bloques ocupados", fs.getUsedBlocks() == 13);

        int liberados = fs.delete("/sistema");
        verificar("Eliminar /sistema libera 10 bloques de sus archivos", liberados == 10);
        verificar("Quedan solo los bloques de fuera.txt", fs.getUsedBlocks() == 3
                && disk.countFreeFileBlocks() == 27);
        verificar("Ya no existe nada de lo que contenía",
                fs.findByPath("/sistema") == null && fs.findByPath("/sistema/lib/b.so") == null);
        verificar("Los bloques liberados no apuntan a ningún archivo",
                disk.getBlock(0).getFile() == null && disk.getBlock(9).getFile() == null);
        verificar("Lo que estaba fuera queda intacto", fs.findByPath("/fuera.txt") != null
                && fs.countFiles() == 1 && fs.countDirectories() == 0);

        esperaError("Eliminar una ruta inexistente", "no existe", () -> fs.delete("/sistema"));
        esperaError("Eliminar la raíz", "raíz", () -> fs.delete("/"));
    }

    private static void discoLleno() {
        System.out.println("--- 5. Disco lleno ---");
        Disk disk = new Disk(10, 5);
        FileSystem fs = new FileSystem(disk);
        fs.createFile("/a", 6, "admin", Permission.PUBLIC, "P1");
        fs.createFile("/b", 4, "admin", Permission.PUBLIC, "P2");
        verificar("Disco de 10 bloques lleno (el swap no cuenta)", disk.countFreeFileBlocks() == 0
                && disk.countFreeSwapBlocks() == 5);
        esperaError("Crear con el disco lleno", "No hay bloques libres suficientes",
                () -> fs.createFile("/c", 1, "admin", Permission.PUBLIC, "P3"));
        verificar("El rechazo no deja archivo ni bloques tomados",
                fs.findByPath("/c") == null && fs.countFiles() == 2 && disk.countFreeSwapBlocks() == 5);

        fs.delete("/b");
        esperaError("Pedir más de lo que hay libre (5 de 4)", "necesita 5 y quedan 4",
                () -> fs.createFile("/d", 5, "admin", Permission.PUBLIC, "P3"));
        verificar("El estado sigue igual tras el rechazo", disk.countFreeFileBlocks() == 4 && fs.countFiles() == 1);
        fs.createFile("/d", 4, "admin", Permission.PUBLIC, "P3");
        verificar("Con 4 bloques libres, un archivo de 4 sí cabe", disk.countFreeFileBlocks() == 0);
    }

    private static void archivosDelJson() {
        System.out.println("--- 6. Archivos en bloques fijos (como el JSON del enunciado) ---");
        Disk disk = new Disk(200, 0);
        FileSystem fs = new FileSystem(disk);
        fs.createDirectory("/sistema", "admin", Permission.PUBLIC);
        FileEntry mbr = fs.loadFile("/sistema/mbr.bin", 0, 11, "admin", Permission.PUBLIC, FsNode.LOADED_FROM_JSON);
        FileEntry boot = fs.loadFile("/sistema/boot.bin", 11, 23, "admin", Permission.PUBLIC, FsNode.LOADED_FROM_JSON);
        verificar("mbr.bin ocupa los bloques 0 a 10", mbr.getStartBlock() == 0 && mbr.getBlockCount() == 11
                && cadena(disk, mbr).equals("0 1 2 3 4 5 6 7 8 9 10"));
        verificar("boot.bin empieza en el 11", boot.getStartBlock() == 11 && disk.getBlock(33).getFile() == boot);
        verificar("Cuentan como creados por el JSON", boot.getCreator().equals(FsNode.LOADED_FROM_JSON));

        esperaError("Bloques que pisan a otro archivo", "ya están ocupados",
                () -> fs.loadFile("/sistema/x.bin", 30, 10, "admin", Permission.PUBLIC, "JSON"));
        esperaError("Bloques fuera de la zona de archivos", "fuera de la zona",
                () -> fs.loadFile("/sistema/y.bin", 195, 10, "admin", Permission.PUBLIC, "JSON"));
        esperaError("Bloque inicial negativo", "fuera de la zona",
                () -> fs.loadFile("/sistema/z.bin", -1, 3, "admin", Permission.PUBLIC, "JSON"));
        verificar("Los rechazos no tocaron el disco", disk.countFreeFileBlocks() == 200 - 34
                && fs.countFiles() == 2);
        esperaError("Ruta cuyo padre no existe", "no existe",
                () -> fs.loadFile("/otro/w.bin", 100, 2, "admin", Permission.PUBLIC, "JSON"));
    }

    private static void recorridoYColores() {
        System.out.println("--- 7. Recorrido, bloques de directorios y colores ---");
        FileSystem fs = new FileSystem(new Disk(40, 0));
        Directory vacio = fs.createDirectory("/vacio", "admin", Permission.PUBLIC);
        Directory carpeta = fs.createDirectory("/carpeta", "admin", Permission.PUBLIC);
        FileEntry uno = fs.createFile("/carpeta/uno", 5, "admin", Permission.PUBLIC, "P1");
        FileEntry dos = fs.createFile("/carpeta/dos", 7, "admin", Permission.PUBLIC, "P1");

        verificar("Un directorio vacío no tiene primer bloque", vacio.getFirstBlock() == -1
                && vacio.getBlockCount() == 0);
        verificar("El primer bloque de un directorio es el de su primer archivo",
                carpeta.getFirstBlock() == uno.getStartBlock());
        verificar("El tamaño de un directorio suma sus archivos", carpeta.getBlockCount() == 12);

        FsNode[] todos = fs.getAllNodes();
        verificar("getAllNodes devuelve 4 elementos en profundidad",
                todos.length == 4 && todos[0] == vacio && todos[1] == carpeta
                && todos[2] == uno && todos[3] == dos);
        verificar("Cada elemento tiene un color distinto",
                vacio.getColorIndex() != carpeta.getColorIndex()
                && uno.getColorIndex() != dos.getColorIndex()
                && carpeta.getColorIndex() != uno.getColorIndex());
    }

    /** Sistema de archivos del enunciado ya cargado: 9 archivos, 2 directorios, disco lleno. */
    private static FileSystem cargarEnunciado(Disk disk) throws IOException {
        FileSystemConfig config = ConfigStore.loadFileSystem(new File(ENUNCIADO));
        FileSystem fs = new FileSystem(disk);
        config.populate(fs);
        return fs;
    }

    private static void operacionesConElJsonDelEnunciado() throws IOException {
        System.out.println("--- 8. Operaciones sobre el JSON del enunciado ---");
        Disk disk = new Disk(200, 100);
        FileSystem fs = cargarEnunciado(disk);
        verificar("Punto de partida: 9 archivos, 2 directorios y el disco lleno",
                fs.countFiles() == 9 && fs.countDirectories() == 2 && disk.countFreeFileBlocks() == 0);

        // El disco del ejemplo está lleno: crear siempre falla hasta liberar espacio.
        esperaError("CREATE con el disco lleno", "necesita 1 y quedan 0",
                () -> fs.createFile("/sistema/nuevo.bin", 1, "admin", Permission.PUBLIC, "Proc_new"));

        // Borrar img1.png (bloques 119 a 122) y reusar ese espacio.
        verificar("DELETE de img1.png libera sus 4 bloques", fs.delete("/usuarios/img1.png") == 4
                && disk.countFreeFileBlocks() == 4);
        FileEntry nuevo = fs.createFile("/usuarios/nuevo.dat", 4, "user1", Permission.PUBLIC, "Proc_new");
        verificar("El archivo nuevo ocupa justo los bloques liberados (119 a 122)",
                nuevo.getStartBlock() == 119 && disk.countFreeFileBlocks() == 0);
        esperaError("Otro CREATE vuelve a fallar", "quedan 0",
                () -> fs.createFile("/usuarios/otro.dat", 1, "user1", Permission.PUBLIC, "Proc_new"));

        // Renombrar no mueve bloques.
        fs.rename("/usuarios/notas.txt", "notas_v2.txt");
        FileEntry notas = (FileEntry) fs.findByPath("/usuarios/notas_v2.txt");
        verificar("UPDATE (renombrar) notas.txt: sigue en el bloque 34 con 28 bloques",
                notas != null && notas.getStartBlock() == 34 && notas.getBlockCount() == 28
                && fs.findByPath("/usuarios/notas.txt") == null);

        // Directorio recursivo: /sistema tiene boot.bin (23), script.py (2), core.dll (31) y config.sys (24).
        int liberados = fs.delete("/sistema");
        verificar("DELETE de /sistema (recursivo) libera 80 bloques", liberados == 80
                && disk.countFreeFileBlocks() == 80);
        verificar("Ya no existe nada de /sistema", fs.findByPath("/sistema") == null
                && fs.findByPath("/sistema/core.dll") == null && fs.countDirectories() == 1);

        // Un archivo grande usa los huecos: 11..33 (23 bloques) y luego 62..98 (37 bloques).
        FileEntry grande = fs.createFile("/grande.bin", 60, "admin", Permission.PUBLIC, "Proc_big");
        int[] cadena = disk.getChain(grande.getStartBlock());
        verificar("Un archivo de 60 bloques se reparte en los huecos (11..33 y 62..98)",
                grande.getStartBlock() == 11 && cadena.length == 60 && cadena[22] == 33
                && cadena[23] == 62 && cadena[59] == 98);
        verificar("Quedan 20 bloques libres", disk.countFreeFileBlocks() == 20);

        // /usuarios completo: notas_v2 (28), data.csv (57), video.mp4 (20), nuevo.dat (4) = 109.
        verificar("DELETE de /usuarios (recursivo) libera 109 bloques", fs.delete("/usuarios") == 109);
        verificar("Quedan mbr.bin (11) y grande.bin (60): 71 bloques ocupados",
                fs.getUsedBlocks() == 71 && fs.countFiles() == 2 && fs.countDirectories() == 0);
    }

    private static void permisosConElJsonDelEnunciado() throws IOException {
        System.out.println("--- 9. Permisos sobre el JSON del enunciado ---");
        FileSystem fs = cargarEnunciado(new Disk(200, 100));
        AccessControl acceso = new AccessControl();
        acceso.setUserMode("user1");

        verificar("user1 lee su archivo privado notas.txt",
                acceso.check(fs, IoOperation.READ, "/usuarios/notas.txt", NodeType.FILE) == null);
        verificar("user1 lee data.csv (de user2, pero público)",
                acceso.check(fs, IoOperation.READ, "/usuarios/data.csv", NodeType.FILE) == null);
        verificar("user1 lee un archivo del sistema",
                acceso.check(fs, IoOperation.READ, "/sistema/boot.bin", NodeType.FILE) == null);
        String creacion = acceso.check(fs, IoOperation.CREATE, "/usuarios/nuevo.txt", NodeType.FILE);
        verificar("user1 no puede crear en /usuarios (es un directorio de admin) -> " + creacion,
                creacion != null && creacion.contains("propios directorios"));
        String borrado = acceso.check(fs, IoOperation.DELETE, "/usuarios/data.csv", NodeType.FILE);
        verificar("user1 no puede eliminar data.csv (es de user2) -> " + borrado,
                borrado != null && borrado.contains("pertenece a user2"));
        verificar("user1 elimina su video.mp4",
                acceso.check(fs, IoOperation.DELETE, "/usuarios/video.mp4", NodeType.FILE) == null);
        String renombrado = acceso.check(fs, IoOperation.UPDATE, "/usuarios/notas.txt", NodeType.FILE);
        verificar("user1 no puede renombrar -> " + renombrado,
                renombrado != null && renombrado.contains("Solo el administrador"));

        acceso.setUserMode("user2");
        String lectura = acceso.check(fs, IoOperation.READ, "/usuarios/notas.txt", NodeType.FILE);
        verificar("user2 no puede leer notas.txt (privado de user1) -> " + lectura,
                lectura != null && lectura.contains("privado"));
        verificar("user2 sí lee video.mp4 (público de user1)",
                acceso.check(fs, IoOperation.READ, "/usuarios/video.mp4", NodeType.FILE) == null);

        acceso.setAdminMode();
        verificar("El administrador puede todo: leer lo privado, renombrar y eliminar",
                acceso.check(fs, IoOperation.READ, "/usuarios/notas.txt", NodeType.FILE) == null
                && acceso.check(fs, IoOperation.UPDATE, "/usuarios/notas.txt", NodeType.FILE) == null
                && acceso.check(fs, IoOperation.DELETE, "/usuarios", NodeType.DIRECTORY) == null);
    }

    // ------------------------------------------------------------------

    private static String cadena(Disk disk, FileEntry file) {
        int[] bloques = disk.getChain(file.getStartBlock());
        StringBuilder texto = new StringBuilder();
        for (int i = 0; i < bloques.length; i++) {
            if (i > 0) {
                texto.append(' ');
            }
            texto.append(bloques[i]);
        }
        return texto.toString();
    }

    private static void esperaError(String descripcion, String fragmento, Runnable accion) {
        String mensaje = null;
        try {
            accion.run();
        } catch (FileSystemException e) {
            mensaje = e.getMessage();
        }
        verificar(descripcion + " -> " + (mensaje == null ? "NO dio error" : "'" + mensaje + "'"),
                mensaje != null && mensaje.contains(fragmento));
    }

    private static void verificar(String descripcion, boolean ok) {
        comprobaciones++;
        if (!ok) {
            fallos++;
        }
        System.out.println((ok ? "OK    " : "FALLO ") + descripcion);
    }
}
