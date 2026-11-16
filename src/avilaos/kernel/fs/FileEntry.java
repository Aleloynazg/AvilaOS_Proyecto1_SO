/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.kernel.fs;

/**
 * Representa un archivo dentro del sistema de archivos.
 * @author alexandraloynaz
 */
public class FileEntry extends FsNode {

    private String content;

    public FileEntry(String name, Permission permission,
                     Directory parent) {

        super(name, NodeType.FILE, permission, parent);

        content = "";
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public int getSize() {
        return content.length();
    }
}