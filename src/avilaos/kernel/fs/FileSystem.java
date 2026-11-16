/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.kernel.fs;

/**
 *
 * @author alexandraloynaz
 */
public class FileSystem {
        private Directory root;

    public FileSystem() {
        // El directorio raíz no tiene directorio padre.
        root = new Directory("/", Permission.PUBLIC, null);
    }

    public Directory getRoot() {
        return root;
    }

    /**
     * Busca un archivo o directorio utilizando su ruta.
     */
    public FsNode findByPath(String path) {

        if (path == null || path.isEmpty()) {
            return null;
        }

        if (path.equals("/")) {
            return root;
        }

        // Eliminar la primera barra de la ruta.
        String cleanPath = path;

        if (cleanPath.startsWith("/")) {
            cleanPath = cleanPath.substring(1);
        }

        String[] names = cleanPath.split("/");

        Directory currentDirectory = root;

        for (int i = 0; i < names.length; i++) {

            if (names[i].isEmpty()) {
                return null;
            }

            FsNode found = currentDirectory.findChild(names[i]);

            if (found == null) {
                return null;
            }

            // Si todavía quedan partes de la ruta,
            // el elemento encontrado debe ser un directorio.
            if (i < names.length - 1) {

                if (found instanceof Directory) {
                    currentDirectory = (Directory) found;
                } else {
                    return null;
                }

            } else {
                return found;
            }
        }

        return null;
    }
}
