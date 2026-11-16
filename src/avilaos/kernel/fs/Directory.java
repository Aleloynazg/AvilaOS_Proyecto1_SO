/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.kernel.fs;
import avilaos.structures.LinkedQueue;
/**
 * Representa un directorio dentro del sistema de archivos.
 * Puede contener archivos y otros directorios
 * @author alexandraloynaz
 */
public class Directory extends FsNode {
     private LinkedQueue<FsNode> children;

    public Directory(String name, Permission permission,
                     Directory parent) {

        super(name, NodeType.DIRECTORY, permission, parent);

        children = new LinkedQueue<>();
    }

    /**
     * Agrega un elemento al directorio.
     */
    public boolean addChild(FsNode child) {

        if (child == null) {
            return false;
        }

        // Evitar elementos con nombres repetidos.
        if (findChild(child.getName()) != null) {
            return false;
        }

        child.setParent(this);
        children.enqueue(child);

        return true;
    }
    /**
     * Busca un elemento directamente dentro del directorio.
     */
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
    /**
     * Elimina un elemento del directorio.
     */
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
    
}
