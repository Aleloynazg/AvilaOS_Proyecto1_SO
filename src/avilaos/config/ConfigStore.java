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
}
