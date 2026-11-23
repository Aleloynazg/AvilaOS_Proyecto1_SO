package avilaos.config;

import avilaos.config.SimulationFactory.Simulation;
import avilaos.hardware.disk.HeadDirection;
import avilaos.kernel.disk.DiskPolicyType;
import avilaos.kernel.process.IoOperation;
import avilaos.kernel.process.PCB;
import avilaos.kernel.process.ProcessType;
import avilaos.kernel.scheduler.SchedulingPolicyType;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Prueba del JSON de procesos y configuración (F3.3): carga el del enunciado, rechaza
 * archivos inválidos con un mensaje claro, arma el sistema a partir de los DOS JSON y
 * comprueba que guardar y volver a cargar deja lo mismo.
 *
 * Se ejecuta desde la carpeta del proyecto (usa config/p2/...). Termina con código 1 si algo falla.
 */
public class ProcesosJsonPrueba {

    private static final String PROCESOS = "config/p2/procesos_enunciado.json";
    private static final String ARCHIVOS = "config/p2/sistema_archivos_enunciado.json";

    private static final String GLOBAL_OK =
            "{\"cycleDurationMs\":300,\"swapSizeBlocks\":100,\"initialDiskPolicy\":\"FIFO\"}";
    private static final String COMPUTER_OK = "{\"ramCapacity\":1024,\"policy\":\"FCFS\",\"quantum\":0}";
    private static final String PROCESO_OK = "{\"name\":\"P\",\"memory\":16,\"deadline\":500,\"instructions\":2,"
            + "\"targetPath\":\"/sistema/config.sys\",\"operation\":\"READ\"}";

    private static int total;
    private static int fallos;

    public static void main(String[] args) throws IOException {
        cargarEnunciado();
        rechazos();
        variantesValidas();
        armarSistemaConLosDosJson();
        guardarYVolverACargar();

        System.out.println();
        System.out.println(total + " comprobaciones, " + fallos + " fallos");
        if (fallos > 0) {
            System.exit(1);
        }
    }

    private static void cargarEnunciado() throws IOException {
        System.out.println("--- 1. El JSON de procesos del enunciado ---");
        ProcessesConfig config = ConfigStore.loadProcesses(new File(PROCESOS));
        check("ciclo de 300 ms, swap de 100 bloques y política de disco FIFO",
                config.globalConfig.cycleDurationMs == 300 && config.globalConfig.swapSizeBlocks == 100
                        && "FIFO".equals(config.globalConfig.initialDiskPolicy));
        check("computador de 1024 con FCFS y quantum 0",
                config.computer.ramCapacity == 1024 && "FCFS".equals(config.computer.policy)
                        && config.computer.quantum == 0);
        check("8 procesos", config.processes.length == 8);
        ProcessesConfig.ProcessEntry primero = config.processes[0];
        check("el primero es Proc_95: READ /sistema/config.sys",
                "Proc_95".equals(primero.name) && "READ".equals(primero.operation)
                        && "/sistema/config.sys".equals(primero.targetPath) && primero.memory == 16
                        && primero.deadline == 500 && primero.instructions == 2);
        check("el cuarto es Proc_119: DELETE /usuarios/img1.png",
                "DELETE".equals(config.processes[3].operation)
                        && "/usuarios/img1.png".equals(config.processes[3].targetPath));
        check("hay 2 UPDATE (Proc_34 y Proc_123)",
                "UPDATE".equals(config.processes[2].operation) && "UPDATE".equals(config.processes[5].operation));

        PCB[] pcbs = config.createProcesses(1);
        check("se crean 8 procesos de E/S de archivos",
                pcbs.length == 8 && pcbs[0].getType() == ProcessType.FILE_IO
                        && pcbs[0].getIoOperation() == IoOperation.READ);
        check("prioridad por defecto " + ProcessesConfig.DEFAULT_PRIORITY,
                pcbs[0].getPriority() == ProcessesConfig.DEFAULT_PRIORITY);
    }

