/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.kernel.memory;

/**
 *
 * @author alexandraloynaz
 */
public class RAM {

    private final int capacidadTotal;
    private int memoriaUtilizada;

    public RAM(int capacidadTotal) {
        if (capacidadTotal <= 0) {
            throw new IllegalArgumentException(
                "La capacidad de RAM debe ser mayor que cero."
            );
        }

        this.capacidadTotal = capacidadTotal;
        this.memoriaUtilizada = 0;
    }

    // Intenta reservar memoria para un proceso.
    public synchronized boolean reservarMemoria(int cantidad) {

        if (cantidad <= 0) {
            return false;
        }

        if (cantidad > getMemoriaDisponible()) {
            return false;
        } memoriaUtilizada += cantidad;
        return true;
    }

    // Libera memoria cuando un proceso termina.
    public synchronized void liberarMemoria(int cantidad) {

        if (cantidad <= 0 || cantidad > memoriaUtilizada) {
            return;
        }

        memoriaUtilizada -= cantidad;
    }

    public synchronized int getMemoriaDisponible() {
        return capacidadTotal - memoriaUtilizada;
    }

    public int getCapacidadTotal() {
        return capacidadTotal;
    }

    public synchronized int getMemoriaUtilizada() {
        return memoriaUtilizada;
    }
}