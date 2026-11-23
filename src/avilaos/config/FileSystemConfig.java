package avilaos.config;

import avilaos.hardware.disk.Disk;
import avilaos.kernel.fs.Directory;
import avilaos.kernel.fs.FileEntry;
import avilaos.kernel.fs.FileSystem;
import avilaos.kernel.fs.FileSystemException;
import avilaos.kernel.fs.FsNode;
import avilaos.kernel.fs.Permission;

/**
 * Primer JSON de la simulación: el estado del sistema de archivos
 * (formato exacto del enunciado).
 *
 * <pre>
 * {
 *   "diskConfig": { "totalBlocks": 200, "initialHead": 50 },
 *   "fileSystem": [
 *     { "path": "/mbr.bin", "type": "FILE", "startBlock": 0, "blocks": 11,
 *       "owner": "admin", "permissions": "public" },
 *     { "path": "/sistema", "type": "DIRECTORY", "owner": "admin", "permissions": "public" }
 *   ]
 * }
 * </pre>
 *
 * Extensión opcional: un archivo fragmentado (que ya no ocupa bloques consecutivos) se
 * guarda con "chain": [0, 1, 2, 5] (los bloques de su cadena, en orden). Los archivos del
 * enunciado no la usan.
 *
 * Los campos son públicos para que Gson los lea y los escriba; se usan arreglos
 * en lugar de colecciones de java.util.
 */
public class FileSystemConfig {

    /** El enunciado pide un disco "limitado": entre 100 y 500 bloques. */
    public static final int MIN_TOTAL_BLOCKS = 100;
    public static final int MAX_TOTAL_BLOCKS = 500;

    public DiskConfig diskConfig;
    public Entry[] fileSystem;

    public static class DiskConfig {
        /** Bloques de la zona de archivos (el swap se suma aparte con swapSizeBlocks). */
        public int totalBlocks;
        /** Posición inicial del cabezal. */
        public int initialHead;
    }

    public static class Entry {
        public String path;
        /** FILE o DIRECTORY. */
        public String type;
        public Integer startBlock;
        public Integer blocks;
        /** Opcional: bloques de la cadena, para archivos fragmentados. */
        public int[] chain;
        public String owner;
        /** public o private. */
        public String permissions;
    }

    /** Configuración vacía: disco de 200 bloques, cabezal en 0, sin archivos. */
    public static FileSystemConfig defaults() {
        FileSystemConfig config = new FileSystemConfig();
        config.diskConfig = new DiskConfig();
        config.diskConfig.totalBlocks = 200;
        config.diskConfig.initialHead = 0;
        config.fileSystem = new Entry[0];
        return config;
    }

    /**
     * Revisa tipos y rangos y prueba cargar todo en un sistema de archivos de prueba, de
     * forma que los errores (bloques fuera de rango o solapados, ruta sin padre, nombres
     * repetidos...) salen con el mismo mensaje que al cargar de verdad.
     *
     * @throws IllegalArgumentException con el motivo del primer problema que encuentre
     */
    public void validate() {
        if (diskConfig == null) {
            throw new IllegalArgumentException("Falta la sección 'diskConfig'");
        }
        if (diskConfig.totalBlocks < MIN_TOTAL_BLOCKS || diskConfig.totalBlocks > MAX_TOTAL_BLOCKS) {
            throw new IllegalArgumentException("El campo 'totalBlocks' debe estar entre " + MIN_TOTAL_BLOCKS
                    + " y " + MAX_TOTAL_BLOCKS + " (valor: " + diskConfig.totalBlocks + ")");
        }
        if (diskConfig.initialHead < 0) {
            throw new IllegalArgumentException("El campo 'initialHead' no puede ser negativo (valor: "
                    + diskConfig.initialHead + ")");
        }
        populate(new FileSystem(new Disk(diskConfig.totalBlocks, 0)));
    }

