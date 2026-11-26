package avilaos;

import avilaos.config.SimulationFactory;
import avilaos.gui.MainWindow;
import avilaos.gui.StartupDialog;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 * Punto de entrada del Proyecto 2: pide los dos JSON (procesos y sistema de
 * archivos), arma el sistema y abre la ventana. El reloj no arranca hasta que
 * se pulsa "Iniciar" en la ventana principal.
 */
public class AvilaOS {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            StartupDialog startup = new StartupDialog();

            while (true) {
                // 1. Los dos archivos JSON
                StartupDialog.Result loaded = startup.mostrar();
                if (loaded == null) {
                    System.exit(0);
                }

                try {
                    // 2. El sistema (lógica): disco, sistema de archivos, computador, reloj y procesos
                    SimulationFactory.Simulation simulation =
                            SimulationFactory.createP2(loaded.processes, loaded.fileSystem);

                    // 3. La ventana (observa al sistema)
                    new MainWindow(simulation.clock, simulation.computer).setVisible(true);
                    startup.dispose();
                    return;
                } catch (RuntimeException ex) {
                    JOptionPane.showMessageDialog(null, ex.getMessage(),
                            "No se pudo armar la simulación", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
    }
}