package avilaos.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Guarda y carga la configuración como JSON. */
public final class ConfigStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ConfigStore() { }

    public static void save(SimulationConfig config, File file) throws IOException {
        config.validate();
        Files.writeString(file.toPath(), GSON.toJson(config), StandardCharsets.UTF_8);
    }

    /**
     * Lee y valida un archivo de configuración.
     * @throws IOException si no se puede leer el archivo
     * @throws IllegalArgumentException si el JSON está mal formado o tiene valores inválidos
     */
    public static SimulationConfig load(File file) throws IOException {
        String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        SimulationConfig config;
        try {
            config = GSON.fromJson(text, SimulationConfig.class);
        } catch (JsonParseException e) {
            throw new IllegalArgumentException("El archivo no es un JSON válido: " + e.getMessage());
        }
        if (config == null) {
            throw new IllegalArgumentException("El archivo está vacío");
        }
        config.validate();
        return config;
    }

    /**
     * Lee y valida el JSON del sistema de archivos (formato del enunciado).
     *
     * @throws IOException si no se puede leer el archivo
     * @throws IllegalArgumentException si el JSON está mal formado o tiene valores inválidos
     */
    public static FileSystemConfig loadFileSystem(File file) throws IOException {
        String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        FileSystemConfig config = parseFileSystem(text);
        config.validate();
        return config;
    }

    /** Convierte el texto de un JSON de sistema de archivos en su configuración (sin validar). */
    public static FileSystemConfig parseFileSystem(String text) {
        FileSystemConfig config;
        try {
            config = GSON.fromJson(text, FileSystemConfig.class);
        } catch (JsonParseException e) {
            String detalle = e.getCause() != null && e.getCause().getMessage() != null
                    ? e.getCause().getMessage() : e.getMessage();
            throw new IllegalArgumentException("El archivo del sistema de archivos no es un JSON válido: "
                    + detalle);
        }
        if (config == null) {
            throw new IllegalArgumentException("El archivo del sistema de archivos está vacío");
        }
        return config;
    }

    /** Guarda el estado del sistema de archivos en el formato del enunciado. */
    public static void saveFileSystem(FileSystemConfig config, File file) throws IOException {
        config.validate();
        Files.writeString(file.toPath(), toJson(config), StandardCharsets.UTF_8);
    }

    /** Texto JSON (con sangría) de una configuración de sistema de archivos. */
    public static String toJson(FileSystemConfig config) {
        return GSON.toJson(config);
    }

    /**
     * Lee y valida el JSON de procesos y configuración (formato del enunciado).
     *
     * @throws IOException si no se puede leer el archivo
     * @throws IllegalArgumentException si el JSON está mal formado o tiene valores inválidos
     */
    public static ProcessesConfig loadProcesses(File file) throws IOException {
        String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        ProcessesConfig config = parseProcesses(text);
        config.validate();
        return config;
    }

    /** Convierte el texto de un JSON de procesos en su configuración (sin validar). */
    public static ProcessesConfig parseProcesses(String text) {
        ProcessesConfig config;
        try {
            config = GSON.fromJson(text, ProcessesConfig.class);
        } catch (JsonParseException e) {
            String detalle = e.getCause() != null && e.getCause().getMessage() != null
                    ? e.getCause().getMessage() : e.getMessage();
            throw new IllegalArgumentException("El archivo de procesos no es un JSON válido: " + detalle);
        }
        if (config == null) {
            throw new IllegalArgumentException("El archivo de procesos está vacío");
        }
        return config;
    }

    /** Guarda procesos y configuración en el formato del enunciado. */
    public static void saveProcesses(ProcessesConfig config, File file) throws IOException {
        config.validate();
        Files.writeString(file.toPath(), toJson(config), StandardCharsets.UTF_8);
    }

    /** Texto JSON (con sangría) de procesos y configuración. */
    public static String toJson(ProcessesConfig config) {
        return GSON.toJson(config);
    }
}
