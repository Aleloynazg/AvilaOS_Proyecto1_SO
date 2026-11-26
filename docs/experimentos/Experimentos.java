import avilaos.config.ConfigStore;
import avilaos.config.FileSystemConfig;
import avilaos.config.ProcessesConfig;
import avilaos.config.SimulationFactory;
import avilaos.config.SimulationFactory.Simulation;
import avilaos.hardware.Computer;
import avilaos.hardware.ComputerMetrics;
import avilaos.hardware.disk.Disk;
import avilaos.hardware.disk.HeadDirection;
import avilaos.kernel.Kernel;
import avilaos.kernel.disk.DiskPolicyFactory;
import avilaos.kernel.disk.DiskPolicyType;
import avilaos.kernel.disk.DiskRequest;
import avilaos.kernel.disk.DiskScheduler;
import avilaos.kernel.process.IoOperation;
import avilaos.kernel.process.PCB;
import avilaos.kernel.process.ProcessFactory;
import avilaos.kernel.process.ProcessState;
import avilaos.kernel.process.TerminationCause;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.util.Locale;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.StandardChartTheme;
import org.jfree.chart.labels.StandardCategoryItemLabelGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.StandardBarPainter;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

/**
 * Experimentos del Proyecto 2 (F6.1): corre los mismos escenarios con cada política de disco
 * y con y sin suspensión, y escribe las tablas (resultados.md) y las gráficas (graficas/).
 * El reloj se maneja a mano, así que los resultados son siempre los mismos.
 *
 * No forma parte del programa: está fuera de src/. Se ejecuta desde la carpeta del proyecto
 * (ver docs/experimentos/README.md).
 */
public class Experimentos {

    private static final String OUT = "docs/experimentos/";
    private static final String ARCHIVOS = "config/p2/sistema_archivos_enunciado.json";
    private static final String ENUNCIADO = "config/p2/procesos_enunciado.json";
    private static final String SUSPENSION = "config/p2/suspension.json";
    private static final String CARGA = OUT + "carga_suspension.json";

    private static final DiskPolicyType[] POLITICAS = DiskPolicyType.values();
    private static final Color[] COLORES = {
        new Color(0x2a78d6), new Color(0xeb6834), new Color(0x1baf7a), new Color(0xeda100), new Color(0xe87ba4)
    };
    private static final Locale ES = new Locale("es", "ES");
    private static final int MAX_CICLOS = 20000;

    private static PrintWriter md;

    /** Resultado de una corrida completa del sistema. */
    private static class Corrida {
        long ciclos;
        long movimiento;
        double esperaDisco;
        int solicitudes;
        int completados;
        int porDeadline;
        int procesos;
        double usoCpu;
        double respuesta;
        double esperaCpu;
        int suspensiones;
        int reactivaciones;
        String orden = "";
        /** Primer ciclo en que el proceso de nombre "observado" sale de Nuevos y ciclo en que termina. */
        long admision = -1;
        long fin = -1;
        XYSeries serie;
    }

    public static void main(String[] args) throws Exception {
        new File(OUT + "graficas").mkdirs();
        md = new PrintWriter(OUT + "resultados.md", "UTF-8");
        md.println("# Resultados de los experimentos");
        md.println();
        md.println("Generado por `Experimentos.java`. El desplazamiento se mide en bloques y el tiempo en ciclos de reloj.");
        md.println();

        discoAislado("1. Anexo del enunciado", new int[] {82, 17, 43, 140, 24, 16, 190}, "anexo");
        discoAislado("2. Casos P1 a P4 (las 8 solicitudes en cola)", new int[] {95, 180, 34, 119, 11, 123, 62, 64}, "casos");
        sistemaCompleto(5, "3", "enunciado_v5");
        sistemaCompleto(1, "4", "enunciado_v1");
        suspension("5. Suspensión: `suspension.json` (3 procesos, 128 MB)", SUSPENSION, "Urgente", "suspension");
        suspension("6. Suspensión con más carga: `carga_suspension.json` (6 procesos, 128 MB)", CARGA, "Calculo_1", "carga");
        suspensionPorPolitica();

        md.close();
        System.out.println("Listo: " + OUT + "resultados.md y " + OUT + "graficas/");
    }

