package avilaos.config;

import avilaos.config.SimulationFactory.Simulation;
import avilaos.hardware.Computer;
import avilaos.kernel.disk.DiskPolicyType;
import avilaos.kernel.fs.FsNode;
import avilaos.kernel.process.PCB;
import avilaos.kernel.process.ProcessState;
import avilaos.kernel.process.TerminationCause;
import java.io.File;
import java.io.IOException;

/**
 * Prueba integrada del Proyecto 2 con los DOS JSON del enunciado (F3.4): se carga el sistema
 * de archivos y los 8 procesos de E/S, se corre la simulación completa (con el reloj manejado
 * a mano para que sea determinista) y se comprueba el resultado con cada política de disco.
 *
 * Se ejecuta desde la carpeta del proyecto (usa config/p2/...). Termina con código 1 si algo falla.
 */
public class IntegracionP2Prueba {

    private static final String PROCESOS = "config/p2/procesos_enunciado.json";
    private static final String ARCHIVOS = "config/p2/sistema_archivos_enunciado.json";

    /** Velocidad del cabezal de las pruebas 1 y 2 (el JSON del enunciado no la trae; ver la prueba 3). */
    private static final int VELOCIDAD = 5;

    private static int total;
    private static int fallos;

    private static long cycle;
    private static StringBuilder log;

    public static void main(String[] args) throws IOException {
        conFifo();
        conTodasLasPoliticas();
        velocidadDelCabezalYDeadlines();

        System.out.println();
        System.out.println(total + " comprobaciones, " + fallos + " fallos");
        if (fallos > 0) {
            System.exit(1);
        }
    }

    private static void conFifo() throws IOException {
        System.out.println("--- 1. Los 8 procesos del enunciado con FIFO (cabezal a " + VELOCIDAD
                + " posiciones por ciclo) ---");
        Simulation sim = armar(DiskPolicyType.FIFO, VELOCIDAD);
        Computer c = sim.computer;
        PCB[] procesos = todos(c);
        check("arranca con 8 procesos nuevos y el disco lleno (200 de 200 bloques)",
                procesos.length == 8 && c.getFileSystem().getUsedBlocks() == 200);

        correr(c, procesos, 5000);

        boolean todosCompleted = true;
        for (PCB p : procesos) {
            todosCompleted &= p.getState() == ProcessState.TERMINATED
                    && p.getTerminationCause() == TerminationCause.COMPLETED;
        }
        check("los 8 procesos terminan COMPLETED (ninguno por deadline ni por falla)", todosCompleted);
        check("la cola de terminados tiene 8 y no queda nadie en las demás colas",
                c.getKernel().getTerminatedQueue().size() == 8 && c.getKernel().getNewQueue().isEmpty()
                        && c.getKernel().getReadyQueue().isEmpty() && c.getKernel().getBlockedQueue().isEmpty()
                        && c.getCPU().isIdle());
        check("toda la RAM quedó libre", c.getMemoryManager().getUsedMemory() == 0);

        int[] bloques = bloquesAtendidos();
        check("FIFO atiende en orden de llegada: 95, 180, 34, 119, 11, 123, 62, 64",
                igual(bloques, new int[] {95, 180, 34, 119, 11, 123, 62, 64}));
        check("el disco atendió 8 solicitudes, todas con FIFO",
                c.getDisk().getCompletedRequests() == 8 && c.getDisk().getFifoCompleted() == 8);
        check("desplazamiento total 644 (el del caso P1 del enunciado)", c.getDisk().getTotalHeadMovement() == 644);
        check("el cabezal terminó en el bloque 64", c.getDisk().getCurrentHead() == 64);
        check("la espera promedio en la cola es positiva", c.getDisk().getAverageWaitingTime() > 0);

        var fs = c.getFileSystem();
        FsNode notas = fs.findByPath("/usuarios/notas_v2.txt");
        check("notas.txt se renombró a notas_v2.txt (mismos 28 bloques desde el 34, dueño y permiso intactos)",
                fs.findByPath("/usuarios/notas.txt") == null && notas != null && notas.getFirstBlock() == 34
                        && notas.getBlockCount() == 28 && "user1".equals(notas.getOwner())
                        && notas.getPermission() == avilaos.kernel.fs.Permission.PRIVATE);
        FsNode data = fs.findByPath("/usuarios/data_v2.csv");
        check("data.csv se renombró a data_v2.csv (57 bloques desde el 123)",
                fs.findByPath("/usuarios/data.csv") == null && data != null && data.getFirstBlock() == 123
                        && data.getBlockCount() == 57);
        check("img1.png se eliminó y liberó sus 4 bloques",
                fs.findByPath("/usuarios/img1.png") == null && c.getDisk().countFreeFileBlocks() == 4
                        && fs.getUsedBlocks() == 196);
        check("los archivos que solo se leyeron siguen igual",
                fs.findByPath("/sistema/config.sys") != null && fs.findByPath("/sistema/boot.bin") != null
                        && fs.findByPath("/sistema/script.py") != null && fs.findByPath("/sistema/core.dll") != null
                        && fs.findByPath("/usuarios/video.mp4") != null);
        check("quedan 8 archivos y 2 directorios", fs.countFiles() == 8 && fs.countDirectories() == 2);
        check("el log cuenta cada solicitud con su política",
                log.indexOf("El disco atiende la solicitud de " + procesos[0].getTag()
                        + " (READ /sistema/config.sys) en el bloque 95 (FIFO)") >= 0);
        check("el log cuenta la operación UPDATE terminada",
                log.indexOf("renombra /usuarios/notas.txt como notas_v2.txt") >= 0);

        // El FS final se puede guardar en el formato del enunciado y volver a cargar.
        FileSystemConfig guardado = FileSystemConfig.fromState(fs, c.getDisk());
        guardado.validate();
        check("el estado final se guarda en el JSON del enunciado (8 archivos + 2 directorios)",
                guardado.fileSystem.length == 10 && guardado.diskConfig.initialHead == 64);
    }

