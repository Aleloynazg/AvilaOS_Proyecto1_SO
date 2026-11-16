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
    FIFO,
    SSTF,
    SCAN,
    CSCAN,
    LOOK
}
