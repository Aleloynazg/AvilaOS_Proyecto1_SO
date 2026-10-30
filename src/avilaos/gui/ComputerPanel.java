package avilaos.gui;

import avilaos.hardware.CPU;
import avilaos.hardware.Computer;
import avilaos.kernel.Kernel;
import avilaos.kernel.memory.MemoryManager;
import avilaos.kernel.scheduler.PriorityPolicy;
import avilaos.kernel.process.BlockReason;
import avilaos.kernel.process.PCB;
import avilaos.kernel.scheduler.PriorityPolicy;
import avilaos.kernel.scheduler.EDFPolicy;
import avilaos.kernel.scheduler.FCFSPolicy;
import avilaos.kernel.scheduler.RoundRobinPolicy;
import avilaos.kernel.scheduler.SchedulingPolicy;
import avilaos.kernel.scheduler.SchedulingPolicyType;
import avilaos.structures.LinkedQueue;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;

/**
 * Panel de UN computador. Solo OBSERVA al Computer: lee su estado y lo dibuja.
 * Se reutiliza: un panel por cada computador del clúster.
 */

public class ComputerPanel extends JPanel {

    private final Computer computer;

    // Política, quantum y RAM
    private final JComboBox<SchedulingPolicyType> policySelector =
            new JComboBox<>(SchedulingPolicyType.values());

    private final javax.swing.JSpinner quantumSelector =
            new javax.swing.JSpinner(
                    new javax.swing.SpinnerNumberModel(4, 1, 100, 1));

    private final JProgressBar ramBar = new JProgressBar(0, 100);

    // CPU
    private final JLabel cpuLabel = new JLabel();
    private final JLabel cpuDetailLabel = new JLabel(" ");
    private final JLabel modeLabel = new JLabel();

    // Modelos de las cuatro colas
    private final DefaultListModel<String> newModel =
            new DefaultListModel<>();
    private final DefaultListModel<String> readyModel =
            new DefaultListModel<>();
    private final DefaultListModel<String> blockedModel =
            new DefaultListModel<>();
    private final DefaultListModel<String> terminatedModel =
            new DefaultListModel<>();

    // Listas visibles
    private final JList<String> newList = new JList<>(newModel);
    private final JList<String> readyList = new JList<>(readyModel);
    private final JList<String> blockedList = new JList<>(blockedModel);
    private final JList<String> terminatedList =
            new JList<>(terminatedModel);

    // Procesos correspondientes a las filas de cada lista
    private PCB[] newProcesses = new PCB[0];
    private PCB[] readyProcesses = new PCB[0];
    private PCB[] blockedProcesses = new PCB[0];
    private PCB[] terminatedProcesses = new PCB[0];

    // Proceso seleccionado
    private PCB selectedProcess;

    // Botón para abrir la ventana de detalles
    private final JButton detailsButton =
            new JButton("Ver detalles del PCB");

    public ComputerPanel(Computer computer) {
        this.computer = computer;

        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createTitledBorder(
                "Computador " + computer.getId()));

        // Parte superior: política, quantum y RAM
        JPanel top = new JPanel(new GridLayout(3, 2, 5, 5));

        top.add(new JLabel("Política:"));
        top.add(policySelector);

        top.add(new JLabel("Quantum Round Robin:"));
        top.add(quantumSelector);

        top.add(new JLabel("RAM:"));
        ramBar.setStringPainted(true);
        top.add(ramBar);

        // Mostrar la política y el quantum con que arrancó el computador (por ejemplo, de un JSON)
        policySelector.setSelectedItem(computer.getPolicy().getType());
        if (computer.getPolicy() instanceof RoundRobinPolicy) {
            quantumSelector.setValue(((RoundRobinPolicy) computer.getPolicy()).getQuantum());
        }

        policySelector.addActionListener(e -> changePolicy());

        quantumSelector.addChangeListener(e -> {
            if (computer.getPolicy().getType()
                    == SchedulingPolicyType.ROUND_ROBIN) {
                computer.setPolicy(new RoundRobinPolicy(
                        (int) quantumSelector.getValue()));
            }
        });

        // Panel de CPU
        JPanel cpuPanel = new JPanel(new GridLayout(3, 1));
        cpuPanel.setBorder(BorderFactory.createTitledBorder("CPU"));
        cpuPanel.add(cpuLabel);
        cpuPanel.add(cpuDetailLabel);
        cpuPanel.add(modeLabel);

