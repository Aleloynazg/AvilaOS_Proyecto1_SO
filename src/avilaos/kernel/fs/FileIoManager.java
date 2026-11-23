package avilaos.kernel.fs;

import avilaos.kernel.disk.DiskRequest;
import avilaos.kernel.process.IoOperation;
import avilaos.kernel.process.PCB;

/**
 * Traduce la operación de un proceso de E/S de archivos en una solicitud para el disco
 * y, cuando el disco la termina, la aplica sobre el sistema de archivos.
 *
 * Reglas (ver DECISIONES.md):
 * <ul>
 *   <li>Antes de pedirle nada al disco se revisa que la operación tenga sentido (la ruta
 *       existe, hay espacio, el nombre es válido...). Si no, se lanza
 *       {@link FileSystemException} con el motivo y el proceso termina con IO_FAILED.</li>
 *   <li>CREATE de un archivo reserva sus bloques al generar la solicitud (decisión del equipo:
 *       así se conoce el bloque destino y dos CREATE no toman los mismos bloques); si la
 *       solicitud falla o se cancela, se libera. UPDATE, DELETE y CREATE de un directorio se
 *       aplican cuando el disco TERMINA la solicitud. Como el estado pudo cambiar mientras
 *       esperaba en la cola, se revisa de nuevo al terminar.</li>
 *   <li>Costo en bloques: leer y crear un archivo cuestan tantos bloques como tiene;
 *       renombrar y eliminar cuestan 1 (solo cambia la entrada del directorio).</li>
 *   <li>Los directorios no ocupan bloques propios: sus operaciones van al primer bloque
 *       del primer archivo que contienen, o al bloque {@link #METADATA_BLOCK} si están
 *       vacíos o se están creando.</li>
 * </ul>
 */
public class FileIoManager {

    /** Bloque donde se atienden las operaciones de directorios vacíos o nuevos (la tabla del sistema de archivos). */
    public static final int METADATA_BLOCK = 0;

    private final FileSystem fs;

    public FileIoManager(FileSystem fs) {
        if (fs == null) {
            throw new IllegalArgumentException("Falta el sistema de archivos");
        }
        this.fs = fs;
    }