    private static void rechazos() throws IOException {
        System.out.println("--- 2. Archivos inválidos: se rechazan con el motivo ---");
        rechaza("el JSON del sistema de archivos en lugar del de procesos",
                new String(Files.readAllBytes(new File(ARCHIVOS).toPath()), StandardCharsets.UTF_8),
                "Falta la sección 'globalConfig'");
        rechaza("texto que no es JSON", "esto no es json", "no es un JSON válido");
        rechaza("archivo vacío", "", "está vacío");
        rechaza("falta 'computer'", "{\"globalConfig\":" + GLOBAL_OK + "}", "Falta la sección 'computer'");
        rechaza("ciclo demasiado corto", doc(GLOBAL_OK.replace("300", "10"), COMPUTER_OK, PROCESO_OK), "cycleDurationMs");
        rechaza("swap negativo", doc(GLOBAL_OK.replace("100", "-1"), COMPUTER_OK, PROCESO_OK), "swapSizeBlocks");
        rechaza("política de disco desconocida", doc(GLOBAL_OK.replace("FIFO", "XYZ"), COMPUTER_OK, PROCESO_OK),
                "initialDiskPolicy");
        rechaza("velocidad del cabezal 0", doc(GLOBAL_OK.replace("}", ",\"headSpeed\":0}"), COMPUTER_OK, PROCESO_OK),
                "headSpeed");
        rechaza("velocidad del cabezal 101", doc(GLOBAL_OK.replace("}", ",\"headSpeed\":101}"), COMPUTER_OK, PROCESO_OK),
                "headSpeed");
        rechaza("dirección inicial inválida",
                doc(GLOBAL_OK.replace("}", ",\"initialDirection\":\"LADO\"}"), COMPUTER_OK, PROCESO_OK),
                "initialDirection");
        rechaza("RAM demasiado pequeña", doc(GLOBAL_OK, COMPUTER_OK.replace("1024", "10"), PROCESO_OK), "ramCapacity");
        rechaza("política de planificación desconocida", doc(GLOBAL_OK, COMPUTER_OK.replace("FCFS", "LIFO"), PROCESO_OK),
                "no es una política conocida");
        rechaza("Round Robin con quantum 0",
                doc(GLOBAL_OK, COMPUTER_OK.replace("FCFS", "ROUND_ROBIN"), PROCESO_OK), "quantum");
        rechaza("proceso sin nombre", doc(GLOBAL_OK, COMPUTER_OK, PROCESO_OK.replace("\"name\":\"P\",", "")),
                "falta el 'name'");
        rechaza("proceso sin memoria", doc(GLOBAL_OK, COMPUTER_OK, PROCESO_OK.replace("\"memory\":16,", "")),
                "Proceso 'P': falta 'memory'");
        rechaza("memoria mayor que la RAM", doc(GLOBAL_OK, COMPUTER_OK, PROCESO_OK.replace("16", "2000")), "memory");
        rechaza("deadline 0", doc(GLOBAL_OK, COMPUTER_OK, PROCESO_OK.replace("500", "0")), "deadline");
        rechaza("0 instrucciones", doc(GLOBAL_OK, COMPUTER_OK, PROCESO_OK.replace("\"instructions\":2", "\"instructions\":0")),
                "instructions");
        rechaza("prioridad fuera de rango", doc(GLOBAL_OK, COMPUTER_OK, agregar(PROCESO_OK, "\"priority\":11")),
                "priority");
        rechaza("operación desconocida", doc(GLOBAL_OK, COMPUTER_OK, PROCESO_OK.replace("READ", "COPY")),
                "'operation' debe ser");
        rechaza("SUSPEND no lo pide un proceso de usuario", doc(GLOBAL_OK, COMPUTER_OK, PROCESO_OK.replace("READ", "SUSPEND")),
                "'operation' debe ser");
        rechaza("falta la operación", doc(GLOBAL_OK, COMPUTER_OK, PROCESO_OK.replace(",\"operation\":\"READ\"", "")),
                "falta 'operation'");
        rechaza("ruta sin barra inicial", doc(GLOBAL_OK, COMPUTER_OK, PROCESO_OK.replace("/sistema", "sistema")),
                "targetPath");
        rechaza("la raíz como destino", doc(GLOBAL_OK, COMPUTER_OK, PROCESO_OK.replace("/sistema/config.sys", "/")),
                "targetPath");
        rechaza("CREATE de archivo sin tamaño", doc(GLOBAL_OK, COMPUTER_OK, PROCESO_OK.replace("READ", "CREATE")),
                "necesita 'blocks'");
        rechaza("permisos inválidos", doc(GLOBAL_OK, COMPUTER_OK, agregar(PROCESO_OK, "\"permissions\":\"secreto\"")),
                "permissions");
        rechaza("targetType inválido", doc(GLOBAL_OK, COMPUTER_OK, agregar(PROCESO_OK, "\"targetType\":\"LINK\"")),
                "targetType");
        rechaza("tipo de proceso desconocido", doc(GLOBAL_OK, COMPUTER_OK, agregar(PROCESO_OK, "\"type\":\"THREAD\"")),
                "'type' debe ser");
        rechaza("I/O bound sin ioEvery", doc(GLOBAL_OK, COMPUTER_OK,
                "{\"name\":\"IO\",\"type\":\"IO_BOUND\",\"memory\":16,\"deadline\":500,\"instructions\":9,\"ioDuration\":2}"),
                "falta 'ioEvery'");
    }