    // ------------------------------------------------------------------ disco aislado

    private static void discoAislado(String titulo, int[] solicitudes, String nombre) throws IOException {
        md.println("## " + titulo);
        md.println();
        md.print("Disco de 200 bloques, cabezal en el 50 subiendo, 1 posición por ciclo, solicitudes de 1 bloque: ");
        md.println(lista(solicitudes) + ".");
        md.println();
        md.println("| Política | Orden de atención | Desplazamiento | Espera promedio | Espera máxima | Ciclos |");
        md.println("| --- | --- | ---: | ---: | ---: | ---: |");
        XYSeriesCollection lineas = new XYSeriesCollection();
        DefaultCategoryDataset movimiento = new DefaultCategoryDataset();
        DefaultCategoryDataset espera = new DefaultCategoryDataset();
        for (DiskPolicyType tipo : POLITICAS) {
            Disk disk = new Disk(200, 0);
            DiskScheduler scheduler = new DiskScheduler(DiskPolicyFactory.create(tipo));
            disk.setScheduler(scheduler);
            disk.setCurrentHead(50);
            disk.setHeadDirection(HeadDirection.UP);
            for (int bloque : solicitudes) {
                PCB p = ProcessFactory.cpuBound("Proc_" + bloque, 1, 1, 1, 99999, 1);
                scheduler.addRequest(new DiskRequest(p, IoOperation.READ, bloque, 1, 0, null, null, null, null));
            }
            XYSeries serie = new XYSeries(tipo.getLabel(), true, true);
            serie.add(0, 0);
            StringBuilder orden = new StringBuilder();
            int atendidas = 0;
            long esperaMaxima = 0;
            long ciclo = 0;
            DiskRequest enCurso = null;
            long inicio = 0;
            while (ciclo < MAX_CICLOS && atendidas < solicitudes.length) {
                ciclo++;
                disk.onTick(ciclo);
                if (disk.getCurrentRequest() != null && disk.getCurrentRequest() != enCurso) {
                    enCurso = disk.getCurrentRequest();
                    inicio = ciclo;
                }
                serie.addOrUpdate(ciclo, disk.getTotalHeadMovement());
                if (disk.getCompletedRequests() > atendidas) {
                    atendidas = disk.getCompletedRequests();
                    esperaMaxima = Math.max(esperaMaxima, inicio);
                    orden.append(orden.length() > 0 ? ", " : "").append(disk.getCurrentHead());
                }
            }
            md.println("| " + tipo.getLabel() + " | " + orden + " | " + disk.getTotalHeadMovement() + " | "
                    + num(disk.getAverageWaitingTime()) + " | " + esperaMaxima + " | " + ciclo + " |");
            lineas.addSeries(serie);
            movimiento.addValue(disk.getTotalHeadMovement(), "Desplazamiento", tipo.getLabel());
            espera.addValue(disk.getAverageWaitingTime(), "Espera", tipo.getLabel());
        }
        md.println();
        lineas("Desplazamiento acumulado del cabezal (" + (nombre.equals("anexo") ? "anexo" : "casos P1 a P4") + ")",
                lineas, nombre + "_acumulado");
        barras("Desplazamiento total", "Bloques", movimiento, "0", nombre + "_desplazamiento");
        barras("Espera promedio en la cola", "Ciclos", espera, "0.0", nombre + "_espera");
        imagenes(nombre);
    }

    // ------------------------------------------------------------------ sistema completo

