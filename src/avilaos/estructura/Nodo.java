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
public class Nodo {

    private Proceso dato;
    private Nodo pNext;

    public Nodo(Proceso dato) {
        this.dato = dato;
        this.pNext = null;
    }

    public Proceso getDato() {
        return dato;
    }

    public void setDato(Proceso dato) {
        this.dato = dato;
    }

    public Nodo getpNext() {
        return pNext;
    }

    public void setpNext(Nodo pNext) {
        this.pNext = pNext;
    }
}