    private static void variantesValidas() {
        System.out.println("--- 3. Variantes válidas ---");
        ProcessesConfig rr = ConfigStore.parseProcesses(doc(GLOBAL_OK.replace("FIFO", "c-scan"),
                COMPUTER_OK.replace("FCFS", "round robin").replace("\"quantum\":0", "\"quantum\":3"), PROCESO_OK));
        rr.validate();
        check("'round robin' y quantum 3",
                ProcessesConfig.parseSchedulingPolicy(rr.computer.policy) == SchedulingPolicyType.ROUND_ROBIN);
        check("'c-scan' es C-SCAN", ProcessesConfig.parseDiskPolicy(rr.globalConfig.initialDiskPolicy)
                == DiskPolicyType.CSCAN);
        check("'RR' es Round Robin",
                ProcessesConfig.parseSchedulingPolicy("RR") == SchedulingPolicyType.ROUND_ROBIN);
        check("'cscan' y 'LOOK' también valen", ProcessesConfig.parseDiskPolicy("cscan") == DiskPolicyType.CSCAN
                && ProcessesConfig.parseDiskPolicy("LOOK") == DiskPolicyType.LOOK);
        check("sin 'initialDiskPolicy' usa FIFO", ProcessesConfig.parseDiskPolicy(null) == DiskPolicyType.FIFO);

        ProcessesConfig dir = ConfigStore.parseProcesses(doc(GLOBAL_OK, COMPUTER_OK,
                "{\"name\":\"Carpeta\",\"memory\":8,\"deadline\":100,\"instructions\":2,\"targetPath\":\"/usuarios/docs\","
                + "\"operation\":\"CREATE\",\"targetType\":\"DIRECTORY\",\"owner\":\"user1\"}"));
        dir.validate();
        check("CREATE de directorio no necesita 'blocks'", dir.createProcesses(1)[0].getIoOperation() == IoOperation.CREATE);

        ProcessesConfig mix = ConfigStore.parseProcesses(doc(GLOBAL_OK, COMPUTER_OK,
                "{\"name\":\"Calculo\",\"type\":\"CPU_BOUND\",\"memory\":8,\"deadline\":100,\"instructions\":5},"
                + "{\"name\":\"Lector\",\"type\":\"IO_BOUND\",\"memory\":8,\"deadline\":100,\"instructions\":9,"
                + "\"ioEvery\":3,\"ioDuration\":2,\"priority\":2}"));
        PCB[] pcbs = mix.createProcesses(1);
        check("también se cargan procesos CPU bound e I/O bound",
                pcbs[0].getType() == ProcessType.CPU_BOUND && pcbs[1].getType() == ProcessType.IO_BOUND
                        && pcbs[1].getIoEveryCycles() == 3 && pcbs[1].getPriority() == 2);
    }

