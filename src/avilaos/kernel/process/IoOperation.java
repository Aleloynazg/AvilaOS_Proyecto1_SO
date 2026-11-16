/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Enum.java to edit this template
 */
package avilaos.kernel.process;

/**
 * Identifica las operaciones de entrada y salida que generan solicitudes hacia el disco.
 * @author alexandraloynaz
 */
public enum IoOperation {
    CREATE,
    READ,
    UPDATE,
    DELETE,
    SUSPEND,
    RESUME 
}