    /**
     * Nombre que se usa cuando un UPDATE no trae nombre nuevo: se agrega "_v2" antes de la
     * extensión ("notas.txt" pasa a "notas_v2.txt"; "datos" pasa a "datos_v2").
     */
    public static String defaultNewName(String name) {
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            return name.substring(0, dot) + "_v2" + name.substring(dot);
        }
        return name + "_v2";
    }

    /**
     * Revisa la operación del proceso y arma la solicitud para el disco.
     *
     * @throws FileSystemException con el motivo si la operación no se puede hacer
     */
    public DiskRequest buildRequest(PCB p, long cycle) {
        IoOperation op = p.getIoOperation();
        String path = p.getTargetPath();
        if (op == null || path == null) {
            throw new FileSystemException("El proceso no tiene una operación de archivos que pedir");
        }
        switch (op) {
            case CREATE:
                return buildCreate(p, path, cycle);
            case READ: {
                FsNode node = existing(path);
                int count = node.isFile() ? node.getBlockCount() : 1;
                return new DiskRequest(p, op, blockOf(node), count, cycle, path, null, null, null);
            }
            case UPDATE: {
                FsNode node = existingNotRoot(path, "renombrar");
                String newName = p.getNewName() != null ? p.getNewName() : defaultNewName(node.getName());
                checkRename(node, newName);
                return new DiskRequest(p, op, blockOf(node), 1, cycle, path, newName, null, null);
            }
            case DELETE: {
                FsNode node = existingNotRoot(path, "eliminar");
                return new DiskRequest(p, op, blockOf(node), 1, cycle, path, null, null, null);
            }
            default:
                throw new FileSystemException("La operación " + op + " no es una operación de archivos");
        }
    }

    /**
     * Aplica sobre el sistema de archivos la operación de una solicitud que el disco terminó.
     *
     * @return descripción de lo que se hizo, para el log
     * @throws FileSystemException con el motivo si ahora la operación ya no se puede hacer
     */
    public String apply(DiskRequest request) {
        PCB p = request.getProcess();
        String path = request.getTargetPath();
        switch (request.getOperation()) {
            case CREATE:
                if (p.getTargetType() == NodeType.DIRECTORY) {
                    Directory dir = fs.createDirectory(path, request.getOwner(), request.getPermission());
                    dir.setCreator(p.getTag());
                    return "crea el directorio " + path;
                }
                // El archivo se reservó al generar la solicitud: ahora solo se confirma que
                // sigue ahí (otro proceso pudo eliminarlo mientras la solicitud esperaba).
                FsNode reserved = request.getReservedNode();
                if (reserved == null || !attached(reserved)) {
                    throw new FileSystemException("El archivo " + path
                            + " ya no existe: lo eliminaron mientras esperaba al disco");
                }
                return "crea " + fs.pathOf(reserved) + " (" + reserved.getBlockCount()
                        + " bloques desde el bloque " + reserved.getFirstBlock() + ")";
            case READ: {
                FsNode node = existing(path);
                return "lee " + path + " (" + (node.isFile() ? node.getBlockCount() : 1) + " bloques)";
            }
            case UPDATE:
                fs.rename(path, request.getNewName());
                return "renombra " + path + " como " + request.getNewName();
            case DELETE: {
                int freed = fs.delete(path);
                return "elimina " + path + (freed > 0 ? " y libera " + freed + " bloques" : "");
            }
            default:
                throw new FileSystemException("La operación " + request.getOperation()
                        + " no es una operación de archivos");
        }
    }

    // ------------------------------------------------------------------

    /**
     * CREATE de un archivo: los bloques se RESERVAN al generar la solicitud (así se conoce su
     * bloque destino y dos CREATE no toman los mismos bloques); el archivo ya aparece en el
     * árbol. Si la solicitud falla o se cancela, {@link #release} lo elimina y libera los bloques.
     * CREATE de un directorio no ocupa bloques: se crea cuando el disco termina.
     */
    private DiskRequest buildCreate(PCB p, String path, long cycle) {
        if (p.getTargetType() == NodeType.DIRECTORY) {
            fs.checkCanCreate(path);
            return new DiskRequest(p, IoOperation.CREATE, METADATA_BLOCK, 1, cycle, path, null,
                    p.getIoOwner(), p.getIoPermission());
        }
        FileEntry file = fs.createFile(path, p.getFileBlocks(), p.getIoOwner(), p.getIoPermission(), p.getTag());
        DiskRequest request = new DiskRequest(p, IoOperation.CREATE, file.getStartBlock(), file.getBlockCount(),
                cycle, path, null, p.getIoOwner(), p.getIoPermission());
        request.setReservedNode(file);
        return request;
    }

    /**
     * Libera lo que reservó una solicitud que no se va a completar (el proceso murió o la
     * operación falló): un archivo de un CREATE se elimina y sus bloques quedan libres.
     *
     * @return cuántos bloques se liberaron
     */
    public int release(DiskRequest request) {
        FsNode reserved = request.getReservedNode();
        if (request.getOperation() != IoOperation.CREATE || reserved == null || !attached(reserved)) {
            return 0;
        }
        return fs.delete(fs.pathOf(reserved));
    }

    /** true si el elemento sigue en el árbol (nadie lo eliminó, ni a él ni a una carpeta que lo contenía). */
    private boolean attached(FsNode node) {
        for (FsNode current = node; current != null; current = current.getParent()) {
            if (current == fs.getRoot()) {
                return true;
            }
        }
        return false;
    }

    private FsNode existing(String path) {
        FsNode node = fs.findByPath(path);
        if (node == null) {
            throw new FileSystemException("La ruta no existe: " + path);
        }
        return node;
    }

    private FsNode existingNotRoot(String path, String action) {
        FsNode node = existing(path);
        if (node == fs.getRoot()) {
            throw new FileSystemException("No se puede " + action + " la raíz");
        }
        return node;
    }

    private void checkRename(FsNode node, String newName) {
        FileSystem.checkName(newName);
        FsNode other = node.getParent().findChild(newName);
        if (other != null && other != node) {
            throw new FileSystemException("Ya existe '" + newName + "' en " + fs.pathOf(node.getParent()));
        }
    }

    private static int blockOf(FsNode node) {
        int first = node.getFirstBlock();
        return first >= 0 ? first : METADATA_BLOCK;
    }
}
