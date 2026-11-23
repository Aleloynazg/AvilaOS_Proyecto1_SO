/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Enum.java to edit this template
 */
package avilaos.kernel.disk;

/**
 * Identifica las políticas de planificación de solicitudes del disco.
 * @author alexandraloynaz
 */
public enum DiskPolicyType {
    FIFO("FIFO"),
    SSTF("SSTF"),
    SCAN("SCAN"),
    CSCAN("C-SCAN"),
    LOOK("LOOK");

    private final String label;

    DiskPolicyType(String label) { this.label = label; }

    /** Nombre para mostrar en la interfaz y en el log, por ejemplo "C-SCAN". */
    public String getLabel() { return label; }

    /**
     * Convierte un texto en una política, sin distinguir mayúsculas ni guiones:
     * "SCAN", "c-scan" y "CSCAN" son válidos.
     *
     * @throws IllegalArgumentException si no corresponde a ninguna política
     */
    public static DiskPolicyType fromText(String text) {
        if (text != null) {
            String clean = text.trim().toUpperCase().replace("-", "").replace("_", "");
            for (DiskPolicyType type : values()) {
                if (type.name().equals(clean)) {
                    return type;
                }
            }
        }
        throw new IllegalArgumentException("política de disco desconocida: " + text
                + " (usa FIFO, SSTF, SCAN, C-SCAN o LOOK)");
    }
}
