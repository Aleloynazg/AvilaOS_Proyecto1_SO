/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Main.java to edit this template
 */
package avilaos;

/**
 *
 * @author alexandraloynaz
 */
import avilaos.hardware.ClockListener;
import avilaos.hardware.GlobalClock;

public class AvilaOS {

    public static void main(String[] args) {

        GlobalClock reloj = new GlobalClock(500);

        ClockListener componentePrueba = new ClockListener() {
            @Override
            public void onTick(long cycle) {

                System.out.println("Componente recibió el ciclo: " + cycle);

                if (cycle == 2) {
                    System.out.println("Pausando reloj...");
                    reloj.pauseClock();
                }

                if (cycle == 4) {
                    System.out.println("Deteniendo reloj...");
                    reloj.stopClock();
                }
            }
        };

        reloj.addListener(componentePrueba);
        reloj.startClock();

        Thread hiloReanudacion = new Thread(() -> {
            try {
                Thread.sleep(2000);
                System.out.println("Reanudando reloj...");
                reloj.resumeClock();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        hiloReanudacion.start();
    }
}