/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Main.java to edit this template
 */
package avilaos;
/**
 *
 * @author alexandraloynaz
 */
import avilaos.hardware.Computer;
import avilaos.hardware.GlobalClock;
import avilaos.kernel.SystemEventListener;
import avilaos.kernel.process.PCB;
import avilaos.kernel.process.ProcessType;
import avilaos.kernel.scheduler.RoundRobinPolicy;

public class AvilaOS {

    public static void main(String[] args) {

        // Crear los dos computadores.
        Computer computador1 = new Computer(1, 1000);
        Computer computador2 = new Computer(2, 2000);

        // Configurar Round Robin con quantum de 2 ciclos.
        computador1.setPolicy(new RoundRobinPolicy(2));

        // Crear el reloj global.
        final GlobalClock reloj = new GlobalClock(500);

        // Conectar los computadores al reloj.
        computador1.conectarReloj(reloj);
        computador2.conectarReloj(reloj);

        // Registrar los eventos del computador 1.
        computador1.addEventListener(new SystemEventListener() {
            @Override
            public void onEvent(long ciclo, String mensaje) {
                System.out.println("Ciclo " + ciclo + ": " + mensaje);
            }
        });

        // Registrar los eventos del computador 2.
        computador2.addEventListener(new SystemEventListener() {
            @Override
            public void onEvent(long ciclo, String mensaje) {
                System.out.println("Ciclo " + ciclo + ": " + mensaje);
            }
        });

        // Crear dos procesos de prueba.
        final PCB proceso1 = new PCB(
            "Proceso 1", 1, ProcessType.CPU_BOUND,
            1, 300, 20, 5
        );

        final PCB proceso2 = new PCB(
            "Proceso 2", 1, ProcessType.CPU_BOUND,
            1, 200, 20, 3
        );

        // Enviar ambos procesos al Kernel.
        computador1.getKernel().submitProcess(
            proceso1, reloj.getCycle()
        );

        computador1.getKernel().submitProcess(
            proceso2, reloj.getCycle()
        );

        System.out.println("Comienzo de la prueba Round Robin");
        System.out.println("Quantum: 2 ciclos");
        System.out.println(proceso1.getTag()
            + " - Instrucciones: "
            + proceso1.getRemainingInstructions());
        System.out.println(proceso2.getTag()
            + " - Instrucciones: "
            + proceso2.getRemainingInstructions());

        // Iniciar el reloj.
        reloj.startClock();

        // Detener la prueba después de 4 segundos.
        Thread hiloPrueba = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(8000);
                    reloj.stopClock();

                    System.out.println();
                    System.out.println("RESULTADO PROCESO 1");
                    System.out.println("Estado: "
                        + proceso1.getState().getLabel());
                    System.out.println("Causa: "
                        + proceso1.getTerminationCause());
                    System.out.println("PC: "
                        + proceso1.getProgramCounter());
                    System.out.println("Instrucciones restantes: "
                        + proceso1.getRemainingInstructions());
                    System.out.println("Ciclos ejecutados: "
                        + proceso1.getCyclesOnCpu());

                    System.out.println();
                    System.out.println("RESULTADO PROCESO 2");
                    System.out.println("Estado: "
                        + proceso2.getState().getLabel());
                    System.out.println("Causa: "
                        + proceso2.getTerminationCause());
                    System.out.println("PC: "
                        + proceso2.getProgramCounter());
                    System.out.println("Instrucciones restantes: "
                        + proceso2.getRemainingInstructions());
                    System.out.println("Ciclos ejecutados: "
                        + proceso2.getCyclesOnCpu());

                    System.out.println();
                    computador1.mostrarEstado();

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });

        hiloPrueba.start();
    }
}