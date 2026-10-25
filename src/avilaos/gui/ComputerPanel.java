package avilaos.gui;

import avilaos.hardware.CPU;
import avilaos.hardware.Computer;
import avilaos.kernel.Kernel;
import avilaos.kernel.memory.MemoryManager;
import avilaos.kernel.scheduler.PriorityPolicy;
import avilaos.kernel.process.BlockReason;
import avilaos.kernel.process.PCB;
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
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;

/**
 * Panel de UN computador. Solo OBSERVA al Computer: lee su estado y lo dibuja.
 * Se reutiliza: un panel por cada computador del clúster.
 */
public class ComputerPanel extends JPanel {

    private final Computer computer;

    // Parte de arriba: política y memoria
    private final JComboBox<SchedulingPolicyType> policySelector = new JComboBox<>(SchedulingPolicyType.values());
    private final JProgressBar ramBar = new JProgressBar(0, 100);

    // CPU
    private final JLabel cpuLabel = new JLabel();
    private final JLabel modeLabel = new JLabel();

    // Las 4 colas
    private final DefaultListModel<String> newModel = new DefaultListModel<>();
    private final DefaultListModel<String> readyModel = new DefaultListModel<>();
    private final DefaultListModel<String> blockedModel = new DefaultListModel<>();
    private final DefaultListModel<String> terminatedModel = new DefaultListModel<>();

    public ComputerPanel(Computer computer) {
        this.computer = computer;
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createTitledBorder("Computador " + computer.getId()));

        // --- Arriba: selector de política + barra de RAM ---
        JPanel top = new JPanel(new GridLayout(2, 2, 5, 5));
        top.add(new JLabel("Política:"));
        top.add(policySelector);
        top.add(new JLabel("RAM:"));
        ramBar.setStringPainted(true);
        top.add(ramBar);
        policySelector.addActionListener(e -> changePolicy());

        // --- Centro: CPU arriba, colas abajo ---
        JPanel cpuPanel = new JPanel(new GridLayout(2, 1));
        cpuPanel.setBorder(BorderFactory.createTitledBorder("CPU"));
        cpuPanel.add(cpuLabel);
        cpuPanel.add(modeLabel);

        JPanel queues = new JPanel(new GridLayout(2, 2, 5, 5));
        queues.add(queuePanel("Nuevos", newModel));
        queues.add(queuePanel("Listos", readyModel));
        queues.add(queuePanel("Bloqueados", blockedModel));
        queues.add(queuePanel("Terminados", terminatedModel));

        JPanel center = new JPanel(new BorderLayout(5, 5));
        center.add(cpuPanel, BorderLayout.NORTH);
        center.add(queues, BorderLayout.CENTER);

        add(top, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);

        refresh();
    }

    /** Un recuadro con título y una lista con scroll. */
    private JPanel queuePanel(String title, DefaultListModel<String> model) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));
        panel.add(new JScrollPane(new JList<>(model)), BorderLayout.CENTER);
        return panel;
    }

    /** Vuelve a leer el estado del computador y actualiza todo lo que se ve. */
    public void refresh() {
        Kernel kernel = computer.getKernel();
        CPU cpu = computer.getCPU();

        // RAM
        MemoryManager ram = kernel.getMemory();
        ramBar.setValue(ram.getUsagePercent());
        ramBar.setString(ram.getUsedMemory() + " / " + ram.getTotalMemory()
                + " MB (libre: " + ram.getFreeMemory() + ")");

        // CPU
        PCB running = cpu.getCurrentProcess();
        if (running == null) {
            cpuLabel.setText("Proceso: (libre)");
        } else {
            cpuLabel.setText("Proceso: " + running.getTag() + " " + running.getName()
                    + "  |  PC: " + running.getProgramCounter()
                    + "  |  Prioridad: " + running.getPriority()
                    + "  |  Deadline: " + running.getDeadline());
        }
        modeLabel.setText("Modo: " + cpu.getMode().getLabel());

        // Colas
        fill(newModel, kernel.getNewQueue());
        fill(readyModel, kernel.getReadyQueue());
        fill(blockedModel, kernel.getBlockedQueue());
        fill(terminatedModel, kernel.getTerminatedQueue());
    }

    /** Copia el contenido de una cola del Kernel a la lista que se ve. */
    private void fill(DefaultListModel<String> model, LinkedQueue<PCB> queue) {
        Object[] processes = queue.toArray();
        model.clear();
        for (Object o : processes) {
            model.addElement(describe((PCB) o));
        }
    }

    /** Texto corto de un proceso en una cola. */
    private String describe(PCB p) {
        String text = p.getTag() + " " + p.getName()
                + " | prio " + p.getPriority()
                + " | " + p.getMemoryRequired() + " MB"
                + " | dl " + p.getDeadline()
                + " | resta " + p.getRemainingInstructions();
        if (p.getBlockReason() != BlockReason.NONE) {
            text += " | " + p.getBlockReason().getLabel();
        }
        if (p.getFinishCycle() >= 0) {
            text += " | " + p.getTerminationCause();
        }
        return text;
    }

    /** El usuario eligió otra política en el selector. */
    private void changePolicy() {
        SchedulingPolicyType type = (SchedulingPolicyType) policySelector.getSelectedItem();
        SchedulingPolicy policy = createPolicy(type);
        if (policy == null) {
            JOptionPane.showMessageDialog(this,
                    "La política " + type.getLabel() + " todavía no está implementada.",
                    "Política no disponible", JOptionPane.WARNING_MESSAGE);
            policySelector.setSelectedItem(computer.getPolicy().getType());
            return;
        }
        computer.setPolicy(policy);
    }

    private SchedulingPolicy createPolicy(SchedulingPolicyType type) {
        switch (type) {
            case FCFS:
                return new FCFSPolicy();
            case ROUND_ROBIN:
                return new RoundRobinPolicy(4);   // quantum por defecto de DECISIONES.md
            case EDF:
                return new EDFPolicy();
            case PRIORITY_PREEMPTIVE:
                return new PriorityPolicy();
            default:
                return null;                      // Prioridades: Fase 2
        }
    }
}