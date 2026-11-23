package avilaos.kernel.fs;

import avilaos.hardware.disk.Disk;
import avilaos.kernel.process.IoOperation;

/**
 * Prueba de AccessControl (F2.3): permisos de administrador y de usuario.
 *
 * Se ejecuta como programa normal. Termina con código 1 si algo falla.
 */
public class AccessControlPrueba {

    private static int fallos = 0;
    private static int comprobaciones = 0;

    public static void main(String[] args) {
        FileSystem fs = armarSistema();
        AccessControl acceso = new AccessControl();

        System.out.println("--- 1. Modo administrador (por defecto) ---");
        verificar("Arranca en modo administrador", acceso.getMode() == UserMode.ADMIN
                && acceso.getCurrentUser().equals("admin"));
        permitido("Leer un archivo privado de otro", acceso, fs, IoOperation.READ, "/usuarios/notas.txt");
        permitido("Crear un archivo en una carpeta de admin", acceso, fs, IoOperation.CREATE, "/sistema/nuevo.bin");
        permitido("Renombrar", acceso, fs, IoOperation.UPDATE, "/usuarios/data.csv");
        permitido("Eliminar un archivo de otro usuario", acceso, fs, IoOperation.DELETE, "/usuarios/data.csv");
        permitido("Eliminar un directorio", acceso, fs, IoOperation.DELETE, "/usuarios");
        verificar("Crear un directorio", acceso.check(fs, IoOperation.CREATE, "/nueva", NodeType.DIRECTORY) == null);
        rechazado("Leer una ruta que no existe", "no existe", acceso, fs, IoOperation.READ, "/nada.txt");
        rechazado("Crear algo que ya existe", "Ya existe", acceso, fs, IoOperation.CREATE, "/sistema/boot.bin");
        rechazado("Crear en una carpeta que no existe", "no existe", acceso, fs, IoOperation.CREATE, "/nada/x.txt");
        rechazado("Crear dentro de un archivo", "es un archivo", acceso, fs, IoOperation.CREATE, "/sistema/boot.bin/x");

        System.out.println("--- 2. Modo usuario (user1) ---");
        acceso.setUserMode("user1");
        verificar("Modo USER con usuario user1", acceso.getMode() == UserMode.USER
                && acceso.getCurrentUser().equals("user1") && acceso.describe().equals("usuario user1"));
        permitido("Leer un archivo propio privado", acceso, fs, IoOperation.READ, "/usuarios/notas.txt");
        permitido("Leer un archivo público de otro usuario", acceso, fs, IoOperation.READ, "/usuarios/data.csv");
        permitido("Leer un archivo público del sistema", acceso, fs, IoOperation.READ, "/sistema/boot.bin");
        permitido("Ver un directorio", acceso, fs, IoOperation.READ, "/usuarios");
        rechazado("Leer un archivo privado de otro usuario", "es privado y pertenece a user2",
                acceso, fs, IoOperation.READ, "/usuarios/secreto.txt");
        permitido("Crear un archivo en su propio directorio", acceso, fs, IoOperation.CREATE, "/usuarios/user1/a.txt");
        rechazado("Crear un archivo en un directorio de admin", "propios directorios",
                acceso, fs, IoOperation.CREATE, "/usuarios/a.txt");
        rechazado("Crear un archivo en el directorio de otro usuario", "pertenece a user2",
                acceso, fs, IoOperation.CREATE, "/usuarios/user2/a.txt");
        verificar("Crear un directorio (solo admin)",
                acceso.check(fs, IoOperation.CREATE, "/usuarios/user1/sub", NodeType.DIRECTORY)
                        .contains("Solo el administrador"));
        rechazado("Renombrar (solo admin)", "Solo el administrador puede renombrar",
                acceso, fs, IoOperation.UPDATE, "/usuarios/notas.txt");
        permitido("Eliminar un archivo propio", acceso, fs, IoOperation.DELETE, "/usuarios/notas.txt");
        rechazado("Eliminar un archivo de otro usuario aunque sea público", "pertenece a user2",
                acceso, fs, IoOperation.DELETE, "/usuarios/data.csv");
        rechazado("Eliminar un directorio", "no puede eliminar directorios",
                acceso, fs, IoOperation.DELETE, "/usuarios/user1");
        rechazado("Eliminar una ruta que no existe", "no existe", acceso, fs, IoOperation.DELETE, "/nada");
        permitido("SUSPEND es una operación interna", acceso, fs, IoOperation.SUSPEND, "/cualquiera");
        permitido("RESUME es una operación interna", acceso, fs, IoOperation.RESUME, "/cualquiera");

        System.out.println("--- 3. Cambio de modo y nombres no válidos ---");
        acceso.setAdminMode();
        verificar("Volver a administrador restablece el usuario",
                acceso.getMode() == UserMode.ADMIN && acceso.getCurrentUser().equals("admin"));
        verificar("No se puede ser usuario 'admin'", lanza(acceso, "admin") && lanza(acceso, "ADMIN"));
        verificar("No se acepta un usuario vacío", lanza(acceso, "") && lanza(acceso, "   ") && lanza(acceso, null));
        verificar("Tras los rechazos sigue en modo administrador", acceso.getMode() == UserMode.ADMIN);

        System.out.println();
        System.out.println("Comprobaciones: " + comprobaciones + " | Fallos: " + fallos);
        if (fallos > 0) {
            System.exit(1);
        }
    }

    /** Sistema parecido al del enunciado, con directorios propios de user1 y user2. */
    private static FileSystem armarSistema() {
        FileSystem fs = new FileSystem(new Disk(200, 0));
        fs.createDirectory("/sistema", "admin", Permission.PUBLIC);
        fs.createDirectory("/usuarios", "admin", Permission.PUBLIC);
        fs.createDirectory("/usuarios/user1", "user1", Permission.PUBLIC);
        fs.createDirectory("/usuarios/user2", "user2", Permission.PUBLIC);
        fs.loadFile("/sistema/boot.bin", 0, 10, "admin", Permission.PUBLIC, "JSON");
        fs.loadFile("/usuarios/notas.txt", 10, 5, "user1", Permission.PRIVATE, "JSON");
        fs.loadFile("/usuarios/data.csv", 15, 5, "user2", Permission.PUBLIC, "JSON");
        fs.loadFile("/usuarios/secreto.txt", 20, 5, "user2", Permission.PRIVATE, "JSON");
        return fs;
    }

    private static boolean lanza(AccessControl acceso, String nombre) {
        try {
            acceso.setUserMode(nombre);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    private static void permitido(String descripcion, AccessControl acceso, FileSystem fs,
                                  IoOperation operacion, String ruta) {
        String motivo = acceso.check(fs, operacion, ruta, NodeType.FILE);
        verificar(descripcion + " -> " + (motivo == null ? "permitido" : "RECHAZADO: " + motivo), motivo == null);
    }

    private static void rechazado(String descripcion, String fragmento, AccessControl acceso, FileSystem fs,
                                  IoOperation operacion, String ruta) {
        String motivo = acceso.check(fs, operacion, ruta, NodeType.FILE);
        verificar(descripcion + " -> " + (motivo == null ? "NO se rechazó" : "'" + motivo + "'"),
                motivo != null && motivo.contains(fragmento));
    }

    private static void verificar(String descripcion, boolean ok) {
        comprobaciones++;
        if (!ok) {
            fallos++;
        }
        System.out.println((ok ? "OK    " : "FALLO ") + descripcion);
    }
}
