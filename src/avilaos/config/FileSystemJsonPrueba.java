package avilaos.config;

import avilaos.hardware.disk.Disk;
import avilaos.kernel.fs.FileEntry;
import avilaos.kernel.fs.FileSystem;
import avilaos.kernel.fs.FsNode;
import avilaos.kernel.fs.Permission;
import java.io.File;
import java.io.IOException;

/**
 * Prueba del JSON del sistema de archivos (F2.4): carga el del enunciado, rechaza
 * archivos inválidos con un mensaje claro y comprueba que guardar y volver a cargar
 * deja el mismo estado (incluyendo archivos fragmentados y la posición del cabezal).
 *
 * Se ejecuta desde la carpeta del proyecto (usa config/p2/...). Termina con código 1
 * si algo falla.
 */
public class FileSystemJsonPrueba {

    private static final String ENUNCIADO = "config/p2/sistema_archivos_enunciado.json";

    private static int fallos = 0;
    private static int comprobaciones = 0;

    public static void main(String[] args) throws IOException {
        cargarEnunciado();
        rechazos();
        ordenDeLasEntradas();
        guardarYVolverACargar();

        System.out.println();
        System.out.println("Comprobaciones: " + comprobaciones + " | Fallos: " + fallos);
        if (fallos > 0) {
            System.exit(1);
        }
    }

    private static void cargarEnunciado() throws IOException {
        System.out.println("--- 1. El JSON del enunciado ---");
        FileSystemConfig config = ConfigStore.loadFileSystem(new File(ENUNCIADO));
        Disk disk = new Disk(config.diskConfig.totalBlocks, 0);
        FileSystem fs = new FileSystem(disk);
        config.populate(fs);

        verificar("Disco de 200 bloques con el cabezal en 50",
                config.diskConfig.totalBlocks == 200 && config.diskConfig.initialHead == 50);
        verificar("9 archivos y 2 directorios", fs.countFiles() == 9 && fs.countDirectories() == 2);
        verificar("Los archivos llenan los 200 bloques (11+23+28+2+31+24+4+57+20)",
                fs.getUsedBlocks() == 200 && disk.countFreeFileBlocks() == 0);
        FileEntry mbr = (FileEntry) fs.findByPath("/mbr.bin");
        FileEntry notas = (FileEntry) fs.findByPath("/usuarios/notas.txt");
        FileEntry video = (FileEntry) fs.findByPath("/usuarios/video.mp4");
        verificar("/mbr.bin: bloques 0 a 10, dueño admin, público",
                mbr.getStartBlock() == 0 && mbr.getBlockCount() == 11 && mbr.getOwner().equals("admin")
                && mbr.getPermission() == Permission.PUBLIC);
        verificar("/usuarios/notas.txt: bloque 34, 28 bloques, dueño user1, privado",
                notas.getStartBlock() == 34 && notas.getBlockCount() == 28 && notas.getOwner().equals("user1")
                && notas.getPermission() == Permission.PRIVATE);
        verificar("/usuarios/video.mp4: bloque 180, 20 bloques", video.getStartBlock() == 180
                && video.getBlockCount() == 20 && disk.getBlock(199).getFile() == video);
        verificar("Los primeros bloques de los archivos son los destinos de los casos P1-P4",
                primerBloque(fs, "/sistema/config.sys") == 95 && primerBloque(fs, "/usuarios/img1.png") == 119
                && primerBloque(fs, "/usuarios/data.csv") == 123 && primerBloque(fs, "/sistema/script.py") == 62
                && primerBloque(fs, "/sistema/core.dll") == 64 && primerBloque(fs, "/sistema/boot.bin") == 11);
        verificar("Cada archivo se marca como creado por el JSON", notas.getCreator().equals(FsNode.LOADED_FROM_JSON));
    }

