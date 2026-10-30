package avilaos;

import avilaos.config.SimulationConfig;
import avilaos.config.SimulationFactory;
import avilaos.gui.MainWindow;
import avilaos.gui.StartupDialog;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/** Punto de entrada: pide la configuración, arma el sistema y abre la ventana. */
public class AvilaOS {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            StartupDialog startup = new StartupDialog();

            while (true) {
                // 1. La configuración: escrita en la vista inicial o cargada de un JSON
                SimulationConfig config = startup.mostrar();
                if (config == null) {
                    System.exit(0);
                }

                try {
                    // 2. El sistema (lógica): clúster, reloj, buffers y procesos iniciales
                    SimulationFactory.Simulation simulation = SimulationFactory.create(config);

                    // 3. La ventana (observa al sistema)
                    new MainWindow(simulation.clock, simulation.cluster).setVisible(true);
                    return;
                } catch (IllegalArgumentException ex) {
                    JOptionPane.showMessageDialog(null, ex.getMessage(),
                            "No se pudo armar la simulación", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
    }
}
