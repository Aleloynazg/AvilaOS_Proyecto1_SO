package avilaos.hardware;

import avilaos.kernel.process.PCB;
import avilaos.kernel.process.ProcessType;
import avilaos.kernel.sync.Buffer;

/**
 * Métricas de todo el clúster. Se calculan sumando los contadores de cada
 * computador (no promediando sus porcentajes), como indica DECISIONES.md.
 * Solo lee: no modifica el sistema.
 */
public class SystemMetrics {

    private final Cluster cluster;

    public SystemMetrics(Cluster cluster) {
        this.cluster = cluster;
    }

    public long getProcesosTerminados() {
        long total = 0;
        Computer[] computers = cluster.getComputers();
        for (int i = 0; i < computers.length; i++) {
            total += computers[i].getMetrics().getProcesosTerminados();
        }
        return total;
    }

    public long getDeadlinesIncumplidos() {
        long total = 0;
        Computer[] computers = cluster.getComputers();
        for (int i = 0; i < computers.length; i++) {
            total += computers[i].getMetrics().getDeadlinesIncumplidos();
        }
        return total;
    }

    /** Procesos completados por ciclo de reloj. */
    public double getThroughput() {
        long ciclos = getCiclosTranscurridos();
        return ciclos == 0 ? 0 : (double) getProcesosTerminados() / ciclos;
    }

    /** Ciclos con algún proceso en CPU, entre los ciclos de todas las CPU, en %. */
    public double getUtilizacionCPU() {
        long ocupados = 0;
        long totales = 0;
        Computer[] computers = cluster.getComputers();
        for (int i = 0; i < computers.length; i++) {
            ocupados += computers[i].getMetrics().getCiclosCPUOcupada();
            totales += computers[i].getMetrics().getCiclosTotales();
        }
        return totales == 0 ? 0 : (double) ocupados / totales * 100;
    }

    public double getPromedioTiempoRespuesta() {
        long suma = 0;
        long cantidad = 0;
        Computer[] computers = cluster.getComputers();
        for (int i = 0; i < computers.length; i++) {
            suma += computers[i].getMetrics().getSumaTiemposRespuesta();
            cantidad += computers[i].getMetrics().getCantidadTiemposRespuesta();
        }
        return cantidad == 0 ? 0 : (double) suma / cantidad;
    }

    public double getPorcentajeDeadlinesCumplidos() {
        long terminados = getProcesosTerminados();
        long total = terminados + getDeadlinesIncumplidos();
        return total == 0 ? 0 : (double) terminados / total * 100;
    }

    /** Índice de Jain sobre el tiempo de espera en Listos de todos los procesos del clúster. */
    public double getIndiceEquidadJain() {
        double suma = 0;
        double cuadrados = 0;
        long cantidad = 0;
        Computer[] computers = cluster.getComputers();
        for (int i = 0; i < computers.length; i++) {
            suma += computers[i].getMetrics().getSumaTiemposEspera();
            cuadrados += computers[i].getMetrics().getSumaCuadradosTiemposEspera();
            cantidad += computers[i].getMetrics().getCantidadTiemposEspera();
        }
        if (cantidad == 0) {
            return 0;
        }
        if (cuadrados == 0) {
            return 1;
        }
        return (suma * suma) / (cantidad * cuadrados);
    }

    // ---------------- Productor-consumidor ----------------

    public long getElementosProducidos() {
        long total = 0;
        Object[] buffers = cluster.getBuffers();
        for (int i = 0; i < buffers.length; i++) {
            total += ((Buffer) buffers[i]).getProduced();
        }
        return total;
    }

    public long getElementosConsumidos() {
        long total = 0;
        Object[] buffers = cluster.getBuffers();
        for (int i = 0; i < buffers.length; i++) {
            total += ((Buffer) buffers[i]).getConsumed();
        }
        return total;
    }

    /** Promedio de ciclos bloqueados en semáforos por proceso productor/consumidor (de un buffer o de todos). */
    public double getPromedioBloqueoSemaforos(int bufferId) {
        long suma = 0;
        long cantidad = 0;
        PCB[] processes = collectBufferProcesses();
        for (int i = 0; i < processes.length; i++) {
            if (bufferId < 0 || processes[i].getBufferId() == bufferId) {
                suma += processes[i].getCyclesBlockedOnSemaphores();
                cantidad++;
            }
        }
        return cantidad == 0 ? 0 : (double) suma / cantidad;
    }

    public double getPromedioBloqueoSemaforos() {
        return getPromedioBloqueoSemaforos(-1);
    }

    // ---------------- Auxiliares ----------------

    private long getCiclosTranscurridos() {
        long max = 0;
        Computer[] computers = cluster.getComputers();
        for (int i = 0; i < computers.length; i++) {
            max = Math.max(max, computers[i].getMetrics().getCiclosTotales());
        }
        return max;
    }

    /** Productores y consumidores de todos los computadores, vivos o terminados. */
    private PCB[] collectBufferProcesses() {
        PCB[] result = new PCB[0];
        Computer[] computers = cluster.getComputers();
        for (int c = 0; c < computers.length; c++) {
            Object[][] groups = {
                computers[c].getKernel().getNewQueue().toArray(),
                computers[c].getKernel().getReadyQueue().toArray(),
                computers[c].getKernel().getBlockedQueue().toArray(),
                computers[c].getKernel().getTerminatedQueue().toArray(),
                { computers[c].getCPU().getCurrentProcess() }
            };
            for (int g = 0; g < groups.length; g++) {
                for (int j = 0; j < groups[g].length; j++) {
                    PCB p = (PCB) groups[g][j];
                    if (p != null && isBufferProcess(p) && !contains(result, p)) {
                        PCB[] bigger = new PCB[result.length + 1];
                        for (int k = 0; k < result.length; k++) {
                            bigger[k] = result[k];
                        }
                        bigger[result.length] = p;
                        result = bigger;
                    }
                }
            }
        }
        return result;
    }

    private static boolean isBufferProcess(PCB p) {
        return p.getType() == ProcessType.PRODUCER || p.getType() == ProcessType.CONSUMER;
    }

    private static boolean contains(PCB[] array, PCB item) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == item) {
                return true;
            }
        }
        return false;
    }
}