    private static void armarSistemaConLosDosJson() throws IOException {
        System.out.println("--- 4. El sistema se arma con los DOS JSON ---");
        ProcessesConfig procesos = ConfigStore.loadProcesses(new File(PROCESOS));
        FileSystemConfig archivos = ConfigStore.loadFileSystem(new File(ARCHIVOS));
        Simulation sim = SimulationFactory.createP2(procesos, archivos);

        check("el disco tiene 200 + 100 bloques", sim.computer.getDisk().getTotalBlocks() == 300
                && sim.computer.getDisk().getSwapStart() == 200 && sim.computer.getDisk().getSwapBlockCount() == 100);
        check("el cabezal empieza en el bloque 50", sim.computer.getDisk().getCurrentHead() == 50);
        check("sin 'headSpeed' ni 'initialDirection': 1 posición por ciclo y hacia los bloques mayores",
                sim.computer.getDisk().getHeadSpeed() == 1
                        && sim.computer.getDisk().getHeadDirection() == HeadDirection.UP);
        ProcessesConfig rapido = ConfigStore.parseProcesses(doc(
                GLOBAL_OK.replace("}", ",\"headSpeed\":5,\"initialDirection\":\"down\"}"), COMPUTER_OK, PROCESO_OK));
        Simulation simRapida = SimulationFactory.createP2(rapido, archivos);
        check("'headSpeed' e 'initialDirection' se aplican al disco",
                simRapida.computer.getDisk().getHeadSpeed() == 5
                        && simRapida.computer.getDisk().getHeadDirection() == HeadDirection.DOWN);
        ProcessesConfig descritoDelEnunciado = SimulationFactory.describeProcesses(sim.computer, sim.clock);
        check("con los valores por defecto, describe no escribe 'headSpeed' ni 'initialDirection'",
                descritoDelEnunciado.globalConfig.headSpeed == null
                        && descritoDelEnunciado.globalConfig.initialDirection == null);
        check("política de disco inicial FIFO", sim.computer.getDiskPolicyType() == DiskPolicyType.FIFO);
        check("política de planificación FCFS", sim.computer.getPolicy().getType() == SchedulingPolicyType.FCFS);
        check("RAM de 1024", sim.computer.getMemoryManager().getTotalMemory() == 1024);
        check("ciclo de reloj de 300 ms", sim.clock.getCycleDuration() == 300);
        check("el sistema de archivos tiene 9 archivos y 2 directorios",
                sim.computer.getFileSystem().countFiles() == 9
                        && sim.computer.getFileSystem().countDirectories() == 2);
        check("los 8 procesos esperan en la cola de nuevos", sim.computer.getKernel().getNewQueue().size() == 8);
        PCB primero = sim.computer.getKernel().getNewQueue().peek();
        check("el primero de la cola es Proc_95 (READ /sistema/config.sys)",
                "Proc_95".equals(primero.getName()) && "READ /sistema/config.sys".equals(primero.describeOperation()));

        rechazaCrear("falta el JSON de procesos", null, archivos, "Falta cargar el archivo de procesos");
        rechazaCrear("falta el JSON del sistema de archivos", procesos, null,
                "Falta cargar el archivo del sistema de archivos");
        FileSystemConfig lejos = ConfigStore.parseFileSystem(
                "{\"diskConfig\":{\"totalBlocks\":200,\"initialHead\":350},\"fileSystem\":[]}");
        rechazaCrear("cabezal fuera del disco", procesos, lejos, "queda fuera del disco");
        FileSystemConfig justo = ConfigStore.parseFileSystem(
                "{\"diskConfig\":{\"totalBlocks\":200,\"initialHead\":299},\"fileSystem\":[]}");
        check("el cabezal en el último bloque (zona de swap) es válido",
                SimulationFactory.createP2(procesos, justo).computer.getDisk().getCurrentHead() == 299);
    }