    private static void sistemaCompleto(int velocidad, String numero, String nombre) throws Exception {
        md.println("## " + numero + ". Sistema completo con los dos JSON del enunciado, cabezal a " + velocidad
                + (velocidad == 1 ? " posición por ciclo (valor por defecto)" : " posiciones por ciclo"));
        md.println();
        md.println("8 procesos de E/S, FCFS en CPU, 1024 MB de RAM, deadline 500.");
        md.println();
        md.println("| Política de disco | Terminan bien | Mueren por deadline | Solicitudes atendidas | Desplazamiento "
                + "| Espera promedio en disco | Ciclos totales | Uso de CPU |");
        md.println("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |");
        XYSeriesCollection lineas = new XYSeriesCollection();
        DefaultCategoryDataset movimiento = new DefaultCategoryDataset();
        DefaultCategoryDataset espera = new DefaultCategoryDataset();
        String ordenes = "";
        for (DiskPolicyType tipo : POLITICAS) {
            Corrida r = correr(ENUNCIADO, tipo, velocidad, true, null);
            md.println("| " + tipo.getLabel() + " | " + r.completados + " de " + r.procesos + " | " + r.porDeadline
                    + " | " + r.solicitudes + " | " + r.movimiento + " | " + num(r.esperaDisco) + " | " + r.ciclos
                    + " | " + num(r.usoCpu) + " % |");
            lineas.addSeries(r.serie);
            movimiento.addValue(r.movimiento, "Desplazamiento", tipo.getLabel());
            espera.addValue(r.esperaDisco, "Espera", tipo.getLabel());
            ordenes += "- " + tipo.getLabel() + ": " + r.orden + "\n";
        }
        md.println();
        md.println("Orden en que el disco atendió los bloques:");
        md.println();
        md.println(ordenes);
        lineas("Desplazamiento acumulado, JSON del enunciado (velocidad " + velocidad + ")", lineas, nombre + "_acumulado");
        barras("Desplazamiento total (velocidad " + velocidad + ")", "Bloques", movimiento, "0", nombre + "_desplazamiento");
        barras("Espera promedio en la cola (velocidad " + velocidad + ")", "Ciclos", espera, "0.0", nombre + "_espera");
        imagenes(nombre);
    }

    // ------------------------------------------------------------------ suspensión

    private static void suspension(String titulo, String procesos, String observado, String nombre) throws Exception {
        md.println("## " + titulo);
        md.println();
        ProcessesConfig base = ConfigStore.loadProcesses(new File(procesos));
        md.println("Política de disco FIFO, " + base.computer.policy + " en CPU, cabezal a " + base.globalConfig.headSpeed
                + " posiciones por ciclo. La columna \"" + observado + "\" muestra cuándo ese proceso entra a RAM y cuándo termina.");
        md.println();
        md.println("| Escenario | Suspensiones | Reactivaciones | " + observado + ": admitido / termina | Ciclos totales "
                + "| Uso de CPU | Respuesta promedio | Desplazamiento | Solicitudes al disco |");
        md.println("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |");
        String[] escenarios = {"128 MB con planificador a mediano plazo", "128 MB sin suspensión",
            "1024 MB (la RAM sobra)"};
        for (int i = 0; i < escenarios.length; i++) {
            Corrida r = correr(procesos, DiskPolicyType.FIFO, 0, i != 1, observado, i == 2 ? 1024 : 0);
            md.println("| " + escenarios[i] + " | " + r.suspensiones + " | " + r.reactivaciones + " | " + r.admision
                    + " / " + r.fin + " | " + r.ciclos + " | " + num(r.usoCpu) + " % | " + num(r.respuesta) + " | "
                    + r.movimiento + " | " + r.solicitudes + " |");
            if (r.completados != r.procesos) {
                md.println("| ↳ ojo: terminan bien " + r.completados + " de " + r.procesos + " | | | | | | | | |");
            }
        }
        md.println();
    }

    private static void suspensionPorPolitica() throws Exception {
        md.println("## 7. `carga_suspension.json` con cada política de disco (128 MB, con suspensión)");
        md.println();
        md.println("| Política de disco | Suspensiones | Reactivaciones | Desplazamiento | Espera promedio en disco "
                + "| Ciclos totales | Uso de CPU |");
        md.println("| --- | ---: | ---: | ---: | ---: | ---: | ---: |");
        for (DiskPolicyType tipo : POLITICAS) {
            Corrida r = correr(CARGA, tipo, 0, true, null);
            md.println("| " + tipo.getLabel() + " | " + r.suspensiones + " | " + r.reactivaciones + " | " + r.movimiento
                    + " | " + num(r.esperaDisco) + " | " + r.ciclos + " | " + num(r.usoCpu) + " % |");
        }
        md.println();
    }

