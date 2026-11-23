package avilaos.kernel.fs;

import avilaos.kernel.process.IoOperation;

/**
 * Control de acceso del sistema de archivos: decide si una operación está
 * permitida según el modo de uso (administrador o usuario) y el usuario actual,
 * y devuelve el motivo del rechazo para mostrarlo en el log y en la interfaz.
 *
 * Reglas (DECISIONES.md, sección 10):
 * - Administrador: todas las operaciones.
 * - Usuario: ve toda la estructura y lee sus archivos y los públicos; crea
 *   archivos solo dentro de directorios propios; elimina solo archivos propios;
 *   no renombra ni crea directorios.
 * - SUSPEND y RESUME son operaciones internas del sistema y siempre se permiten.
 */
public class AccessControl {

    /** Nombre del usuario en modo administrador. */
    public static final String ADMIN_USER = FsNode.DEFAULT_OWNER;

    private UserMode mode = UserMode.ADMIN;
    private String currentUser = ADMIN_USER;

    public UserMode getMode() {
        return mode;
    }

    /** Usuario actual ("admin" en modo administrador). */
    public String getCurrentUser() {
        return currentUser;
    }

    /** Pasa a modo administrador. */
    public void setAdminMode() {
        this.mode = UserMode.ADMIN;
        this.currentUser = ADMIN_USER;
    }

    /**
     * Pasa a modo usuario con el nombre indicado.
     *
     * @throws IllegalArgumentException si el nombre está vacío o es "admin"
     */
    public void setUserMode(String userName) {
        if (userName == null || userName.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre de usuario no puede estar vacío");
        }
        if (userName.trim().equalsIgnoreCase(ADMIN_USER)) {
            throw new IllegalArgumentException("El nombre '" + ADMIN_USER
                    + "' está reservado para el administrador");
        }
        this.mode = UserMode.USER;
        this.currentUser = userName.trim();
    }

    /** Texto para el log: "administrador" o "usuario user1". */
    public String describe() {
        return mode == UserMode.ADMIN ? "administrador" : "usuario " + currentUser;
    }

    /**
     * Revisa si la operación sobre la ruta está permitida en el modo actual.
     *
     * @param fs sistema de archivos donde se busca la ruta
     * @param operation operación pedida
     * @param path ruta del archivo o directorio (para CREATE, la ruta del nuevo elemento)
     * @param createType para CREATE, si se crea un archivo o un directorio; se ignora en las demás
     * @return null si está permitida; si no, el motivo del rechazo
     */
    public String check(FileSystem fs, IoOperation operation, String path, NodeType createType) {
        if (operation == IoOperation.SUSPEND || operation == IoOperation.RESUME) {
            return null;
        }
        switch (operation) {
            case CREATE:
                return checkCreate(fs, path, createType);
            case READ:
                return checkRead(fs, path);
            case UPDATE:
                return checkRename(fs, path);
            case DELETE:
                return checkDelete(fs, path);
            default:
                return "Operación desconocida: " + operation;
        }
    }

    private String checkRead(FileSystem fs, String path) {
        FsNode node = fs.findByPath(path);
        if (node == null) {
            return "La ruta no existe: " + path;
        }
        if (mode == UserMode.ADMIN || node.isDirectory()) {
            return null;
        }
        if (node.getOwner().equals(currentUser) || node.getPermission() == Permission.PUBLIC) {
            return null;
        }
        return "El archivo " + path + " es privado y pertenece a " + node.getOwner()
                + "; el " + describe() + " no puede leerlo";
    }

    private String checkCreate(FileSystem fs, String path, NodeType type) {
        if (path == null || path.length() < 2 || path.charAt(0) != '/') {
            return "La ruta debe empezar con / y tener un nombre: " + path;
        }
        if (fs.findByPath(path) != null) {
            return "Ya existe " + path;
        }
        int cut = path.lastIndexOf('/');
        String parentPath = cut == 0 ? "/" : path.substring(0, cut);
        FsNode parent = fs.findByPath(parentPath);
        if (parent == null) {
            return "La carpeta " + parentPath + " no existe";
        }
        if (!parent.isDirectory()) {
            return parentPath + " es un archivo, no una carpeta";
        }
        if (mode == UserMode.ADMIN) {
            return null;
        }
        if (type == NodeType.DIRECTORY) {
            return "Solo el administrador puede crear directorios";
        }
        if (!parent.getOwner().equals(currentUser)) {
            return "El " + describe() + " solo puede crear archivos dentro de sus propios directorios, y "
                    + parentPath + " pertenece a " + parent.getOwner();
        }
        return null;
    }

    private String checkRename(FileSystem fs, String path) {
        if (fs.findByPath(path) == null) {
            return "La ruta no existe: " + path;
        }
        if (mode == UserMode.ADMIN) {
            return null;
        }
        return "Solo el administrador puede renombrar archivos y directorios";
    }

    private String checkDelete(FileSystem fs, String path) {
        FsNode node = fs.findByPath(path);
        if (node == null) {
            return "La ruta no existe: " + path;
        }
        if (mode == UserMode.ADMIN) {
            return null;
        }
        if (node.isDirectory()) {
            return "Un usuario no puede eliminar directorios";
        }
        if (!node.getOwner().equals(currentUser)) {
            return "El archivo " + path + " pertenece a " + node.getOwner()
                    + "; el " + describe() + " solo puede eliminar sus propios archivos";
        }
        return null;
    }
}
