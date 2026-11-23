package avilaos.kernel.fs;

import avilaos.structures.LinkedQueue;

/**
 * Directorio del sistema de archivos. Sus hijos (archivos y otros
 * directorios) van en una lista enlazada propia.
 */
public class Directory extends FsNode {

    private LinkedQueue<FsNode> children;

    public Directory(String name, Permission permission,
                     Directory parent) {
        super(name, NodeType.DIRECTORY, permission, parent);
        children = new LinkedQueue<>();
    }

    /**
     * Agrega un hijo si no existe otro con el mismo nombre.
     *
     * @return true si se agregó; false si el hijo es null o el nombre ya existe
     */
    public boolean addChild(FsNode child) {
        if (child == null) {
            return false;
        }
        if (findChild(child.getName()) != null) {
            return false;
        }
        child.setParent(this);
        children.enqueue(child);
        return true;
    }

    public FsNode findChild(String name) {
        if (name == null) {
            return null;
        }
        for (int i = 0; i < children.size(); i++) {
            FsNode child = children.get(i);
            if (child.getName().equals(name)) {
                return child;
            }
        }
        return null;
    }

    public boolean removeChild(FsNode child) {
        if (child == null) {
            return false;
        }
        boolean removed = children.remove(child);
        if (removed) {
            child.setParent(null);
        }
        return removed;
    }

    public int getChildCount() {
        return children.size();
    }

    public FsNode getChildAt(int index) {
        return children.get(index);
    }

    @Override
    public int getFirstBlock() {
        for (int i = 0; i < children.size(); i++) {
            int first = children.get(i).getFirstBlock();
            if (first >= 0) {
                return first;
            }
        }
        return -1;
    }

    @Override
    public int getBlockCount() {
        int total = 0;
        for (int i = 0; i < children.size(); i++) {
            total += children.get(i).getBlockCount();
        }
        return total;
    }
}