    // ------------------------------------------------------------------ motor

    private static Corrida correr(String procesos, DiskPolicyType politica, int velocidad, boolean suspender,
            String observado) throws Exception {
        return correr(procesos, politica, velocidad, suspender, observado, 0);
    }

    /**
     * @param velocidad posiciones por ciclo; 0 deja la del JSON
     * @param suspender false apaga el planificador a mediano plazo (solo para el experimento)
     * @param ram       MB de RAM; 0 deja la del JSON
     */
    private static Corrida correr(String procesos, DiskPolicyType politica, int velocidad, boolean suspender,
            String observado, int ram) throws Exception {
        ProcessesConfig config = ConfigStore.loadProcesses(new File(procesos));
        config.globalConfig.initialDiskPolicy = politica.getLabel();
        if (velocidad > 0) {
            config.globalConfig.headSpeed = velocidad;
        }
        if (ram > 0) {
            config.computer.ramCapacity = ram;
        }
        FileSystemConfig archivos = ConfigStore.loadFileSystem(new File(ARCHIVOS));
        Simulation sim = SimulationFactory.createP2(config, archivos);
        Computer c = sim.computer;
        if (!suspender) {
            // El programa no tiene un interruptor para esto: se quita el planificador a mediano
            // plazo del kernel por reflexión, solo en esta corrida.
            Field medium = Kernel.class.getDeclaredField("medium");
            medium.setAccessible(true);
            medium.set(c.getKernel(), null);
        }
        StringBuilder log = new StringBuilder();
        c.addEventListener((ciclo, mensaje) -> log.append(mensaje).append('\n'));
        Object[] cola = c.getKernel().getNewQueue().toArray();

        Corrida r = new Corrida();
        r.procesos = cola.length;
        r.serie = new XYSeries(politica.getLabel(), true, true);
        r.serie.add(0, 0);
        long ciclo = 0;
        boolean terminados = false;
        while (!terminados && ciclo < MAX_CICLOS) {
            ciclo++;
            c.onTick(ciclo);
            c.getDisk().onTick(ciclo);
            r.serie.addOrUpdate(ciclo, c.getDisk().getTotalHeadMovement());
            terminados = true;
            for (Object o : cola) {
                PCB p = (PCB) o;
                terminados &= p.getState() == ProcessState.TERMINATED;
                if (observado != null && p.getName().equals(observado)) {
                    if (r.admision < 0 && p.getState() != ProcessState.NEW) {
                        r.admision = ciclo;
                    }
                    if (r.fin < 0 && p.getState() == ProcessState.TERMINATED) {
                        r.fin = ciclo;
                    }
                }
            }
        }
        if (!terminados) {
            throw new IllegalStateException(procesos + " no termina en " + MAX_CICLOS + " ciclos");
        }
        for (Object o : cola) {
            PCB p = (PCB) o;
            if (p.getTerminationCause() == TerminationCause.COMPLETED) {
                r.completados++;
            } else if (p.getTerminationCause() == TerminationCause.DEADLINE_MISSED) {
                r.porDeadline++;
            }
        }
        ComputerMetrics m = c.getMetrics();
        r.ciclos = ciclo;
        r.movimiento = c.getDisk().getTotalHeadMovement();
        r.esperaDisco = c.getDisk().getAverageWaitingTime();
        r.solicitudes = c.getDisk().getCompletedRequests();
        r.usoCpu = m.getPorcentajeUsoCPU();
        r.respuesta = m.getPromedioTiempoRespuesta();
        r.esperaCpu = m.getPromedioTiempoEspera();
        r.suspensiones = c.getKernel().getSuspensionCount();
        r.reactivaciones = c.getKernel().getReactivationCount();
        r.orden = bloquesAtendidos(log.toString());
        return r;
    }

    private static String bloquesAtendidos(String log) {
        StringBuilder result = new StringBuilder();
        String marca = " en el bloque ";
        int pos = 0;
        while (true) {
            int linea = log.indexOf("El disco atiende la solicitud de", pos);
            if (linea < 0) {
                return result.toString();
            }
            int b = log.indexOf(marca, linea) + marca.length();
            int fin = b;
            while (Character.isDigit(log.charAt(fin))) {
                fin++;
            }
            result.append(result.length() > 0 ? ", " : "").append(log, b, fin);
            pos = fin;
        }
    }