    private static void conTodasLasPoliticas() throws IOException {
        System.out.println("--- 2. El mismo escenario con cada política de disco ---");
        System.out.println("  (política | desplazamiento | espera promedio | ciclos)");
        long[] desplazamiento = new long[DiskPolicyType.values().length];
        DiskPolicyType[] politicas = DiskPolicyType.values();
        for (int i = 0; i < politicas.length; i++) {
            DiskPolicyType politica = politicas[i];
            Simulation sim = armar(politica, VELOCIDAD);
            Computer c = sim.computer;
            PCB[] procesos = todos(c);
            correr(c, procesos, 8000);

            boolean todosCompleted = true;
            for (PCB p : procesos) {
                todosCompleted &= p.getTerminationCause() == TerminationCause.COMPLETED;
            }
            desplazamiento[i] = c.getDisk().getTotalHeadMovement();
            System.out.println("  " + politica.getLabel() + " | " + desplazamiento[i] + " | "
                    + String.format("%.1f", c.getDisk().getAverageWaitingTime()) + " | " + cycle);

            check(politica.getLabel() + ": los 8 procesos terminan COMPLETED", todosCompleted);
            check(politica.getLabel() + ": el sistema de archivos queda igual que con FIFO",
                    c.getFileSystem().findByPath("/usuarios/notas_v2.txt") != null
                            && c.getFileSystem().findByPath("/usuarios/data_v2.csv") != null
                            && c.getFileSystem().findByPath("/usuarios/img1.png") == null
                            && c.getFileSystem().getUsedBlocks() == 196);
            check(politica.getLabel() + ": el disco atendió 8 solicitudes con esa política",
                    c.getDisk().getCompletedRequests() == 8 && cuentaDe(c, politica) == 8);
        }
        long fifo = desplazamiento[DiskPolicyType.FIFO.ordinal()];
        long sstf = desplazamiento[DiskPolicyType.SSTF.ordinal()];
        check("SSTF se mueve menos que FIFO", sstf < fifo);
    }

