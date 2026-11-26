package avilaos.gui;

import avilaos.config.ConfigStore;
import avilaos.config.FileSystemConfig;
import avilaos.config.ProcessesConfig;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.io.IOException;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Vista inicial del Proyecto 2. Antes de arrancar el reloj hay que cargar los DOS
 * archivos JSON: el de procesos y configuración, y el del sistema de archivos.
 * El botón "Iniciar simulación" solo se habilita cuando ambos están cargados.
 */
public class StartupDialog extends JDialog {

    /** Lo que se cargó: los dos archivos ya leídos y validados. */
    public static final class Result {
        public final ProcessesConfig processes;
        public final FileSystemConfig fileSystem;

        Result(ProcessesConfig processes, FileSystemConfig fileSystem) {
            this.processes = processes;
            this.fileSystem = fileSystem;
        }
    }

    private ProcessesConfig processes;
    private FileSystemConfig fileSystem;
    private Result result;

    private final JLabel processesLabel = new JLabel("(sin cargar)");
    private final JLabel fileSystemLabel = new JLabel("(sin cargar)");
    private final JButton startButton = new JButton("Iniciar simulación");

    public StartupDialog() {
        super((java.awt.Frame) null, "ÁvilaOS – Cargar configuración", true);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.anchor = GridBagConstraints.WEST;

        JLabel intro = new JLabel("<html>Para iniciar el reloj hay que cargar los <b>dos</b> archivos JSON.<br>"
                + "Los del enunciado están en la carpeta <i>config/p2</i> del proyecto.</html>");
        c.gridx = 0; c.gridy = 0; c.gridwidth = 3;
        form.add(intro, c);
        c.gridwidth = 1;

        JButton loadProcesses = new JButton("Cargar procesos (JSON)…");
        JButton loadFileSystem = new JButton("Cargar sistema de archivos (JSON)…");
        loadProcesses.addActionListener(e -> cargarProcesos());
        loadFileSystem.addActionListener(e -> cargarSistemaArchivos());

        c.gridx = 0; c.gridy = 1;
        form.add(new JLabel("1. Procesos y configuración:"), c);
        c.gridx = 1;
        form.add(loadProcesses, c);
        c.gridx = 2;
        form.add(processesLabel, c);

        c.gridx = 0; c.gridy = 2;
        form.add(new JLabel("2. Sistema de archivos:"), c);
        c.gridx = 1;
        form.add(loadFileSystem, c);
        c.gridx = 2;
        form.add(fileSystemLabel, c);

        JButton exitButton = new JButton("Salir");
        startButton.setEnabled(false);
        startButton.addActionListener(e -> {
            result = new Result(processes, fileSystem);
            setVisible(false);
        });
        exitButton.addActionListener(e -> {
            result = null;
            setVisible(false);
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(exitButton);
        buttons.add(startButton);

        setLayout(new BorderLayout());
        add(form, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(null);
    }

    /** Muestra la vista y espera. Devuelve los dos archivos, o null si se eligió Salir. */
    public Result mostrar() {
        result = null;
        setVisible(true);
        return result;
    }

    private File elegirArchivo(String titulo) {
        File folder = new File("config/p2");
        JFileChooser chooser = new JFileChooser(folder.isDirectory() ? folder : new File("."));
        chooser.setDialogTitle(titulo);
        chooser.setFileFilter(new FileNameExtensionFilter("Archivo JSON (*.json)", "json"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        return chooser.getSelectedFile();
    }

    private void cargarProcesos() {
        File file = elegirArchivo("Elegir el JSON de procesos y configuración");
        if (file == null) {
            return;
        }
        try {
            ProcessesConfig loaded = ConfigStore.loadProcesses(file);
            loaded.validate();
            processes = loaded;
            processesLabel.setText("✔ " + file.getName());
        } catch (IOException | RuntimeException ex) {
            processes = null;
            processesLabel.setText("(sin cargar)");
            JOptionPane.showMessageDialog(this, "No se pudo cargar " + file.getName() + ":\n" + ex.getMessage(),
                    "Archivo de procesos inválido", JOptionPane.ERROR_MESSAGE);
        }
        actualizarBoton();
    }

    private void cargarSistemaArchivos() {
        File file = elegirArchivo("Elegir el JSON del sistema de archivos");
        if (file == null) {
            return;
        }
        try {
            FileSystemConfig loaded = ConfigStore.loadFileSystem(file);
            loaded.validate();
            fileSystem = loaded;
            fileSystemLabel.setText("✔ " + file.getName());
        } catch (IOException | RuntimeException ex) {
            fileSystem = null;
            fileSystemLabel.setText("(sin cargar)");
            JOptionPane.showMessageDialog(this, "No se pudo cargar " + file.getName() + ":\n" + ex.getMessage(),
                    "Archivo del sistema de archivos inválido", JOptionPane.ERROR_MESSAGE);
        }
        actualizarBoton();
    }

    private void actualizarBoton() {
        startButton.setEnabled(processes != null && fileSystem != null);
        pack();
    }
}