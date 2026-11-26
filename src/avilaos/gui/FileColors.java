package avilaos.gui;

import java.awt.Color;

/**
 * Colores de la interfaz para el disco: uno distinto por archivo (según su
 * colorIndex), uno para el swap ocupado y uno para los bloques libres.
 */
public final class FileColors {

    public static final Color FREE = new Color(250, 250, 250);
    public static final Color FREE_SWAP = new Color(226, 236, 246);
    public static final Color SWAP_USED = new Color(96, 110, 140);
    public static final Color HEAD = new Color(214, 40, 40);

    private FileColors() { }

    /** Color de un archivo: tonos repartidos con el ángulo dorado para que no se parezcan. */
    public static Color forIndex(int index) {
        float hue = (index * 0.618034f) % 1f;
        return Color.getHSBColor(hue, 0.45f, 0.95f);
    }

    /** Texto legible (negro o blanco) sobre un color de fondo. */
    public static Color textOn(Color background) {
        int luminance = (background.getRed() * 299 + background.getGreen() * 587
                + background.getBlue() * 114) / 1000;
        return luminance > 140 ? Color.BLACK : Color.WHITE;
    }
}