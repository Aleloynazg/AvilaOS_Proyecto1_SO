package avilaos.gui;

import avilaos.kernel.fs.Directory;
import avilaos.kernel.fs.FileSystem;
import avilaos.kernel.fs.FsNode;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeModel;

/**
 * Adaptador entre el árbol propio de ÁvilaOS (Directory / FileEntry) y el JTree.
 * No copia el árbol a otra estructura: el JTree lee directamente los nodos del
 * FileSystem. Los oyentes se guardan en un arreglo propio (sin java.util).
 */
public class FsTreeModel implements TreeModel {

    private final FileSystem fileSystem;
    private TreeModelListener[] listeners = new TreeModelListener[0];

    public FsTreeModel(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    @Override
    public Object getRoot() {
        return fileSystem.getRoot();
    }

    @Override
    public Object getChild(Object parent, int index) {
        if (parent instanceof Directory) {
            Directory dir = (Directory) parent;
            if (index >= 0 && index < dir.getChildCount()) {
                return dir.getChildAt(index);
            }
        }
        return null;
    }

    @Override
    public int getChildCount(Object parent) {
        return parent instanceof Directory ? ((Directory) parent).getChildCount() : 0;
    }

    @Override
    public boolean isLeaf(Object node) {
        return !(node instanceof Directory);
    }

    @Override
    public int getIndexOfChild(Object parent, Object child) {
        if (parent instanceof Directory) {
            Directory dir = (Directory) parent;
            for (int i = 0; i < dir.getChildCount(); i++) {
                if (dir.getChildAt(i) == child) {
                    return i;
                }
            }
        }
        return -1;
    }

    @Override
    public void valueForPathChanged(TreePath path, Object newValue) {
        // El JTree no edita: los cambios los hacen los procesos de E/S.
    }

    @Override
    public void addTreeModelListener(TreeModelListener l) {
        TreeModelListener[] bigger = new TreeModelListener[listeners.length + 1];
        for (int i = 0; i < listeners.length; i++) {
            bigger[i] = listeners[i];
        }
        bigger[listeners.length] = l;
        listeners = bigger;
    }

    @Override
    public void removeTreeModelListener(TreeModelListener l) {
        int count = 0;
        for (int i = 0; i < listeners.length; i++) {
            if (listeners[i] != l) {
                count++;
            }
        }
        TreeModelListener[] smaller = new TreeModelListener[count];
        int j = 0;
        for (int i = 0; i < listeners.length; i++) {
            if (listeners[i] != l) {
                smaller[j++] = listeners[i];
            }
        }
        listeners = smaller;
    }

    /** Avisa al JTree que el árbol cambió (se crea, borra o renombra algo). */
    public void fireStructureChanged() {
        TreeModelEvent event = new TreeModelEvent(this, new Object[] { getRoot() });
        for (int i = 0; i < listeners.length; i++) {
            listeners[i].treeStructureChanged(event);
        }
    }

    /** Ruta del nodo en el sistema de archivos, por ejemplo "/usuarios/notas.txt". */
    public String pathOf(FsNode node) {
        return fileSystem.pathOf(node);
    }
}