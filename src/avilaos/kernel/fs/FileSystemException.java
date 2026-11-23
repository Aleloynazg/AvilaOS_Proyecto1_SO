package avilaos.kernel.fs;

/**
 * Error de una operación del sistema de archivos (ruta inexistente, nombre
 * repetido, disco lleno...). El mensaje explica el motivo y se muestra en el
 * log y en la interfaz; la simulación no se detiene.
 */
public class FileSystemException extends RuntimeException {

    public FileSystemException(String message) {
        super(message);
    }
}