    private static void rechazos() {
        System.out.println("--- 2. Archivos inválidos ---");
        String disco = "\"diskConfig\": {\"totalBlocks\": 200, \"initialHead\": 50}";
        String archivoOk = "{\"path\": \"/a.txt\", \"type\": \"FILE\", \"startBlock\": 0, \"blocks\": 10, "
                + "\"owner\": \"admin\", \"permissions\": \"public\"}";

        rechaza("JSON mal formado", "no es un JSON válido", "{ \"diskConfig\": ");
        rechaza("JSON vacío", "está vacío", "");
        rechaza("Falta diskConfig", "Falta la sección 'diskConfig'", "{\"fileSystem\": []}");
        rechaza("Disco muy chico (50 bloques)", "entre 100 y 500",
                "{\"diskConfig\": {\"totalBlocks\": 50, \"initialHead\": 0}, \"fileSystem\": []}");
        rechaza("Disco muy grande (600 bloques)", "entre 100 y 500",
                "{\"diskConfig\": {\"totalBlocks\": 600, \"initialHead\": 0}, \"fileSystem\": []}");
        rechaza("Cabezal negativo", "no puede ser negativo",
                "{\"diskConfig\": {\"totalBlocks\": 200, \"initialHead\": -5}, \"fileSystem\": []}");
        rechaza("Un número escrito como texto", "no es un JSON válido",
                "{" + disco + ", \"fileSystem\": [{\"path\": \"/a\", \"type\": \"FILE\", \"startBlock\": \"x\", \"blocks\": 1}]}");
        rechaza("Tipo desconocido", "'type' debe ser FILE o DIRECTORY",
                "{" + disco + ", \"fileSystem\": [{\"path\": \"/a\", \"type\": \"LINK\"}]}");
        rechaza("Permiso desconocido", "'permissions' debe ser public o private",
                "{" + disco + ", \"fileSystem\": [{\"path\": \"/a\", \"type\": \"DIRECTORY\", \"permissions\": \"secreto\"}]}");
        rechaza("Entrada sin ruta", "no tiene 'path'",
                "{" + disco + ", \"fileSystem\": [{\"type\": \"DIRECTORY\"}]}");
        rechaza("Archivo sin startBlock", "falta 'startBlock'",
                "{" + disco + ", \"fileSystem\": [{\"path\": \"/a\", \"type\": \"FILE\", \"blocks\": 3}]}");
        rechaza("Archivo sin blocks", "falta 'blocks'",
                "{" + disco + ", \"fileSystem\": [{\"path\": \"/a\", \"type\": \"FILE\", \"startBlock\": 3}]}");
        rechaza("Archivo de 0 bloques", "al menos 1 bloque",
                "{" + disco + ", \"fileSystem\": [{\"path\": \"/a\", \"type\": \"FILE\", \"startBlock\": 3, \"blocks\": 0}]}");
        rechaza("Bloques fuera de rango", "fuera de la zona de archivos",
                "{" + disco + ", \"fileSystem\": [{\"path\": \"/a\", \"type\": \"FILE\", \"startBlock\": 195, \"blocks\": 10}]}");
        rechaza("Bloque inicial negativo", "fuera de la zona de archivos",
                "{" + disco + ", \"fileSystem\": [{\"path\": \"/a\", \"type\": \"FILE\", \"startBlock\": -2, \"blocks\": 4}]}");
        rechaza("Bloques solapados", "ya están ocupados",
                "{" + disco + ", \"fileSystem\": [" + archivoOk
                + ", {\"path\": \"/b.txt\", \"type\": \"FILE\", \"startBlock\": 5, \"blocks\": 10}]}");
        rechaza("Ruta sin padre", "La carpeta /sistema no existe",
                "{" + disco + ", \"fileSystem\": [{\"path\": \"/sistema/a.txt\", \"type\": \"FILE\", \"startBlock\": 0, \"blocks\": 2}]}");
        rechaza("Archivo dentro de un archivo", "es un archivo",
                "{" + disco + ", \"fileSystem\": [" + archivoOk
                + ", {\"path\": \"/a.txt/b.txt\", \"type\": \"FILE\", \"startBlock\": 20, \"blocks\": 2}]}");
        rechaza("Ruta repetida", "Ya existe",
                "{" + disco + ", \"fileSystem\": [" + archivoOk
                + ", {\"path\": \"/a.txt\", \"type\": \"FILE\", \"startBlock\": 20, \"blocks\": 2}]}");
        rechaza("Ruta sin barra inicial", "empezar con /",
                "{" + disco + ", \"fileSystem\": [{\"path\": \"a.txt\", \"type\": \"FILE\", \"startBlock\": 0, \"blocks\": 2}]}");
        rechaza("Cadena que no coincide con blocks", "no coincide",
                "{" + disco + ", \"fileSystem\": [{\"path\": \"/a\", \"type\": \"FILE\", \"blocks\": 3, \"chain\": [1, 2]}]}");
        rechaza("Cadena con un bloque repetido", "repetidos",
                "{" + disco + ", \"fileSystem\": [{\"path\": \"/a\", \"type\": \"FILE\", \"chain\": [1, 2, 1]}]}");

        String mensaje = mensajeDe("{" + disco + ", \"fileSystem\": [" + archivoOk
                + ", {\"path\": \"/b.txt\", \"type\": \"FILE\", \"startBlock\": 5, \"blocks\": 10}]}");
        verificar("El mensaje indica la entrada y la ruta", mensaje != null
                && mensaje.contains("Entrada 2 (/b.txt)"));

        FileSystemConfig sinCampos = ConfigStore.parseFileSystem("{" + disco
                + ", \"fileSystem\": [{\"path\": \"/d\", \"type\": \"directory\"}]}");
        sinCampos.validate();
        verificar("Sin owner ni permissions se usa admin y público; 'type' acepta minúsculas", true);
    }

