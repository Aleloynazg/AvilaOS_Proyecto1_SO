package avilaos.structures;

/**
 * Comparador propio para no depender de colecciones de Java.
 * Negativo si a va antes que b, 0 si son iguales, positivo si va después.
 */
@FunctionalInterface
public interface ItemComparator<T> {

    int compare(T a, T b);
}
