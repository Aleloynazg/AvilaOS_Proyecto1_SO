/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.hardware;

/**
 *
 * @author alexandraloynaz
 */

import avilaos.kernel.SystemEventListener;
import avilaos.kernel.process.PCB;
import avilaos.kernel.process.ProcessState;
import java.util.concurrent.Semaphore;

public class CPU implements ClockListener {

    private final int computerId;
    private PCB currentProcess;
    private CpuMode mode;

    private final Semaphore semaforo = new Semaphore(1);
    private SystemEventListener eventListener;

    public CPU(int computerId) {
        this.computerId = computerId;
        this.currentProcess = null;
        this.mode = CpuMode.KERNEL;
        this.eventListener = null;
    }

    // Permite recibir eventos sin depender directamente de la GUI.
    public void setEventListener(SystemEventListener listener) {
        semaforo.acquireUninterruptibly();

        try {
            this.eventListener = listener;
        } finally {
            semaforo.release();
        }
    }

    private void registrarEvento(long cycle, String message) {
        if (eventListener != null) {
            eventListener.onEvent(cycle, message);
        }
    }

    public boolean assignProcess(PCB process) {
        semaforo.acquireUninterruptibly();

        try {
            if (process == null || currentProcess != null) {
                return false;
            }

            if (process.getState() != ProcessState.READY) {
                return false;
            }

            currentProcess = process;
            currentProcess.setState(ProcessState.RUNNING);
            mode = CpuMode.USER;

            return true;
        } finally {
            semaforo.release();
        }
    }

    public PCB releaseProcess() {
        semaforo.acquireUninterruptibly();

        try {
            PCB process = currentProcess;

            currentProcess = null;
            mode = CpuMode.KERNEL;

            return process;
        } finally {
            semaforo.release();
        }
    }

    public void setMode(CpuMode mode) {
        semaforo.acquireUninterruptibly();

        try {
            if (mode != null) {
                this.mode = mode;
            }
        } finally {
            semaforo.release();
        }
    }

    public PCB getCurrentProcess() {
        semaforo.acquireUninterruptibly();

        try {
            return currentProcess;
        } finally {
            semaforo.release();
        }
    }

    public CpuMode getMode() {
        semaforo.acquireUninterruptibly();

        try {
            return mode;
        } finally {
            semaforo.release();
        }
    }

    public int getComputerId() {
        return computerId;
    }

    public boolean isIdle() {
        semaforo.acquireUninterruptibly();

        try {
            return currentProcess == null;
        } finally {
            semaforo.release();
        }
    }

    @Override
    public void onTick(long cycle) {
        semaforo.acquireUninterruptibly();

        try {

            if (mode != CpuMode.USER || currentProcess == null) {
                return;
            }

            if (currentProcess.isFinished()) {
                return;
            }

            currentProcess.advance();
            currentProcess.incrementCyclesOnCpu();
            currentProcess.markFirstRun(cycle);

            registrarEvento(
                cycle,
                "ejecuta " + currentProcess.getTag()
                + " | PC: " + currentProcess.getProgramCounter()
                + " | Instrucciones restantes: "
                + currentProcess.getRemainingInstructions()
            );
        } finally {
            semaforo.release();
        }
    }
}