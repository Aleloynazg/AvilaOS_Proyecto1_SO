package avilaos;

import avilaos.gui.MainWindow;
import avilaos.hardware.Computer;
import avilaos.hardware.GlobalClock;
import avilaos.kernel.process.PCB;
import avilaos.kernel.process.ProcessType;
import javax.swing.SwingUtilities;
import avilaos.kernel.process.ProcessFactory;

/** Punto de entrada: arma el sistema y abre la ventana. */
public class AvilaOS {

    public static void main(String[] args) {
        // 1. El sistema (lógica): 2 computadores y el reloj
        Computer c1 = new Computer(1, 1000);
        Computer c2 = new Computer(2, 1000);
        Computer[] computers = {c1, c2};

        GlobalClock clock = new GlobalClock(500);
        c1.conectarReloj(clock);
        c2.conectarReloj(clock);

        // 2. La ventana (observa al sistema)
        SwingUtilities.invokeLater(() -> {
            MainWindow window = new MainWindow(clock, computers);
            window.setVisible(true);

            // 3. Procesos de prueba (en la Fase 2 se crearán desde un formulario)
            //               nombre, computador, tipo, prioridad, memoria, deadline, instrucciones
            c1.getKernel().submitProcess(
               
            ProcessFactory.ioBound("Editor", 1, 1, 200, 60, 8, 3, 4),
                0
            );
            

            c1.getKernel().submitProcess(
                new PCB("Navegador", 1, ProcessType.CPU_BOUND, 3, 500, 60, 6),
                0
            );
            c1.getKernel().submitProcess(new PCB("Juego", 1, ProcessType.CPU_BOUND, 2, 300, 60, 5), 0);
            c2.getKernel().submitProcess(new PCB("Compilador", 2, ProcessType.CPU_BOUND, 1, 700, 40, 10), 0);
            c2.getKernel().submitProcess(new PCB("Música", 2, ProcessType.CPU_BOUND, 2, 200, 4, 10), 0);
        });
    }
}

/*
c1.getKernel().submitProcess(
               
            ProcessFactory.ioBound("Editor", 1, 1, 200, 40, 8, 3, 4),
                0
            );
            

            c1.getKernel().submitProcess(
                new PCB("Navegador", 1, ProcessType.CPU_BOUND, 3, 500, 30, 6),
                0
            );
            c1.getKernel().submitProcess(new PCB("Juego", 1, ProcessType.CPU_BOUND, 2, 300, 60, 5), 0);
        c2.getKernel().submitProcess(
               
            ProcessFactory.ioBound("Editor", 1, 1, 200, 60, 8, 3, 4),
                0
            );
            

            c2.getKernel().submitProcess(
                new PCB("Navegador", 1, ProcessType.CPU_BOUND, 3, 500, 60, 6),
                0
            );
            c2.getKernel().submitProcess(new PCB("Juego", 1, ProcessType.CPU_BOUND, 2, 300, 60, 5), 0);
*/