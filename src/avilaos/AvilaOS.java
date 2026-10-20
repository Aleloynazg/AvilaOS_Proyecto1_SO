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
import avilaos.kernel.process.PCB;
import avilaos.kernel.process.ProcessType;

public class AvilaOS {

    public static void main(String[] args) {

        // Crear los dos computadores.
        Computer computador1 = new Computer(1, 1000);
        Computer computador2 = new Computer(2, 2000);

        // Crear el reloj global.
        GlobalClock reloj = new GlobalClock(500);

        // Conectar los computadores al reloj.
        computador1.conectarReloj(reloj);
        computador2.conectarReloj(reloj);

        // Crear un proceso de prueba para el computador 1.
        PCB proceso = new PCB(
            "Proceso de prueba",
            1,
            ProcessType.values()[0],
            1,
            300,
            20,
            3
        );

        // Enviar el proceso al Kernel.
        boolean recibido = computador1.getKernel()
            .submitProcess(proceso, reloj.getCycle());

        if (!recibido) {
            System.out.println("El Kernel rechazó el proceso.");
            return;
        }

        System.out.println("Comienzo Prueba");
        System.out.println("Proceso: " + proceso.getTag());
        System.out.println("Instrucciones iniciales: "
            + proceso.getRemainingInstructions());

        // Iniciar el reloj.
        reloj.startClock();

        // Detener la prueba después de 4 segundos.
        Thread hiloPrueba = new Thread(() -> {
            try {
                Thread.sleep(4000);
                reloj.stopClock();

                System.out.println();
                System.out.println("RESULTADO");
                System.out.println("Estado: "
                    + proceso.getState().getLabel());
                System.out.println("Causa de terminación: "
                  + proceso.getTerminationCause());
                System.out.println("Deadline restante: "
                    + proceso.getDeadline());
                System.out.println("PC: "
                    + proceso.getProgramCounter());
                System.out.println("MAR: "
                    + proceso.getMar());
                System.out.println("Instrucciones restantes: "
                    + proceso.getRemainingInstructions());
                System.out.println("Ciclos ejecutados en CPU: "
                    + proceso.getCyclesOnCpu());

                computador1.mostrarEstado();

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        hiloPrueba.start();
    }
}