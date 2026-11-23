package avilaos.kernel.fs;

/**
 * Nodo del árbol del sistema de archivos: puede ser un archivo o un directorio.
 *
 * Guarda lo que muestra el JTree y la tabla de asignación: nombre, tipo,
 * permiso, dueño, proceso creador y color (índice de paleta para el mapa del disco).
 * Los hijos de un directorio van en una lista enlazada propia (no se usan
 * colecciones de java.util).
 */
public abstract class FsNode {

    /** Dueño por defecto de los elementos creados por el administrador. */
    public static final String DEFAULT_OWNER = "admin";

    /** Proceso creador de los elementos que vienen del JSON inicial. */
    public static final String LOADED_FROM_JSON = "JSON";

    private String name;
    private final NodeType type;
    private Permission permission;
    private Directory parent;
    private String owner = DEFAULT_OWNER;
    private String creator = LOADED_FROM_JSON;
    private int colorIndex;

    public FsNode(String name, NodeType type, Permission permission,
                  Directory parent) {
        this.name = name;
        this.type = type;
        this.permission = permission;
        this.parent = parent;
    }

    public String getName() {
        return name;
    }

    public NodeType getType() {
        return type;
    }

    public Permission getPermission() {
        return permission;
    }

    public Directory getParent() {
        return parent;
    }

    /** Dueño del elemento (por ejemplo "admin" o "user1"). */
    public String getOwner() {
        return owner;
    }

    /** Nombre del proceso que creó el elemento ("JSON" si vino del archivo inicial). */
    public String getCreator() {
        return creator;
    }

    /** Índice de color asignado para pintar el elemento en el mapa del disco. */
    public int getColorIndex() {
        return colorIndex;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPermission(Permission permission) {
        this.permission = permission;
    }

    public void setParent(Directory parent) {
        this.parent = parent;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public void setCreator(String creator) {
        this.creator = creator;
    }

    public void setColorIndex(int colorIndex) {
        this.colorIndex = colorIndex;
    }

    public boolean isDirectory() {
        return type == NodeType.DIRECTORY;
    }

    public boolean isFile() {
        return type == NodeType.FILE;
    }

    /**
     * Primer bloque del elemento. Para un archivo es el inicio de su cadena;
     * para un directorio es el primer bloque del primer archivo que contiene
     * (recorrido en profundidad), o -1 si no contiene archivos.
     */
    public abstract int getFirstBlock();

    /** Tamaño en bloques. Para un directorio es la suma de lo que contiene. */
    public abstract int getBlockCount();
}
