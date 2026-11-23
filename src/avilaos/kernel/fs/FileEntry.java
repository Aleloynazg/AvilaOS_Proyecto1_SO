package avilaos.kernel.fs;

/**
 * Archivo del sistema de archivos.
 *
 * No guarda contenido: el enunciado pide simular solo la estructura y los
 * bloques. Su ubicación en el disco es el primer bloque de una cadena
 * (asignación encadenada) y la cantidad de bloques que ocupa.
 */
public class FileEntry extends FsNode {

    private int startBlock = -1;
    private int blocks;

    public FileEntry(String name, Permission permission,
                     Directory parent) {
        super(name, NodeType.FILE, permission, parent);
    }

    /** Registra dónde quedó el archivo en el disco. */
    public void setLocation(int startBlock, int blocks) {
        this.startBlock = startBlock;
        this.blocks = blocks;
    }

    public int getStartBlock() {
        return startBlock;
    }

    @Override
    public int getFirstBlock() {
        return startBlock;
    }

    @Override
    public int getBlockCount() {
        return blocks;
    }
}
