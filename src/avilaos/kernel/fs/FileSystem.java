package avilaos.kernel.fs;

import avilaos.hardware.disk.Disk;
import java.util.concurrent.Semaphore;

/**
 * Sistema de archivos de ÁvilaOS: árbol propio de directorios y archivos
 * sobre el disco simulado, con asignación encadenada de bloques.
 *
 * Crea archivos y directorios, los renombra y los elimina (un directorio se
 * elimina con todo su contenido y se liberan las cadenas de bloques). Los
 * errores se informan con {@link FileSystemException} y un mensaje que
 * explica el motivo; el sistema nunca queda a medias.
 *
 * Las operaciones que cambian el árbol usan un semáforo, porque el reloj
 * (hilo del kernel) y la interfaz leen y modifican el mismo árbol.
 */
public class FileSystem {

    /** Longitud máxima del nombre de un archivo o directorio. */
    public static final int MAX_NAME_LENGTH = 40;

    private final Directory root;
    private final Disk disk;
    private final Semaphore mutex = new Semaphore(1);
    private int nextColor;

    public FileSystem(Disk disk) {
        if (disk == null) {
            throw new IllegalArgumentException("El sistema de archivos necesita un disco");
        }
        this.disk = disk;
        this.root = new Directory("/", Permission.PUBLIC, null);
        this.root.setOwner(FsNode.DEFAULT_OWNER);
    }

    public Directory getRoot() {
        return root;
    }

    public Disk getDisk() {
        return disk;
    }

    // ------------------------------------------------------------------
    // Búsqueda
    // ------------------------------------------------------------------

    /**
     * Busca un elemento por su ruta absoluta (por ejemplo /usuarios/notas.txt).
     *
     * @return el elemento, o null si la ruta no existe
     */
    public FsNode findByPath(String path) {
        mutex.acquireUninterruptibly();
        try {
            return find(path);
        } finally {
            mutex.release();
        }
    }

    private FsNode find(String path) {
        if (path == null || path.isEmpty() || path.charAt(0) != '/') {
            return null;
        }
        if (path.equals("/")) {
            return root;
        }
        String[] names = path.substring(1).split("/", -1);
        Directory current = root;
        for (int i = 0; i < names.length; i++) {
            if (names[i].isEmpty()) {
                return null;
            }
            FsNode found = current.findChild(names[i]);
            if (found == null) {
                return null;
            }
            if (i == names.length - 1) {
                return found;
            }
            if (!(found instanceof Directory)) {
                return null;
            }
            current = (Directory) found;
        }
        return null;
    }

    /** Ruta absoluta de un elemento (la raíz es "/"). */
    public String pathOf(FsNode node) {
        if (node == null) {
            return null;
        }
        if (node == root || node.getParent() == null) {
            return "/";
        }
        String parentPath = pathOf(node.getParent());
        return (parentPath.equals("/") ? "" : parentPath) + "/" + node.getName();
    }

    // ------------------------------------------------------------------
    // Crear
    // ------------------------------------------------------------------

    /**
     * Crea un directorio.
     *
     * @param path ruta completa del directorio nuevo; el padre debe existir
     * @throws FileSystemException si la ruta, el nombre o el padre no son válidos
     */
    public Directory createDirectory(String path, String owner, Permission permission) {
        mutex.acquireUninterruptibly();
        try {
            Directory parent = parentForNewNode(path);
            Directory dir = new Directory(lastName(path), permission, null);
            dir.setOwner(owner);
            parent.addChild(dir);
            dir.setColorIndex(nextColor++);
            return dir;
        } finally {
            mutex.release();
        }
    }

