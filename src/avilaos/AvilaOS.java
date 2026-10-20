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
import avilaos.kernel.process.ProcessState;
import avilaos.kernel.process.ProcessType;

public class AvilaOS {

    public static void main(String[] args) {

        // Crear los dos computadores.
        Computer computador1 = new Computer(1, 1000);
        Computer computador2 = new Computer(2, 2000);

        // Crear un reloj global.
        GlobalClock reloj = new GlobalClock(500);

        // Conectar ambos computadores al reloj.
        computador1.conectarReloj(reloj);
        computador2.conectarReloj(reloj);

        // Crear un proceso para el computador 1.
        PCB proceso = new PCB(
            "Proceso de prueba",
            1,
            ProcessType.values()[0],
            1,
            300,
            20,
            3
        );

        // Reservar la memoria necesaria para el proceso.
        boolean memoriaReservada =
            computador1.getRAM().reservarMemoria(
                proceso.getMemoryRequired()
            );

        if (!memoriaReservada) {
            System.out.println("No hay memoria suficiente.");
            return;
        }

        // Simular la admisión del proceso a la cola de listos.
        proceso.setState(ProcessState.READY);

        // Asignar el proceso a la CPU del computador 1.
        boolean procesoAsignado =
            computador1.getCPU().assignProcess(proceso);

        if (!procesoAsignado) {
            System.out.println("No se pudo asignar el proceso.");
            return;
        }

        System.out.println("===== INICIO DE LA PRUEBA =====");
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
                System.out.println("===== RESULTADO =====");
                System.out.println("Estado: "
                    + proceso.getState().getLabel());
                System.out.println("PC: "
                    + proceso.getProgramCounter());
                System.out.println("MAR: "
                    + proceso.getMar());
                System.out.println("Instrucciones restantes: "
                    + proceso.getRemainingInstructions());
                System.out.println("Ciclos ejecutados en CPU: "
                    + proceso.getCyclesOnCpu());

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        hiloPrueba.start();
    }
}