    private static void guardarYVolverACargar() throws IOException {
        System.out.println("--- 5. Guardar y volver a cargar ---");
        String texto = doc(GLOBAL_OK.replace("FIFO", "C-SCAN").replace("300", "250")
                        .replace("}", ",\"headSpeed\":4,\"initialDirection\":\"DOWN\"}"),
                COMPUTER_OK.replace("FCFS", "ROUND_ROBIN").replace("\"quantum\":0", "\"quantum\":3"),
                PROCESO_OK + ","
                + "{\"name\":\"Nuevo\",\"memory\":8,\"deadline\":300,\"instructions\":3,\"targetPath\":\"/usuarios/tesis.pdf\","
                + "\"operation\":\"CREATE\",\"blocks\":12,\"owner\":\"user1\",\"permissions\":\"private\",\"priority\":2},"
                + "{\"name\":\"Carpeta\",\"memory\":8,\"deadline\":300,\"instructions\":2,\"targetPath\":\"/usuarios/docs\","
                + "\"operation\":\"CREATE\",\"targetType\":\"DIRECTORY\"},"
                + "{\"name\":\"Renombrar\",\"memory\":8,\"deadline\":300,\"instructions\":2,\"targetPath\":\"/usuarios/notas.txt\","
                + "\"operation\":\"UPDATE\",\"newName\":\"apuntes.txt\"},"
                + "{\"name\":\"Calculo\",\"type\":\"CPU_BOUND\",\"memory\":8,\"deadline\":100,\"instructions\":5},"
                + "{\"name\":\"Lector\",\"type\":\"IO_BOUND\",\"memory\":8,\"deadline\":100,\"instructions\":9,"
                + "\"ioEvery\":3,\"ioDuration\":2}");
        ProcessesConfig original = ConfigStore.parseProcesses(texto);
        original.validate();
        FileSystemConfig archivos = ConfigStore.loadFileSystem(new File(ARCHIVOS));

        Simulation sim = SimulationFactory.createP2(original, archivos);
        ProcessesConfig descrito = SimulationFactory.describeProcesses(sim.computer, sim.clock);
        check("describe: ciclo, swap, política de disco y computador",
                descrito.globalConfig.cycleDurationMs == 250 && descrito.globalConfig.swapSizeBlocks == 100
                        && "C-SCAN".equals(descrito.globalConfig.initialDiskPolicy)
                        && descrito.computer.ramCapacity == 1024 && "ROUND_ROBIN".equals(descrito.computer.policy)
                        && descrito.computer.quantum == 3);
        check("describe: velocidad y dirección del cabezal",
                descrito.globalConfig.headSpeed != null && descrito.globalConfig.headSpeed == 4
                        && "DOWN".equals(descrito.globalConfig.initialDirection));
        check("describe: los 6 procesos, en el orden en que se crearon", descrito.processes.length == 6
                && "P".equals(descrito.processes[0].name) && "Lector".equals(descrito.processes[5].name));
        ProcessesConfig.ProcessEntry nuevo = descrito.processes[1];
        check("describe: CREATE conserva tamaño, dueño, permiso y prioridad",
                "CREATE".equals(nuevo.operation) && nuevo.blocks == 12 && "user1".equals(nuevo.owner)
                        && "private".equals(nuevo.permissions) && nuevo.priority == 2
                        && "FILE".equals(nuevo.targetType));
        check("describe: UPDATE conserva el nombre nuevo", "apuntes.txt".equals(descrito.processes[3].newName));
        check("describe: un proceso de archivos no escribe 'type'", descrito.processes[0].type == null);

        String json = ConfigStore.toJson(descrito);
        File temporal = File.createTempFile("procesos", ".json");
        try {
            ConfigStore.saveProcesses(descrito, temporal);
            ProcessesConfig recargado = ConfigStore.loadProcesses(temporal);
            check("recargar desde el archivo da el mismo JSON", json.equals(ConfigStore.toJson(recargado)));
            Simulation sim2 = SimulationFactory.createP2(recargado, archivos);
            String json2 = ConfigStore.toJson(SimulationFactory.describeProcesses(sim2.computer, sim2.clock));
            check("armar el sistema otra vez y describirlo da el mismo JSON", json.equals(json2));
            check("la política C-SCAN y los 6 procesos se recuperan",
                    sim2.computer.getDiskPolicyType() == DiskPolicyType.CSCAN
                            && sim2.computer.getKernel().getNewQueue().size() == 6);
        } finally {
            Files.deleteIfExists(temporal.toPath());
        }
    }

    // ------------------------------------------------------------------

    private static String doc(String globalConfig, String computer, String procesos) {
        return "{\"globalConfig\":" + globalConfig + ",\"computer\":" + computer + ",\"processes\":[" + procesos + "]}";
    }

    /** Agrega un campo al final de un objeto JSON de una línea. */
    private static String agregar(String objeto, String campo) {
        return objeto.substring(0, objeto.length() - 1) + "," + campo + "}";
    }

    private static void rechaza(String nombre, String json, String fragmento) {
        String mensaje = null;
        try {
            ConfigStore.parseProcesses(json).validate();
        } catch (IllegalArgumentException e) {
            mensaje = e.getMessage();
        }
        boolean ok = mensaje != null && mensaje.contains(fragmento);
        check("rechaza: " + nombre, ok);
        if (!ok) {
            System.out.println("    esperaba un mensaje con \"" + fragmento + "\" y fue: " + mensaje);
        }
    }

    private static void rechazaCrear(String nombre, ProcessesConfig procesos, FileSystemConfig archivos,
                                     String fragmento) {
        String mensaje = null;
        try {
            SimulationFactory.createP2(procesos, archivos);
        } catch (IllegalArgumentException e) {
            mensaje = e.getMessage();
        }
        boolean ok = mensaje != null && mensaje.contains(fragmento);
        check("createP2 rechaza: " + nombre, ok);
        if (!ok) {
            System.out.println("    esperaba un mensaje con \"" + fragmento + "\" y fue: " + mensaje);
        }
    }

    private static void check(String name, boolean ok) {
        total++;
        if (!ok) {
            fallos++;
        }
        System.out.println((ok ? "  OK   " : "  FALLA ") + name);
    }
}
