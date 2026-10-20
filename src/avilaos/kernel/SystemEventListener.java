package avilaos.kernel;

/**
 * Permite que el log de la GUI y las métricas escuchen lo que pasa en el
 * kernel sin que el kernel conozca a la GUI ("la interfaz observa al sistema,
 * no es el sistema").
 */
public interface SystemEventListener {

    void onEvent(long cycle, String message);
}