        // Configurar selección de las listas
        configurarLista(newList);
        configurarLista(readyList);
        configurarLista(blockedList);
        configurarLista(terminatedList);

        newList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()
                    && newList.getSelectedIndex() >= 0) {
                seleccionarProceso(
                        newProcesses[newList.getSelectedIndex()]);
            }
        });

        readyList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()
                    && readyList.getSelectedIndex() >= 0) {
                seleccionarProceso(
                        readyProcesses[readyList.getSelectedIndex()]);
            }
        });

        blockedList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()
                    && blockedList.getSelectedIndex() >= 0) {
                seleccionarProceso(
                        blockedProcesses[blockedList.getSelectedIndex()]);
            }
        });

        terminatedList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()
                    && terminatedList.getSelectedIndex() >= 0) {
                seleccionarProceso(
                        terminatedProcesses[
                                terminatedList.getSelectedIndex()]);
            }
        });

        // Las cuatro colas
        JPanel queues = new JPanel(new GridLayout(2, 2, 5, 5));
        queues.add(queuePanel("Nuevos", newList));
        queues.add(queuePanel("Listos", readyList));
        queues.add(queuePanel("Bloqueados", blockedList));
        queues.add(queuePanel("Terminados", terminatedList));

        // Botón de detalles
        detailsButton.addActionListener(e -> abrirDetalles());

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(detailsButton, BorderLayout.EAST);

        // Centro de la interfaz
        JPanel center = new JPanel(new BorderLayout(5, 5));
        center.add(cpuPanel, BorderLayout.NORTH);
        center.add(queues, BorderLayout.CENTER);
        center.add(bottom, BorderLayout.SOUTH);

        add(top, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);

        refresh();
    }

    private void configurarLista(JList<String> lista) {
        lista.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    }

    private JPanel queuePanel(String title, JList<String> lista) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));
        panel.add(new JScrollPane(lista), BorderLayout.CENTER);
        return panel;
    }

    /**
     * Actualiza la información visible del computador.
     */
    public void refresh() {
        Kernel kernel = computer.getKernel();
        CPU cpu = computer.getCPU();

        // RAM
        MemoryManager ram = kernel.getMemory();

        ramBar.setValue(ram.getUsagePercent());
        ramBar.setString(
                ram.getUsedMemory() + " / "
                + ram.getTotalMemory()
                + " MB (libre: " + ram.getFreeMemory() + ")");

        // CPU
        PCB running = cpu.getCurrentProcess();

        if (running == null) {
            cpuLabel.setText("Proceso: (libre)");
            cpuDetailLabel.setText(" ");
        } else {
            cpuLabel.setText(
                    "Proceso: " + running.getTag()
                    + " " + running.getName());
            cpuDetailLabel.setText(
                    "PC: " + running.getProgramCounter()
                    + " | Prioridad: " + running.getPriority()
                    + " | Deadline: " + running.getDeadline());
        }

        modeLabel.setText("Modo: " + cpu.getMode().getLabel());

        // Actualizar las colas
        newProcesses = obtenerProcesos(kernel.getNewQueue());
        actualizarLista(newModel, newProcesses);

        readyProcesses = obtenerProcesosOrdenados(
                kernel.getReadyQueue());
        actualizarLista(readyModel, readyProcesses);

        blockedProcesses = obtenerProcesos(kernel.getBlockedQueue());
        actualizarLista(blockedModel, blockedProcesses);

        terminatedProcesses =
                obtenerProcesos(kernel.getTerminatedQueue());
        actualizarLista(terminatedModel, terminatedProcesses);
    }

    private PCB[] obtenerProcesos(LinkedQueue<PCB> queue) {
        Object[] elementos = queue.toArray();
        PCB[] procesos = new PCB[elementos.length];

        for (int i = 0; i < elementos.length; i++) {
            procesos[i] = (PCB) elementos[i];
        }

        return procesos;
    }

    private void actualizarLista(
            DefaultListModel<String> model, PCB[] procesos) {

        model.clear();

        for (int i = 0; i < procesos.length; i++) {
            model.addElement(describe(procesos[i]));
        }
    }

    /**
     * Ordena una copia de la cola de listos sin alterar la cola original.
     */
    private PCB[] obtenerProcesosOrdenados(
            LinkedQueue<PCB> queue) {

        PCB[] procesos = obtenerProcesos(queue);
        SchedulingPolicyType type = computer.getPolicy().getType();

        for (int i = 0; i < procesos.length - 1; i++) {
            int posicionMenor = i;

            for (int j = i + 1; j < procesos.length; j++) {
                boolean debeCambiar = false;

                if (type == SchedulingPolicyType.EDF) {
                    debeCambiar = procesos[j].getDeadline()
                            < procesos[posicionMenor].getDeadline();

                } else if (type
                        == SchedulingPolicyType.PRIORITY_PREEMPTIVE) {
                    debeCambiar = procesos[j].getPriority()
                            < procesos[posicionMenor].getPriority();
                }

                if (debeCambiar) {
                    posicionMenor = j;
                }
            }

            if (posicionMenor != i) {
                PCB temporal = procesos[i];
                procesos[i] = procesos[posicionMenor];
                procesos[posicionMenor] = temporal;
            }
        }

        return procesos;
    }

    /**
     * Texto de un proceso en una cola. Va en varias líneas (HTML) para que
     * entre en la lista sin cortarse y el motivo de bloqueo siempre se vea.
     */
    private String describe(PCB p) {
        String text = "<html><b>" + escape(p.getTag() + " " + p.getName()) + "</b>"
                + "<br>prio " + p.getPriority()
                + " | dl " + p.getDeadline()
                + " | " + p.getMemoryRequired() + " MB"
                + "<br>resta " + p.getRemainingInstructions() + " instr.";

        if (p.getBlockReason() != BlockReason.NONE) {
            text += "<br><font color='#B00020'>"
                    + shortReason(p.getBlockReason()) + "</font>";
        }

        if (p.getFinishCycle() >= 0) {
            text += "<br>" + p.getTerminationCause();
        }

        return text + "</html>";
    }

    /** Motivo corto para las listas; el texto completo está en el PCB. */
    private String shortReason(BlockReason reason) {
        switch (reason) {
            case IO:
                return "E/S";
            case SEM_EMPTY:
                return "sem e: lleno";
            case SEM_FULL:
                return "sem n: vacío";
            case SEM_MUTEX:
                return "sem s: mutex";
            case NETWORK:
                return "red remota";
            default:
                return reason.getLabel();
        }
    }

    private String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void seleccionarProceso(PCB proceso) {
        selectedProcess = proceso;
    }

    /**
     * Abre la ventana de detalles para el proceso seleccionado.
     */
    private void abrirDetalles() {
    PCB proceso = selectedProcess;

    if (proceso == null) {
        proceso = computer.getCPU().getCurrentProcess();
    }

    if (proceso == null) {
        JOptionPane.showMessageDialog(
                this,
                "Selecciona un proceso de una cola "
                + "o espera a que la CPU ejecute uno.",
                "Sin proceso seleccionado",
                JOptionPane.INFORMATION_MESSAGE);
        return;
    }

    java.awt.Window ventanaPadre =
            javax.swing.SwingUtilities.getWindowAncestor(this);

    PCBDetails ventana = new PCBDetails(ventanaPadre, proceso);
    ventana.setVisible(true);
}

    private void changePolicy() {
        SchedulingPolicyType type =
                (SchedulingPolicyType) policySelector.getSelectedItem();

        if (type == null) {
            return;
        }

        SchedulingPolicy policy = createPolicy(type);

        if (policy == null) {
            JOptionPane.showMessageDialog(
                    this,
                    "La política " + type.getLabel()
                    + " todavía no está implementada.",
                    "Política no disponible",
                    JOptionPane.WARNING_MESSAGE);

            policySelector.setSelectedItem(
                    computer.getPolicy().getType());
            return;
        }

        computer.setPolicy(policy);
        refresh();
    }

    private SchedulingPolicy createPolicy(
            SchedulingPolicyType type) {

        switch (type) {
            case FCFS:
                return new FCFSPolicy();

            case ROUND_ROBIN:
                return new RoundRobinPolicy(
                        (int) quantumSelector.getValue());

            case EDF:
                return new EDFPolicy();

            case PRIORITY_PREEMPTIVE:
                return new PriorityPolicy();

            default:
                return null;
        }
    }
}