    /**
     * Crea un archivo y le asigna una cadena de bloques libres del disco
     * (los primeros que encuentre; no tienen que ser consecutivos).
     *
     * @param path ruta completa del archivo nuevo; el padre debe existir
     * @param blocks tamaño en bloques (al menos 1)
     * @param creator nombre del proceso que lo crea
     * @throws FileSystemException si no hay bloques libres suficientes o la ruta no es válida
     */
    public FileEntry createFile(String path, int blocks, String owner,
                                Permission permission, String creator) {
        mutex.acquireUninterruptibly();
        try {
            Directory parent = parentForNewNode(path);
            checkBlocks(blocks);
            int free = disk.countFreeFileBlocks();
            if (blocks > free) {
                throw new FileSystemException("No hay bloques libres suficientes: el archivo necesita "
                        + blocks + " y quedan " + free);
            }
            FileEntry file = new FileEntry(lastName(path), permission, null);
            file.setOwner(owner);
            file.setCreator(creator);
            int first = disk.allocateFileChain(file, blocks);
            if (first < 0) {
                throw new FileSystemException("No se pudo reservar la cadena de " + blocks + " bloques");
            }
            file.setLocation(first, blocks);
            parent.addChild(file);
            file.setColorIndex(nextColor++);
            return file;
        } finally {
            mutex.release();
        }
    }

    /**
     * Registra un archivo que ya existe en el disco, en bloques consecutivos
     * desde startBlock (así llega en el JSON del sistema de archivos).
     *
     * @throws FileSystemException si el rango se sale del disco o pisa a otro archivo
     */
    public FileEntry loadFile(String path, int startBlock, int blocks, String owner,
                              Permission permission, String creator) {
        mutex.acquireUninterruptibly();
        try {
            Directory parent = parentForNewNode(path);
            checkBlocks(blocks);
            int last = disk.getFileBlockCount() - 1;
            if (startBlock < 0 || startBlock + blocks - 1 > last) {
                throw new FileSystemException("Los bloques " + startBlock + " a " + (startBlock + blocks - 1)
                        + " están fuera de la zona de archivos (0 a " + last + ")");
            }
            FileEntry file = new FileEntry(lastName(path), permission, null);
            file.setOwner(owner);
            file.setCreator(creator);
            if (!disk.allocateFileAt(file, startBlock, blocks)) {
                throw new FileSystemException("Los bloques " + startBlock + " a " + (startBlock + blocks - 1)
                        + " ya están ocupados por otro archivo");
            }
            file.setLocation(startBlock, blocks);
            parent.addChild(file);
            file.setColorIndex(nextColor++);
            return file;
        } finally {
            mutex.release();
        }
    }

    // ------------------------------------------------------------------
    // Renombrar y eliminar
    // ------------------------------------------------------------------

    /**
     * Cambia el nombre de un archivo o directorio.
     *
     * @throws FileSystemException si la ruta no existe, es la raíz o el nombre nuevo
     *         no es válido o ya lo usa otro elemento de la misma carpeta
     */
    public void rename(String path, String newName) {
        mutex.acquireUninterruptibly();
        try {
            FsNode node = find(path);
            if (node == null) {
                throw new FileSystemException("La ruta no existe: " + path);
            }
            if (node == root) {
                throw new FileSystemException("No se puede renombrar la raíz");
            }
            checkName(newName);
            FsNode other = node.getParent().findChild(newName);
            if (other != null && other != node) {
                throw new FileSystemException("Ya existe '" + newName + "' en "
                        + pathOf(node.getParent()));
            }
            node.setName(newName);
        } finally {
            mutex.release();
        }
    }

    /**
     * Elimina un archivo o un directorio. Un directorio se elimina junto con todos
     * sus archivos y subdirectorios, y se liberan todas las cadenas de bloques.
     *
     * @return cantidad de bloques liberados
     * @throws FileSystemException si la ruta no existe o es la raíz
     */
    public int delete(String path) {
        mutex.acquireUninterruptibly();
        try {
            FsNode node = find(path);
            if (node == null) {
                throw new FileSystemException("La ruta no existe: " + path);
            }
            if (node == root) {
                throw new FileSystemException("No se puede eliminar la raíz");
            }
            int freed = freeBlocksOf(node);
            node.getParent().removeChild(node);
            return freed;
        } finally {
            mutex.release();
        }
    }

