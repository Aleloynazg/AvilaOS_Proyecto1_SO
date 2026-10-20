/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.hardware;

/**
 *
 * @author alexandraloynaz
 */

import avilaos.kernel.process.PCB;
import avilaos.kernel.process.ProcessState;

public class CPU implements ClockListener {

    private final int computerId;

    private PCB currentProcess;
    private CpuMode mode;

    public CPU(int computerId) {
        this.computerId = computerId;
        this.currentProcess = null;
        this.mode = CpuMode.KERNEL;
    }

    // Asigna un proceso listo a la CPU.
    public synchronized boolean assignProcess(PCB process) {

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
    }

    // Retira el proceso actual para que el kernel lo gestione.
    public synchronized PCB releaseProcess() {

        PCB process = currentProcess;

        currentProcess = null;
        mode = CpuMode.KERNEL;

        return process;
    }

    // Permite cambiar el modo de ejecución.
    public synchronized void setMode(CpuMode mode) {

        if (mode != null) {
            this.mode = mode;
        }
    }

    public synchronized PCB getCurrentProcess() {
        return currentProcess;
    }

    public synchronized CpuMode getMode() {
        return mode;
    }

    public int getComputerId() {
        return computerId;
    }

    public synchronized boolean isIdle() {
        return currentProcess == null;
    }

    // Se ejecuta cuando llega un ciclo del reloj global.
    @Override
    public synchronized void onTick(long cycle) {

        System.out.println(
            "Computador " + computerId
            + " recibió el ciclo " + cycle
        );

        if (mode != CpuMode.USER || currentProcess == null) {
            return;
        }

        if (currentProcess.isFinished()) {
            currentProcess.terminate(
                avilaos.kernel.process.TerminationCause.values()[0],
                cycle
            );

            currentProcess = null;
            mode = CpuMode.KERNEL;

            return;
        }

        if (currentProcess.isFinished()) {
            return;
        }

        currentProcess.advance();
        currentProcess.incrementCyclesOnCpu();
        currentProcess.markFirstRun(cycle);

        System.out.println(
            "Computador " + computerId
            + " ejecuta " + currentProcess.getTag()
            + " | PC: " + currentProcess.getProgramCounter()
            + " | Instrucciones restantes: "
            + currentProcess.getRemainingInstructions()
        );
    }
}