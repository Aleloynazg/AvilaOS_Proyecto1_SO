package avilaos.hardware;

/**
 * Interfaz común para todo lo que reacciona al reloj global
 * (CPU, dispositivo de E/S, kernel, red...). Requisito obligatorio.
 */
public interface ClockListener {

    /** Se llama exactamente una vez por ciclo de reloj. */
    void onTick(long cycle);
}
