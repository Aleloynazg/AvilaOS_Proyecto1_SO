package avilaos.kernel.fs;

import avilaos.hardware.disk.Disk;

/**
 * Prueba del sistema de archivos (F2.1 y F2.2): crear, renombrar y eliminar
 * archivos y directorios sobre el disco, con asignación encadenada.
 *
 * Se ejecuta como programa normal. Termina con código 1 si algo falla.
 */
public class FileSystemPrueba {

    private static int fallos = 0;
    private static int comprobaciones = 0;

    public static void main(String[] args) {
        arbolYCreacion();
        asignacionEncadenada();
        renombrar();
        eliminarRecursivo();
        discoLleno();
        archivosDelJson();
        recorridoYColores();

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