    private static void ordenDeLasEntradas() {
        System.out.println("--- 3. El orden de las entradas no importa ---");
        String json = "{\"diskConfig\": {\"totalBlocks\": 200, \"initialHead\": 0}, \"fileSystem\": ["
                + "{\"path\": \"/a/b/c.txt\", \"type\": \"FILE\", \"startBlock\": 0, \"blocks\": 3, \"owner\": \"u\", \"permissions\": \"private\"},"
                + "{\"path\": \"/a/b\", \"type\": \"DIRECTORY\", \"owner\": \"u\", \"permissions\": \"public\"},"
                + "{\"path\": \"/a\", \"type\": \"DIRECTORY\", \"owner\": \"admin\", \"permissions\": \"public\"}]}";
        FileSystemConfig config = ConfigStore.parseFileSystem(json);
        config.validate();
        FileSystem fs = new FileSystem(new Disk(200, 0));
        config.populate(fs);
        verificar("Un archivo antes que sus carpetas se carga bien",
                fs.findByPath("/a/b/c.txt") != null && fs.countDirectories() == 2);
    }

    private static void guardarYVolverACargar() throws IOException {
        System.out.println("--- 4. Guardar y volver a cargar ---");
        FileSystemConfig original = ConfigStore.loadFileSystem(new File(ENUNCIADO));
        Disk disk = new Disk(200, 100);
        FileSystem fs = new FileSystem(disk);
        original.populate(fs);

        // Se modifica el estado: se borran dos archivos y se crea uno que queda fragmentado.
        fs.delete("/usuarios/img1.png");      // libera 119..122
        fs.delete("/sistema/script.py");      // libera 62..63
        fs.createDirectory("/nuevo", "user1", Permission.PRIVATE);
        FileEntry roto = fs.createFile("/nuevo/roto.dat", 5, "user1", Permission.PRIVATE, "Proc_X");
        fs.rename("/usuarios/notas.txt", "notas_v2.txt");
        disk.setCurrentHead(77);

        verificar("El archivo nuevo quedó fragmentado (62, 63 y 119..121)",
                disk.getChain(roto.getStartBlock()).length == 5 && roto.getStartBlock() == 62
                && disk.getChain(62)[2] == 119);

        FileSystemConfig guardado = FileSystemConfig.fromState(fs, disk);
        File temporal = File.createTempFile("fs_p2_", ".json");
        temporal.deleteOnExit();
        ConfigStore.saveFileSystem(guardado, temporal);

        FileSystemConfig recargado = ConfigStore.loadFileSystem(temporal);
        Disk disk2 = new Disk(recargado.diskConfig.totalBlocks, 100);
        FileSystem fs2 = new FileSystem(disk2);
        recargado.populate(fs2);

        verificar("Se conserva la posición del cabezal", recargado.diskConfig.initialHead == 77);
        verificar("Mismos archivos y directorios", fs2.countFiles() == fs.countFiles()
                && fs2.countDirectories() == fs.countDirectories());
        verificar("El archivo renombrado y el borrado", fs2.findByPath("/usuarios/notas_v2.txt") != null
                && fs2.findByPath("/usuarios/img1.png") == null && fs2.findByPath("/sistema/script.py") == null);
        boolean iguales = true;
        FsNode[] nodos = fs.getAllNodes();
        for (int i = 0; i < nodos.length; i++) {
            String ruta = fs.pathOf(nodos[i]);
            FsNode otro = fs2.findByPath(ruta);
            if (otro == null || otro.getType() != nodos[i].getType()
                    || !otro.getOwner().equals(nodos[i].getOwner())
                    || otro.getPermission() != nodos[i].getPermission()
                    || otro.getBlockCount() != nodos[i].getBlockCount()
                    || otro.getFirstBlock() != nodos[i].getFirstBlock()) {
                iguales = false;
                System.out.println("      diferencia en " + ruta);
            }
        }
        verificar("Cada elemento conserva tipo, dueño, permiso, tamaño y primer bloque", iguales);
        verificar("La cadena fragmentada se conserva",
                cadena(disk2.getChain(62)).equals("62 63 119 120 121")
                && fs2.findByPath("/nuevo/roto.dat").getFirstBlock() == 62);
        // 200 bloques - 4 (img1.png) - 2 (script.py) + 5 (roto.dat) ocupados = queda 1 libre
        verificar("Los bloques ocupados son los mismos (queda 1 libre)",
                disk.countFreeFileBlocks() == 1 && disk2.countFreeFileBlocks() == 1);

        String texto = ConfigStore.toJson(guardado);
        verificar("Los archivos consecutivos no llevan 'chain' y el fragmentado sí",
                texto.contains("\"chain\"") && texto.indexOf("\"chain\"") == texto.lastIndexOf("\"chain\""));
    }

    // ------------------------------------------------------------------

    private static int primerBloque(FileSystem fs, String ruta) {
        return fs.findByPath(ruta).getFirstBlock();
    }

    private static String cadena(int[] bloques) {
        StringBuilder texto = new StringBuilder();
        for (int i = 0; i < bloques.length; i++) {
            if (i > 0) {
                texto.append(' ');
            }
            texto.append(bloques[i]);
        }
        return texto.toString();
    }

    /** Devuelve el mensaje de error al validar el texto, o null si es válido. */
    private static String mensajeDe(String json) {
        try {
            ConfigStore.parseFileSystem(json).validate();
            return null;
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }

    private static void rechaza(String descripcion, String fragmento, String json) {
        String mensaje = mensajeDe(json);
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
