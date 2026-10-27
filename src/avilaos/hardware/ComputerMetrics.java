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
    private double sumaCuadradosTiemposEspera;

    private long sumaTiemposEspera;
    private long cantidadTiemposEspera;

    private String[] historial;
    private int cantidadRegistros;

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
        cantidadRegistros = 0;
    }

    public void registrarCiclo(long ciclo, boolean cpuOcupada) {
        ciclosTotales++;

        if (cpuOcupada) {
            ciclosCPUOcupada++;
        }

        String registro =
            "Ciclo " + ciclo
            + " | CPU ocupada: " + cpuOcupada
            + " | Procesos terminados: " + procesosTerminados
            + " | Deadlines incumplidos: " + deadlinesIncumplidos;

        guardarRegistro(registro);
    }

    private void guardarRegistro(String registro) {
        if (cantidadRegistros == historial.length) {
            String[] nuevoHistorial =
                new String[historial.length * 2];

            for (int i = 0; i < historial.length; i++) {
                nuevoHistorial[i] = historial[i];
            }

            historial = nuevoHistorial;
        }

        historial[cantidadRegistros] = registro;
        cantidadRegistros++;
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

    public int getCantidadRegistros() {
        return cantidadRegistros;
    }

    public String getRegistro(int indice) {
        if (indice < 0 || indice >= cantidadRegistros) {
            return null;
        }

        return historial[indice];
    }

    public int getComputerId() {
        return computerId;
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
}