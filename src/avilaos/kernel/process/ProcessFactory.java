package avilaos.kernel.process;

import avilaos.kernel.fs.NodeType;
import avilaos.kernel.fs.Permission;

/**
 * Crea procesos de cada tipo con sus parámetros. La usa el formulario
 * "Crear proceso" de la GUI y la carga desde el archivo de configuración.
 */
public final class ProcessFactory {

    private ProcessFactory() { }

    public static PCB cpuBound(String name, int computerId, int priority, int memory,
                               int deadline, int instructions) {
        requirePositive(instructions, "instrucciones");
        return new PCB(name, computerId, ProcessType.CPU_BOUND, priority, memory, deadline, instructions);
    }

    /** ioEvery = cada cuántas instrucciones pide E/S; ioDuration = cuántos ciclos dura la E/S. */
    public static PCB ioBound(String name, int computerId, int priority, int memory,
                              int deadline, int instructions, int ioEvery, int ioDuration) {
        requirePositive(instructions, "instrucciones");
        requirePositive(ioEvery, "cada cuántas instrucciones pide E/S");
        requirePositive(ioDuration, "duración de la E/S");
        PCB p = new PCB(name, computerId, ProcessType.IO_BOUND, priority, memory, deadline, instructions);
        p.setIoParameters(ioEvery, ioDuration);
        return p;
    }

    /**
     * Proceso de E/S de archivos. Su primera instrucción pide la operación al disco (en modo
     * sistema operativo) y el proceso queda bloqueado hasta que el disco la atiende.
     *
     * @param operation CREATE, READ, UPDATE o DELETE (suspender y reactivar las pide el kernel)
     * @param targetPath ruta absoluta del archivo o directorio, por ejemplo "/usuarios/notas.txt"
     * @param newName UPDATE: nombre nuevo; null para que el kernel elija uno
     * @param targetType CREATE: FILE o DIRECTORY (null = FILE)
     * @param blocks CREATE de archivo: tamaño en bloques (mayor que 0)
     * @param owner CREATE: dueño del elemento nuevo (null = admin)
     * @param permission CREATE: permiso del elemento nuevo (null = público)
     */
    public static PCB fileIo(String name, int computerId, int priority, int memory, int deadline,
                             int instructions, IoOperation operation, String targetPath, String newName,
                             NodeType targetType, int blocks, String owner, Permission permission) {
        requirePositive(instructions, "instrucciones");
        if (operation == null) {
            throw new IllegalArgumentException("Falta la operación del proceso de E/S");
        }
        if (operation == IoOperation.SUSPEND || operation == IoOperation.RESUME) {
            throw new IllegalArgumentException("La operación " + operation
                    + " no la pide un proceso de usuario: la genera el planificador a mediano plazo");
        }
        if (targetPath == null || !targetPath.startsWith("/") || targetPath.length() < 2) {
            throw new IllegalArgumentException("La ruta del archivo debe empezar con / y no puede ser la raíz"
                    + " (valor: " + targetPath + ")");
        }
        NodeType type = targetType == null ? NodeType.FILE : targetType;
        boolean createsFile = operation == IoOperation.CREATE && type == NodeType.FILE;
        if (createsFile) {
            requirePositive(blocks, "tamaño del archivo (bloques)");
        }
        PCB p = new PCB(name, computerId, ProcessType.FILE_IO, priority, memory, deadline, instructions);
        p.setFileOperation(operation, targetPath, newName, type, createsFile ? blocks : 0, owner, permission);
        return p;
    }

    /** Atajo para READ, DELETE y UPDATE (con nombre nuevo elegido por el kernel) de un archivo existente. */
    public static PCB fileIo(String name, int computerId, int priority, int memory, int deadline,
                             int instructions, IoOperation operation, String targetPath) {
        return fileIo(name, computerId, priority, memory, deadline, instructions, operation,
                targetPath, null, NodeType.FILE, 0, null, null);
    }

    private static void requirePositive(int value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException("El campo '" + field + "' debe ser mayor que 0");
        }
    }
}