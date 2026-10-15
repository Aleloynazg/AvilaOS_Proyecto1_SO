/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.estructura;
import avilaos.Proceso;
/**
 *
 * @author alexandraloynaz
 */
public class ColaProcesos {

    private Nodo cabeza;
    private Nodo ultimo;
    private int size;

    public ColaProcesos() {
        cabeza = null;
        ultimo = null;
        size = 0;
    }

    public boolean esVacia() {
        return cabeza == null;}

    public void encolar(Proceso proceso) {
        Nodo nuevo = new Nodo(proceso);

        if (esVacia()) {
            cabeza = nuevo;
            ultimo = nuevo;
        } else {
            ultimo.setpNext(nuevo);
            ultimo = nuevo;
        }

        size++;
    }

    public Proceso desencolar() {
        if (esVacia()) {
            return null;
        }

        Proceso proceso = cabeza.getDato();
        cabeza = cabeza.getpNext();

        if (cabeza == null) {
            ultimo = null;
        }

        size--;

        return proceso;
    }

    public Proceso verPrimero() {
        if (esVacia()) {
            return null;
        }

        return cabeza.getDato();
    }

    public int getSize() {
        return size;
    }
}