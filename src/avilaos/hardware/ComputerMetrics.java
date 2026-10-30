/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.hardware;

/**
 *
 * @author alexandraloynaz
 */
    
public class ComputerMetrics {
private final int computerId;

    private long procesosTerminados;
    private long deadlinesIncumplidos;
    private long ciclosCPUOcupada;
    private long ciclosTotales;
    private long sumaTiemposRespuesta;
    private long cantidadTiemposRespuesta;
    private long sumaTiemposEspera;
    private long cantidadTiemposEspera;
    private double sumaCuadradosTiemposEspera;

    // Historial textual para consultar los registros.
    private String[] historial;
    private int cantidadRegistros;

    // Historial numerico para utilizarlo en graficas.
    private long[] historialCiclos;
    private double[] historialUsoCPU;
    private long[] historialProcesosTerminados;
    private long[] historialDeadlinesIncumplidos;

    public ComputerMetrics(int computerId) {
        this.computerId = computerId;

        procesosTerminados = 0;
        deadlinesIncumplidos = 0;
        ciclosCPUOcupada = 0;
        ciclosTotales = 0;

        sumaTiemposRespuesta = 0;
        cantidadTiemposRespuesta = 0;

        sumaTiemposEspera = 0;
        cantidadTiemposEspera = 0;
        sumaCuadradosTiemposEspera = 0;

        historial = new String[100];
        historialCiclos = new long[100];
        historialUsoCPU = new double[100];
        historialProcesosTerminados = new long[100];
        historialDeadlinesIncumplidos = new long[100];

        cantidadRegistros = 0;
    }

    public void registrarCiclo(long ciclo, boolean cpuOcupada) {
        ciclosTotales++;

        if (cpuOcupada) {
            ciclosCPUOcupada++;
        }

        // Ampliar los arreglos cuando sea necesario.
        if (cantidadRegistros == historial.length) {
            ampliarHistorial();
        }

        historialCiclos[cantidadRegistros] = ciclo;
        historialUsoCPU[cantidadRegistros] = getPorcentajeUsoCPU();
        historialProcesosTerminados[cantidadRegistros] =
                procesosTerminados;
        historialDeadlinesIncumplidos[cantidadRegistros] =
                deadlinesIncumplidos;

        String registro =
                "Ciclo " + ciclo
                + " | CPU ocupada: " + cpuOcupada
                + " | Procesos terminados: " + procesosTerminados
                + " | Deadlines incumplidos: " + deadlinesIncumplidos;

        historial[cantidadRegistros] = registro;
        cantidadRegistros++;
    }

    private void ampliarHistorial() {
        int nuevaCapacidad = historial.length * 2;

        String[] nuevoHistorial = new String[nuevaCapacidad];
        long[] nuevosCiclos = new long[nuevaCapacidad];
        double[] nuevoUsoCPU = new double[nuevaCapacidad];
        long[] nuevosTerminados = new long[nuevaCapacidad];
        long[] nuevosDeadlines = new long[nuevaCapacidad];

        for (int i = 0; i < cantidadRegistros; i++) {
            nuevoHistorial[i] = historial[i];
            nuevosCiclos[i] = historialCiclos[i];
            nuevoUsoCPU[i] = historialUsoCPU[i];
            nuevosTerminados[i] = historialProcesosTerminados[i];
            nuevosDeadlines[i] = historialDeadlinesIncumplidos[i];
        }

        historial = nuevoHistorial;
        historialCiclos = nuevosCiclos;
        historialUsoCPU = nuevoUsoCPU;
        historialProcesosTerminados = nuevosTerminados;
        historialDeadlinesIncumplidos = nuevosDeadlines;
    }

    public void registrarProcesoTerminado(
            boolean deadlineIncumplido,
            long tiempoRespuesta,
            long tiempoEspera) {

        if (deadlineIncumplido) {
            deadlinesIncumplidos++;
        } else {
            procesosTerminados++;
        }

        if (tiempoRespuesta >= 0) {
            sumaTiemposRespuesta += tiempoRespuesta;
            cantidadTiemposRespuesta++;
        }

        if (tiempoEspera >= 0) {
            sumaTiemposEspera += tiempoEspera;
            cantidadTiemposEspera++;

            sumaCuadradosTiemposEspera +=
                    (double) tiempoEspera * tiempoEspera;
        }
    }

