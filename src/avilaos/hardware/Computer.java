/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.hardware;

/**
 *
 * @author alexandraloynaz
 */
import avilaos.kernel.memory.RAM;
import avilaos.hardware.GlobalClock;

public class Computer {

    private final int id;
    private final CPU cpu;
    private final RAM ram;

    public Computer(int id, int capacidadRAM) {

        if (id <= 0) {
            throw new IllegalArgumentException(
                "El identificador debe ser mayor que cero."
            );
        }

        this.id = id;
        this.cpu = new CPU(id);
        this.ram = new RAM(capacidadRAM);
    }

    public int getId() {
        return id;
    }

    public CPU getCPU() {
        return cpu;
    }

    public RAM getRAM() {
        return ram;
    }

    public void mostrarEstado() {
        System.out.println("Computador: " + id);

        System.out.println(
            "Estado de CPU: "
            + (cpu.isIdle() ? "Libre" : "Ejecutando un proceso")
        );

        System.out.println(
            "Modo de CPU: " + cpu.getMode().getLabel()
        );

        System.out.println(
            "Memoria RAM utilizada: "
            + ram.getMemoriaUtilizada()
            + " / "
            + ram.getCapacidadTotal()
        );

        System.out.println(
            "Memoria RAM disponible: "
            + ram.getMemoriaDisponible()
        );
    }
    public void conectarReloj(GlobalClock reloj) {
        if (reloj != null) {
            reloj.addListener(cpu);
    }
    }
}