/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos;

/**
 *
 * @author alexandraloynaz
 */
public class Proceso {

    private int id;
    private String nombre;
    private EstadoProceso estado;
    private TipoProceso tipoProceso;
    private int prioridad;
    private int memoriaRequerida;
    private int tiempoEjecucion;
    private int tiempoRestante;
    private int fechaLimite;
    private int contadorPrograma;

    public Proceso(int id, String nombre, TipoProceso tipoProceso,
                   int prioridad, int memoriaRequerida,
                   int tiempoEjecucion, int fechaLimite) {

        this.id = id;
        this.nombre = nombre;
        this.estado = EstadoProceso.NUEVO;
        this.tipoProceso = tipoProceso;
        this.prioridad = prioridad;
        this.memoriaRequerida = memoriaRequerida;
        this.tiempoEjecucion = tiempoEjecucion;
        this.tiempoRestante = tiempoEjecucion;
        this.fechaLimite = fechaLimite;
        this.contadorPrograma = 0;
    }

    public int getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public EstadoProceso getEstado() {
        return estado;
    }

    public void setEstado(EstadoProceso estado) {
        this.estado = estado;
    }

    public TipoProceso getTipoProceso() {
        return tipoProceso;
    }

    public int getPrioridad() {
        return prioridad;
    }

    public void setPrioridad(int prioridad) {
        this.prioridad = prioridad;
    }

    public int getMemoriaRequerida() {
        return memoriaRequerida;
    }

    public int getTiempoEjecucion() {
        return tiempoEjecucion;
    }

    public int getTiempoRestante() {
        return tiempoRestante;
    }

    public void setTiempoRestante(int tiempoRestante) {
        this.tiempoRestante = tiempoRestante;
    }

    public int getFechaLimite() {
        return fechaLimite;
    }

    public int getContadorPrograma() {
        return contadorPrograma;
    }

    public void setContadorPrograma(int contadorPrograma) {
        this.contadorPrograma = contadorPrograma;
    }
}