    /**
     * Crea en el sistema de archivos los directorios y archivos del JSON. Primero los
     * directorios (los menos profundos primero) y después los archivos, así el orden de
     * las entradas en el JSON no importa.
     *
     * @throws IllegalArgumentException si una entrada no es válida
     */
    public void populate(FileSystem fs) {
        Entry[] entries = fileSystem == null ? new Entry[0] : fileSystem;
        int n = entries.length;
        boolean[] isDirectory = new boolean[n];
        int[] depth = new int[n];

        for (int i = 0; i < n; i++) {
            Entry e = entries[i];
            if (e == null) {
                throw new IllegalArgumentException("La entrada " + (i + 1) + " de 'fileSystem' está vacía");
            }
            if (e.path == null || e.path.isEmpty()) {
                throw new IllegalArgumentException("La entrada " + (i + 1) + " no tiene 'path'");
            }
            String type = e.type == null ? "" : e.type.trim().toUpperCase();
            if (type.equals("DIRECTORY")) {
                isDirectory[i] = true;
            } else if (!type.equals("FILE")) {
                throw new IllegalArgumentException(prefix(i, e) + "'type' debe ser FILE o DIRECTORY (valor: "
                        + e.type + ")");
            }
            depth[i] = countSlashes(e.path);
        }

        // Directorios, de menos a más profundos (inserción estable).
        int[] order = new int[n];
        int dirs = 0;
        for (int i = 0; i < n; i++) {
            if (isDirectory[i]) {
                int j = dirs - 1;
                while (j >= 0 && depth[order[j]] > depth[i]) {
                    order[j + 1] = order[j];
                    j--;
                }
                order[j + 1] = i;
                dirs++;
            }
        }
        for (int k = 0; k < dirs; k++) {
            Entry e = entries[order[k]];
            try {
                fs.createDirectory(e.path, ownerOf(e), permissionOf(e, order[k]));
            } catch (FileSystemException ex) {
                throw new IllegalArgumentException(prefix(order[k], e) + ex.getMessage());
            }
        }

        for (int i = 0; i < n; i++) {
            if (isDirectory[i]) {
                continue;
            }
            Entry e = entries[i];
            Permission permission = permissionOf(e, i);
            try {
                if (e.chain != null && e.chain.length > 0) {
                    if (e.blocks != null && e.blocks.intValue() != e.chain.length) {
                        throw new IllegalArgumentException(prefix(i, e) + "'blocks' (" + e.blocks
                                + ") no coincide con la cantidad de bloques de 'chain' (" + e.chain.length + ")");
                    }
                    fs.loadFileChain(e.path, e.chain, ownerOf(e), permission, FsNode.LOADED_FROM_JSON);
                } else {
                    if (e.startBlock == null) {
                        throw new IllegalArgumentException(prefix(i, e) + "falta 'startBlock'");
                    }
                    if (e.blocks == null) {
                        throw new IllegalArgumentException(prefix(i, e) + "falta 'blocks'");
                    }
                    fs.loadFile(e.path, e.startBlock.intValue(), e.blocks.intValue(), ownerOf(e),
                            permission, FsNode.LOADED_FROM_JSON);
                }
            } catch (FileSystemException ex) {
                throw new IllegalArgumentException(prefix(i, e) + ex.getMessage());
            }
        }
    }

    /**
     * Describe el estado actual (disco, cabezal y todo el árbol) en este mismo formato,
     * para guardarlo y volver a cargarlo más tarde.
     */
    public static FileSystemConfig fromState(FileSystem fs, Disk disk) {
        FileSystemConfig config = new FileSystemConfig();
        config.diskConfig = new DiskConfig();
        config.diskConfig.totalBlocks = disk.getFileBlockCount();
        config.diskConfig.initialHead = disk.getCurrentHead();

        FsNode[] nodes = fs.getAllNodes();
        config.fileSystem = new Entry[nodes.length];
        for (int i = 0; i < nodes.length; i++) {
            FsNode node = nodes[i];
            Entry e = new Entry();
            e.path = fs.pathOf(node);
            e.owner = node.getOwner();
            e.permissions = node.getPermission() == Permission.PRIVATE ? "private" : "public";
            if (node instanceof Directory) {
                e.type = "DIRECTORY";
            } else {
                FileEntry file = (FileEntry) node;
                e.type = "FILE";
                e.startBlock = file.getStartBlock();
                e.blocks = file.getBlockCount();
                int[] chain = disk.getChain(file.getStartBlock());
                if (!isConsecutive(chain)) {
                    e.chain = chain;
                }
            }
            config.fileSystem[i] = e;
        }
        return config;
    }

    // ------------------------------------------------------------------

    private static boolean isConsecutive(int[] chain) {
        for (int i = 1; i < chain.length; i++) {
            if (chain[i] != chain[i - 1] + 1) {
                return false;
            }
        }
        return true;
    }

    private static String prefix(int index, Entry e) {
        return "Entrada " + (index + 1) + " (" + e.path + "): ";
    }

    private static int countSlashes(String path) {
        int total = 0;
        for (int i = 0; i < path.length(); i++) {
            if (path.charAt(i) == '/') {
                total++;
            }
        }
        return total;
    }

    private static String ownerOf(Entry e) {
        return e.owner == null || e.owner.trim().isEmpty() ? FsNode.DEFAULT_OWNER : e.owner.trim();
    }

    private static Permission permissionOf(Entry e, int index) {
        if (e.permissions == null || e.permissions.trim().isEmpty()) {
            return Permission.PUBLIC;
        }
        String value = e.permissions.trim().toUpperCase();
        if (value.equals("PUBLIC")) {
            return Permission.PUBLIC;
        }
        if (value.equals("PRIVATE")) {
            return Permission.PRIVATE;
        }
        throw new IllegalArgumentException(prefix(index, e) + "'permissions' debe ser public o private (valor: "
                + e.permissions + ")");
    }
}
