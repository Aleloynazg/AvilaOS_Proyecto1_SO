/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.hardware;

/**
 **
 * Almacena y calcula las métricas de rendimiento de un computador
 * dentro del simulador AvilaOS.
 *
 * Registra información sobre procesos terminados, incumplimiento
 * de deadlines, utilización de CPU, tiempos de respuesta y espera.
 * También mantiene un historial de los resultados obtenidos en
 * cada ciclo para facilitar su consulta y representación gráfica.
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
    
    /**
     * Construye el registro de métricas de un computador.
     *
     * Inicializa los contadores en cero y crea los arreglos que almacenan
     * el historial textual y numérico. Cada arreglo comienza con una
     * capacidad de 100 registros.
     *
     * @param computerId identificador del computador al que pertenecen
     *                   las métricas.
     */
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

        /**
     * Registra las métricas correspondientes a un ciclo del computador.
     *
     * Actualiza la cantidad total de ciclos y, si la CPU estuvo ocupada,
     * incrementa el contador de ciclos de CPU ocupada.
     *
     * También almacena el número del ciclo, el porcentaje de uso de CPU,
     * la cantidad de procesos terminados y los deadlines incumplidos.
     * Si el historial está lleno, amplía su capacidad antes de registrar
     * la nueva información.
     *
     * @param ciclo número del ciclo que se está registrando.
     * @param cpuOcupada indica si la CPU estuvo ocupada durante ese ciclo.
     */
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
    /**
     * Amplía la capacidad de todos los arreglos del historial.
     *
     * Crea nuevos arreglos con el doble de capacidad y copia en ellos
     * los registros existentes. Finalmente, sustituye los arreglos
     * anteriores por los nuevos.
     *
     * Este método se ejecuta cuando se alcanza la capacidad disponible
     * del historial.
     */
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
    /**
     * Registra la información de un proceso que ha finalizado.
     *
     * Actualiza el contador de procesos terminados o de deadlines
     * incumplidos, según corresponda. También acumula los tiempos
     * de respuesta y espera cuando sus valores son mayores o iguales
     * a cero.
     *
     * @param deadlineIncumplido indica si el proceso incumplió su deadline.
     * @param tiempoRespuesta tiempo de respuesta del proceso.
     * @param tiempoEspera tiempo total de espera del proceso.
     */
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
    /**
     * Calcula el tiempo promedio de respuesta de los procesos registrados.
     *
     * @return promedio de los tiempos de respuesta, o cero si no existen
     *         tiempos de respuesta registrados.
     */
    public double getPromedioTiempoRespuesta() {
        if (cantidadTiemposRespuesta == 0) {
            return 0;
        }

        return (double) sumaTiemposRespuesta
                / cantidadTiemposRespuesta;
    }
    /**
     * Calcula el tiempo promedio de espera de los procesos registrados.
     *
     * @return promedio de los tiempos de espera, o cero si no existen
     *         tiempos de espera registrados.
     */
    public double getPromedioTiempoEspera() {
        if (cantidadTiemposEspera == 0) {
            return 0;
        }

        return (double) sumaTiemposEspera
                / cantidadTiemposEspera;
    }
    /**
     * Calcula el porcentaje de utilización de la CPU.
     *
     * Se obtiene dividiendo los ciclos en los que la CPU estuvo ocupada
     * entre el total de ciclos registrados y multiplicando el resultado
     * por cien.
     *
     * @return porcentaje de utilización de CPU, o cero si todavía
     *         no se han registrado ciclos.
     */
    public double getPorcentajeUsoCPU() {
        if (ciclosTotales == 0) {
            return 0;
        }

        return (double) ciclosCPUOcupada
                / ciclosTotales * 100;
    }
    /**
     * Calcula el throughput del computador.
     *
     * Representa la cantidad de procesos terminados por ciclo registrado.
     *
     * @return throughput del computador, o cero si no se han registrado
     *         ciclos.
     */
    public double getThroughput() {
        if (ciclosTotales == 0) {
            return 0;
        }

        return (double) procesosTerminados / ciclosTotales;
    }
    /**
     * Calcula el porcentaje de procesos que cumplieron su deadline.
     *
     * Compara los procesos terminados sin incumplimientos con el total
     * de procesos terminados e incumplidos registrados.
     *
     * @return porcentaje de deadlines cumplidos, o cero si no existen
     *         procesos registrados.
     */
    public double getPorcentajeDeadlinesCumplidos() {
        long totalTerminados =
                procesosTerminados + deadlinesIncumplidos;

        if (totalTerminados == 0) {
            return 0;
        }

        return (double) procesosTerminados
                / totalTerminados * 100;
    }
    /**
     * Calcula el índice de equidad de Jain a partir de los tiempos
     * de espera registrados.
     *
     * Este índice permite evaluar qué tan equitativa ha sido la
     * distribución de los tiempos de espera entre los procesos.
     * Su valor se encuentra entre cero y uno cuando los datos son
     * no negativos y existe al menos un tiempo de espera registrado.
     *
     * @return índice de equidad de Jain; cero si no hay tiempos
     *         registrados y uno si la suma de los cuadrados es cero.
     */
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
    /**
     * Obtiene la cantidad de procesos terminados sin incumplir
     * su deadline.
     *
     * @return cantidad de procesos terminados.
     */
    public long getProcesosTerminados() {
        return procesosTerminados;
    }
    /**
     * Obtiene la cantidad de deadlines incumplidos.
     *
     * @return cantidad de deadlines incumplidos.
     */
    public long getDeadlinesIncumplidos() {
        return deadlinesIncumplidos;
    }
    /**
     * Obtiene la cantidad de ciclos durante los cuales la CPU estuvo ocupada.
     *
     * @return cantidad de ciclos de CPU ocupada.
     */
    public long getCiclosCPUOcupada() {
        return ciclosCPUOcupada;
    }   
      /**
     * Obtiene la cantidad total de ciclos registrados.
     *
     * @return cantidad total de ciclos.
     */

    public long getCiclosTotales() {
        return ciclosTotales;
    }   
    /**
     * Obtiene la cantidad de registros almacenados en el historial.
     *
     * @return cantidad de registros disponibles.
     */

    public int getCantidadRegistros() {
        return cantidadRegistros;
    }
    /**
     * Obtiene el registro textual correspondiente a una posición
     * del historial.
     *
     * @param indice posición del registro que se desea consultar.
     * @return registro textual correspondiente al índice, o null
     *         si el índice está fuera del rango válido.
     */
    public String getRegistro(int indice) {
        if (indice < 0 || indice >= cantidadRegistros) {
            return null;
        }

        return historial[indice];
    }
    
        /**
     * Obtiene el número de ciclo almacenado en una posición del historial.
     *
     * @param indice posición del ciclo que se desea consultar.
     * @return número del ciclo, o -1 si el índice está fuera del rango válido.
     */

    public long getCicloHistorial(int indice) {
        if (indice < 0 || indice >= cantidadRegistros) {
            return -1;
        }

        return historialCiclos[indice];
    }
    /**
     * Obtiene el porcentaje de utilización de CPU almacenado
     * en una posición del historial.
     *
     * @param indice posición del registro que se desea consultar.
     * @return porcentaje de uso de CPU, o -1 si el índice está
     *         fuera del rango válido.
     */
    public double getUsoCPUHistorial(int indice) {
        if (indice < 0 || indice >= cantidadRegistros) {
            return -1;
        }

        return historialUsoCPU[indice];
    }
    /**
     * Obtiene la cantidad de procesos terminados almacenada
     * en una posición del historial.
     *
     * @param indice posición del registro que se desea consultar.
     * @return cantidad de procesos terminados, o -1 si el índice
     *         está fuera del rango válido.
     */
    public long getProcesosTerminadosHistorial(int indice) {
        if (indice < 0 || indice >= cantidadRegistros) {
            return -1;
        }

        return historialProcesosTerminados[indice];
    }
    /**
     * Obtiene la cantidad de deadlines incumplidos almacenada
     * en una posición del historial.
     *
     * @param indice posición del registro que se desea consultar.
     * @return cantidad de deadlines incumplidos, o -1 si el índice
     *         está fuera del rango válido.
     */

    public long getDeadlinesIncumplidosHistorial(int indice) {
        if (indice < 0 || indice >= cantidadRegistros) {
            return -1;
        }
        return historialDeadlinesIncumplidos[indice];
    }
        /**
     * Obtiene el identificador del computador al que pertenecen
     * estas métricas.
     *
     * @return identificador del computador.
     */
    public int getComputerId() {
        return computerId;
    }
}