    /**
     * Hallazgo documentado: los procesos del enunciado traen deadline 500 y el JSON no trae la
     * velocidad del cabezal. Con 1 posición por ciclo (el valor por defecto del plan), solo el
     * desplazamiento de FIFO ya son 644 ciclos, y los últimos procesos mueren por deadline.
     */
    private static void velocidadDelCabezalYDeadlines() throws IOException {
        System.out.println("--- 3. Velocidad del cabezal y deadlines del enunciado (FIFO) ---");
        System.out.println("  (posiciones por ciclo | procesos que terminan bien de 8 | ciclo en que termina todo)");
        int[] velocidades = {1, 2, 3, 5, 10};
        int[] completados = new int[velocidades.length];
        for (int i = 0; i < velocidades.length; i++) {
            Simulation sim = armar(DiskPolicyType.FIFO, velocidades[i]);
            PCB[] procesos = todos(sim.computer);
            correr(sim.computer, procesos, 3000);
            for (PCB p : procesos) {
                if (p.getTerminationCause() == TerminationCause.COMPLETED) {
                    completados[i]++;
                }
            }
            System.out.println("  " + velocidades[i] + " | " + completados[i] + " | " + cycle);
        }
        check("con 1 posición por ciclo NO alcanzan los 500 ciclos de deadline para los 8 procesos",
                completados[0] < 8);
        check("con 10 posiciones por ciclo terminan los 8", completados[velocidades.length - 1] == 8);
    }

    // ------------------------------------------------------------------

    private static Simulation armar(DiskPolicyType politica, int velocidad) throws IOException {
        ProcessesConfig procesos = ConfigStore.loadProcesses(new File(PROCESOS));
        procesos.globalConfig.initialDiskPolicy = politica.getLabel();
        procesos.globalConfig.headSpeed = velocidad;
        FileSystemConfig archivos = ConfigStore.loadFileSystem(new File(ARCHIVOS));
        Simulation sim = SimulationFactory.createP2(procesos, archivos);
        cycle = 0;
        log = new StringBuilder();
        sim.computer.addEventListener((ciclo, mensaje) -> log.append(mensaje).append('\n'));
        return sim;
    }

    private static PCB[] todos(Computer c) {
        Object[] cola = c.getKernel().getNewQueue().toArray();
        PCB[] result = new PCB[cola.length];
        for (int i = 0; i < cola.length; i++) {
            result[i] = (PCB) cola[i];
        }
        return result;
    }

    /** Un ciclo de reloj = Computer.onTick + Disk.onTick, en el mismo orden que el reloj real. */
    private static void correr(Computer c, PCB[] procesos, int maxCiclos) {
        while (cycle < maxCiclos) {
            boolean all = true;
            for (PCB p : procesos) {
                all &= p.getState() == ProcessState.TERMINATED;
            }
            if (all) {
                return;
            }
            cycle++;
            c.onTick(cycle);
            c.getDisk().onTick(cycle);
        }
    }

    /** Los bloques de las solicitudes en el orden en que el disco las atendió (según el log). */
    private static int[] bloquesAtendidos() {
        int[] result = new int[0];
        String marca = " en el bloque ";
        String texto = log.toString();
        int pos = 0;
        while (true) {
            int linea = texto.indexOf("El disco atiende la solicitud de", pos);
            if (linea < 0) {
                break;
            }
            int b = texto.indexOf(marca, linea) + marca.length();
            int fin = b;
            while (Character.isDigit(texto.charAt(fin))) {
                fin++;
            }
            int[] mayor = new int[result.length + 1];
            System.arraycopy(result, 0, mayor, 0, result.length);
            mayor[result.length] = Integer.parseInt(texto.substring(b, fin));
            result = mayor;
            pos = fin;
        }
        return result;
    }

    private static int cuentaDe(Computer c, DiskPolicyType politica) {
        switch (politica) {
            case FIFO:
                return c.getDisk().getFifoCompleted();
            case SSTF:
                return c.getDisk().getSstfCompleted();
            case SCAN:
                return c.getDisk().getScanCompleted();
            case CSCAN:
                return c.getDisk().getCscanCompleted();
            default:
                return c.getDisk().getLookCompleted();
        }
    }

    private static boolean igual(int[] a, int[] b) {
        if (a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }
        return true;
    }

    private static void check(String name, boolean ok) {
        total++;
        if (!ok) {
            fallos++;
        }
        System.out.println((ok ? "  OK   " : "  FALLA ") + name);
        if (!ok && log != null && log.length() < 6000) {
            System.out.println("    ---- log ----");
            System.out.println("    " + log.toString().replace("\n", "\n    "));
        }
    }
}