    // ------------------------------------------------------------------ gráficas

    private static void lineas(String titulo, XYSeriesCollection datos, String archivo) throws IOException {
        JFreeChart chart = ChartFactory.createXYLineChart(titulo, "Ciclo", "Bloques recorridos (acumulado)",
                datos, PlotOrientation.VERTICAL, true, false, false);
        estilo(chart);
        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(new Color(0xfcfcfb));
        plot.setDomainGridlinePaint(new Color(0xe4e3df));
        plot.setRangeGridlinePaint(new Color(0xe4e3df));
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        // Trazos distintos además del color: dos políticas pueden coincidir y la hoja puede
        // imprimirse en blanco y negro.
        float[][] trazos = {null, {10f, 4f}, {3f, 3f}, {12f, 4f, 3f, 4f}, {2f, 6f}};
        for (int i = 0; i < datos.getSeriesCount(); i++) {
            renderer.setSeriesPaint(i, COLORES[i]);
            renderer.setSeriesStroke(i, new BasicStroke(2.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND,
                    10f, trazos[i], 0f));
        }
        renderer.setDefaultLegendTextFont(new Font("SansSerif", Font.PLAIN, 16));
        plot.setRenderer(renderer);
        ChartUtils.saveChartAsPNG(new File(OUT + "graficas/" + archivo + ".png"), chart, 1200, 650);
    }

    private static void barras(String titulo, String eje, DefaultCategoryDataset datos, String formato,
            String archivo) throws IOException {
        JFreeChart chart = ChartFactory.createBarChart(titulo, null, eje, datos,
                PlotOrientation.VERTICAL, false, false, false);
        estilo(chart);
        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(new Color(0xfcfcfb));
        plot.setRangeGridlinePaint(new Color(0xe4e3df));
        plot.getRangeAxis().setUpperMargin(0.12);
        BarRenderer renderer = (BarRenderer) plot.getRenderer();
        renderer.setBarPainter(new StandardBarPainter());
        renderer.setShadowVisible(false);
        renderer.setSeriesPaint(0, COLORES[0]);
        renderer.setMaximumBarWidth(0.09);
        java.text.DecimalFormat numeros =
                new java.text.DecimalFormat(formato, java.text.DecimalFormatSymbols.getInstance(ES));
        numeros.setRoundingMode(java.math.RoundingMode.HALF_UP); // el mismo redondeo que las tablas
        renderer.setDefaultItemLabelGenerator(new StandardCategoryItemLabelGenerator("{2}", numeros));
        renderer.setDefaultItemLabelsVisible(true);
        renderer.setDefaultItemLabelFont(new Font("SansSerif", Font.PLAIN, 16));
        ChartUtils.saveChartAsPNG(new File(OUT + "graficas/" + archivo + ".png"), chart, 900, 500);
    }

    private static void estilo(JFreeChart chart) {
        StandardChartTheme tema = new StandardChartTheme("informe");
        tema.setExtraLargeFont(new Font("SansSerif", Font.BOLD, 20));
        tema.setLargeFont(new Font("SansSerif", Font.PLAIN, 16));
        tema.setRegularFont(new Font("SansSerif", Font.PLAIN, 14));
        tema.setChartBackgroundPaint(Color.WHITE);
        tema.apply(chart);
    }

    private static void imagenes(String nombre) {
        md.println("![Desplazamiento acumulado](graficas/" + nombre + "_acumulado.png)");
        md.println();
        md.println("![Desplazamiento total](graficas/" + nombre + "_desplazamiento.png)");
        md.println();
        md.println("![Espera promedio](graficas/" + nombre + "_espera.png)");
        md.println();
    }

    private static String num(double v) {
        return String.format(ES, "%.1f", v);
    }

    private static String lista(int[] a) {
        String s = "";
        for (int i = 0; i < a.length; i++) {
            s += (i > 0 ? ", " : "") + a[i];
        }
        return s;
    }
}
