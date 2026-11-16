/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.kernel.fs;

/**
 * Clase base para representar un elemento del sistema de archivos.
 * Puede ser un archivo o un directorio.
 * @author alexandraloynaz
 */
public abstract class FsNode {
    private String name;
    private NodeType type;
    private Permission permission;
    private Directory parent;

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

    public void setName(String name) {
        this.name = name;
    }

    public void setPermission(Permission permission) {
        this.permission = permission;
    }

    public void setParent(Directory parent) {
        this.parent = parent;
    }
}
    

