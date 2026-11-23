package avilaos.kernel.process;

import avilaos.kernel.fs.NodeType;
import avilaos.kernel.fs.Permission;

/**
 * Prueba de los procesos de E/S de archivos (F3.1): el PCB lleva la operación y la ruta,
 * la fábrica valida los datos y el modelo de instrucciones pide la operación al disco
 * en la primera instrucción. Ejecutar con Run File; termina con código 1 si algo falla.
 */
public class ProcesoArchivosPrueba {

    private static int total;
    private static int fallos;

    public static void main(String[] args) {
        System.out.println("--- 1. READ con los valores por defecto ---");
        PCB read = ProcessFactory.fileIo("Proc_95", 1, 5, 16, 500, 2, IoOperation.READ, "/sistema/config.sys");
        check("el tipo es FILE_IO", read.getType() == ProcessType.FILE_IO && read.isFileIo());
        check("operación READ", read.getIoOperation() == IoOperation.READ);
        check("ruta guardada", "/sistema/config.sys".equals(read.getTargetPath()));
        check("describeOperation", "READ /sistema/config.sys".equals(read.describeOperation()));
        check("sin nombre nuevo", read.getNewName() == null);
        check("dueño por defecto admin y permiso público",
                "admin".equals(read.getIoOwner()) && read.getIoPermission() == Permission.PUBLIC);
        check("memoria, deadline e instrucciones del enunciado",
                read.getMemoryRequired() == 16 && read.getInitialDeadline() == 500
                        && read.getTotalInstructions() == 2);
        check("empieza en NEW", read.getState() == ProcessState.NEW);

        System.out.println("--- 2. Instrucciones: la primera pide el disco (modo SO) ---");
        Instruction first = InstructionModel.next(read);
        check("primera = DISK_REQUEST", first.getType() == InstructionType.DISK_REQUEST);
        check("DISK_REQUEST es modo kernel", first.isKernelMode());
        read.advance();
        Instruction second = InstructionModel.next(read);
        check("segunda = NORMAL", second.getType() == InstructionType.NORMAL && !second.isKernelMode());
        read.advance();
        check("terminó tras 2 instrucciones", read.isFinished());

        PCB single = ProcessFactory.fileIo("Una", 1, 5, 8, 100, 1, IoOperation.DELETE, "/usuarios/img1.png");
        check("con 1 instrucción, esa es DISK_REQUEST",
                InstructionModel.next(single).getType() == InstructionType.DISK_REQUEST);

        System.out.println("--- 3. CREATE de archivo y de directorio ---");
        PCB create = ProcessFactory.fileIo("Nuevo", 1, 3, 16, 500, 2, IoOperation.CREATE, "/usuarios/tesis.pdf",
                null, NodeType.FILE, 12, "user1", Permission.PRIVATE);
        check("CREATE de archivo guarda el tamaño", create.getFileBlocks() == 12
                && create.getTargetType() == NodeType.FILE);
        check("CREATE guarda dueño y permiso", "user1".equals(create.getIoOwner())
                && create.getIoPermission() == Permission.PRIVATE);
        PCB dir = ProcessFactory.fileIo("Carpeta", 1, 3, 16, 500, 2, IoOperation.CREATE, "/usuarios/docs",
                null, NodeType.DIRECTORY, 99, "user1", Permission.PUBLIC);
        check("CREATE de directorio ignora el tamaño", dir.getTargetType() == NodeType.DIRECTORY
                && dir.getFileBlocks() == 0);
        PCB blank = ProcessFactory.fileIo("Vacio", 1, 3, 16, 500, 2, IoOperation.CREATE, "/x.txt",
                null, null, 3, "  ", null);
        check("tipo, dueño y permiso vacíos usan los valores por defecto", blank.getTargetType() == NodeType.FILE
                && "admin".equals(blank.getIoOwner()) && blank.getIoPermission() == Permission.PUBLIC);

        System.out.println("--- 4. UPDATE con y sin nombre nuevo ---");
        PCB upd = ProcessFactory.fileIo("Renombrar", 1, 5, 16, 500, 2, IoOperation.UPDATE, "/usuarios/notas.txt",
                "apuntes.txt", NodeType.FILE, 0, null, null);
        check("UPDATE guarda el nombre nuevo", "apuntes.txt".equals(upd.getNewName()));
        PCB upd2 = ProcessFactory.fileIo("Renombrar2", 1, 5, 16, 500, 2, IoOperation.UPDATE, "/usuarios/notas.txt");
        check("UPDATE sin nombre nuevo lo deja para el kernel", upd2.getNewName() == null);

        System.out.println("--- 5. Datos inválidos ---");
        checkRechaza("operación nula", () -> ProcessFactory.fileIo("a", 1, 5, 16, 500, 2, null, "/a"));
        checkRechaza("SUSPEND no lo pide un usuario",
                () -> ProcessFactory.fileIo("a", 1, 5, 16, 500, 2, IoOperation.SUSPEND, "/a"));
        checkRechaza("RESUME no lo pide un usuario",
                () -> ProcessFactory.fileIo("a", 1, 5, 16, 500, 2, IoOperation.RESUME, "/a"));
        checkRechaza("ruta sin barra inicial",
                () -> ProcessFactory.fileIo("a", 1, 5, 16, 500, 2, IoOperation.READ, "usuarios/a"));
        checkRechaza("ruta nula", () -> ProcessFactory.fileIo("a", 1, 5, 16, 500, 2, IoOperation.READ, null));
        checkRechaza("la raíz no es un destino", () -> ProcessFactory.fileIo("a", 1, 5, 16, 500, 2,
                IoOperation.DELETE, "/"));
        checkRechaza("CREATE de archivo sin tamaño", () -> ProcessFactory.fileIo("a", 1, 5, 16, 500, 2,
                IoOperation.CREATE, "/a", null, NodeType.FILE, 0, null, null));
        checkRechaza("cero instrucciones",
                () -> ProcessFactory.fileIo("a", 1, 5, 16, 500, 0, IoOperation.READ, "/a"));

        System.out.println("--- 6. Los otros tipos no cambian ---");
        PCB cpu = ProcessFactory.cpuBound("Calculo", 1, 1, 100, 50, 4);
        check("CPU bound: sin operación", !cpu.isFileIo() && cpu.getIoOperation() == null
                && "-".equals(cpu.describeOperation()));
        PCB io = ProcessFactory.ioBound("Lector", 1, 1, 100, 50, 7, 3, 2);
        check("I/O bound: sin operación y sigue pidiendo E/S cada 3",
                io.getIoOperation() == null && InstructionModel.next(io).getType() == InstructionType.NORMAL);

        System.out.println("--- 7. Terminación por falla de E/S ---");
        PCB failed = ProcessFactory.fileIo("Falla", 1, 5, 16, 500, 2, IoOperation.READ, "/no/existe");
        failed.terminate(TerminationCause.IO_FAILED, 12);
        check("queda TERMINATED con causa IO_FAILED",
                failed.getState() == ProcessState.TERMINATED
                        && failed.getTerminationCause() == TerminationCause.IO_FAILED
                        && failed.getFinishCycle() == 12);

        System.out.println();
        System.out.println(total + " comprobaciones, " + fallos + " fallos");
        if (fallos > 0) {
            System.exit(1);
        }
    }

    private static void check(String name, boolean ok) {
        total++;
        if (!ok) {
            fallos++;
        }
        System.out.println((ok ? "  OK   " : "  FALLA ") + name);
    }

    private static void checkRechaza(String name, Runnable action) {
        boolean rejected = false;
        try {
            action.run();
        } catch (IllegalArgumentException e) {
            rejected = true;
        }
        check(name, rejected);
    }
}