    private int freeBlocksOf(FsNode node) {
        if (node instanceof FileEntry) {
            return disk.freeFileChain(((FileEntry) node).getStartBlock());
        }
        int freed = 0;
        Directory dir = (Directory) node;
        for (int i = 0; i < dir.getChildCount(); i++) {
            freed += freeBlocksOf(dir.getChildAt(i));
        }
        return freed;
    }

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    /** Cantidad de archivos (no cuenta directorios) del sistema. */
    public int countFiles() {
        return count(root, NodeType.FILE);
    }

    /** Cantidad de directorios, sin contar la raíz. */
    public int countDirectories() {
        return count(root, NodeType.DIRECTORY) - 1;
    }

    private int count(FsNode node, NodeType type) {
        int total = node.getType() == type ? 1 : 0;
        if (node instanceof Directory) {
            Directory dir = (Directory) node;
            for (int i = 0; i < dir.getChildCount(); i++) {
                total += count(dir.getChildAt(i), type);
            }
        }
        return total;
    }

    /** Bloques de la zona de archivos que están ocupados. */
    public int getUsedBlocks() {
        return disk.getFileBlockCount() - disk.countFreeFileBlocks();
    }

    /**
     * Todos los elementos del árbol (sin la raíz), en recorrido en profundidad:
     * cada directorio aparece antes de su contenido.
     */
    public FsNode[] getAllNodes() {
        FsNode[] result = new FsNode[countFiles() + countDirectories()];
        fill(root, result, 0);
        return result;
    }

    private int fill(Directory dir, FsNode[] result, int index) {
        for (int i = 0; i < dir.getChildCount(); i++) {
            FsNode child = dir.getChildAt(i);
            result[index++] = child;
            if (child instanceof Directory) {
                index = fill((Directory) child, result, index);
            }
        }
        return index;
    }

    // ------------------------------------------------------------------
    // Validaciones de ayuda
    // ------------------------------------------------------------------

    private Directory parentForNewNode(String path) {
        if (path == null || path.length() < 2 || path.charAt(0) != '/') {
            throw new FileSystemException("La ruta debe empezar con / y tener un nombre: " + path);
        }
        if (path.endsWith("/")) {
            throw new FileSystemException("La ruta no puede terminar en /: " + path);
        }
        String name = lastName(path);
        checkName(name);
        int cut = path.lastIndexOf('/');
        String parentPath = cut == 0 ? "/" : path.substring(0, cut);
        FsNode parent = find(parentPath);
        if (parent == null) {
            throw new FileSystemException("La carpeta " + parentPath + " no existe");
        }
        if (!(parent instanceof Directory)) {
            throw new FileSystemException(parentPath + " es un archivo, no una carpeta");
        }
        if (((Directory) parent).findChild(name) != null) {
            throw new FileSystemException("Ya existe " + path);
        }
        return (Directory) parent;
    }

    private static String lastName(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private void checkBlocks(int blocks) {
        if (blocks < 1) {
            throw new FileSystemException("El archivo debe tener al menos 1 bloque (valor: " + blocks + ")");
        }
    }

    /** Un nombre válido no está vacío, no tiene '/' ni espacios en los extremos y cabe en el límite. */
    public static void checkName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new FileSystemException("El nombre no puede estar vacío");
        }
        if (name.indexOf('/') >= 0) {
            throw new FileSystemException("El nombre no puede contener /: " + name);
        }
        if (!name.equals(name.trim())) {
            throw new FileSystemException("El nombre no puede empezar ni terminar con espacios: '" + name + "'");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new FileSystemException("El nombre es demasiado largo (máximo "
                    + MAX_NAME_LENGTH + " caracteres)");
        }
    }
}