    public double getPromedioTiempoRespuesta() {
        if (cantidadTiemposRespuesta == 0) {
            return 0;
        }

        return (double) sumaTiemposRespuesta
                / cantidadTiemposRespuesta;
    }

    public double getPromedioTiempoEspera() {
        if (cantidadTiemposEspera == 0) {
            return 0;
        }

        return (double) sumaTiemposEspera
                / cantidadTiemposEspera;
    }

    public double getPorcentajeUsoCPU() {
        if (ciclosTotales == 0) {
            return 0;
        }

        return (double) ciclosCPUOcupada
                / ciclosTotales * 100;
    }

    public double getThroughput() {
        if (ciclosTotales == 0) {
            return 0;
        }

        return (double) procesosTerminados / ciclosTotales;
    }

    public double getPorcentajeDeadlinesCumplidos() {
        long totalTerminados =
                procesosTerminados + deadlinesIncumplidos;

        if (totalTerminados == 0) {
            return 0;
        }

        return (double) procesosTerminados
                / totalTerminados * 100;
    }

    public double getIndiceEquidadJain() {
        if (cantidadTiemposEspera == 0) {
            return 0;
        }

        if (sumaCuadradosTiemposEspera == 0) {
            return 1;
        }

        double suma = sumaTiemposEspera;
        double cantidad = cantidadTiemposEspera;

        return (suma * suma)
                / (cantidad * sumaCuadradosTiemposEspera);
    }

    public long getProcesosTerminados() {
        return procesosTerminados;
    }

    public long getDeadlinesIncumplidos() {
        return deadlinesIncumplidos;
    }

    public long getCiclosCPUOcupada() {
        return ciclosCPUOcupada;
    }

    public long getCiclosTotales() {
        return ciclosTotales;
    }

    // Acumuladores crudos: el SystemMetrics los suma entre computadores.
    public long getSumaTiemposRespuesta() {
        return sumaTiemposRespuesta;
    }

    public long getCantidadTiemposRespuesta() {
        return cantidadTiemposRespuesta;
    }

    public long getSumaTiemposEspera() {
        return sumaTiemposEspera;
    }

    public long getCantidadTiemposEspera() {
        return cantidadTiemposEspera;
    }

    public double getSumaCuadradosTiemposEspera() {
        return sumaCuadradosTiemposEspera;
    }

    public int getCantidadRegistros() {
        return cantidadRegistros;
    }

    public String getRegistro(int indice) {
        if (indice < 0 || indice >= cantidadRegistros) {
            return null;
        }

        return historial[indice];
    }

    public long getCicloHistorial(int indice) {
        if (indice < 0 || indice >= cantidadRegistros) {
            return -1;
        }

        return historialCiclos[indice];
    }

    public double getUsoCPUHistorial(int indice) {
        if (indice < 0 || indice >= cantidadRegistros) {
            return -1;
        }

        return historialUsoCPU[indice];
    }

    /**
     * Utilización (%) de la CPU en los últimos {@code ventana} ciclos
     * registrados hasta el registro {@code indice}. Se deduce de los
     * porcentajes acumulados guardados en el historial.
     */
    public double getUsoCPUVentana(int indice, int ventana) {
        if (indice < 0 || indice >= cantidadRegistros || ventana <= 0) {
            return -1;
        }

        long ocupadosHasta = Math.round(
                historialUsoCPU[indice] * (indice + 1) / 100);
        int desde = indice - ventana;

        if (desde < 0) {
            return historialUsoCPU[indice];
        }

        long ocupadosAntes = Math.round(
                historialUsoCPU[desde] * (desde + 1) / 100);

        return (double) (ocupadosHasta - ocupadosAntes) / ventana * 100;
    }

    public long getProcesosTerminadosHistorial(int indice) {
        if (indice < 0 || indice >= cantidadRegistros) {
            return -1;
        }

        return historialProcesosTerminados[indice];
    }

    public long getDeadlinesIncumplidosHistorial(int indice) {
        if (indice < 0 || indice >= cantidadRegistros) {
            return -1;
        }
        return historialDeadlinesIncumplidos[indice];
    }
    public int getComputerId() {
        return computerId;
    }
}