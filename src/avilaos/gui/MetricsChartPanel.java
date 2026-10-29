/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.gui;

import avilaos.hardware.ComputerMetrics;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JPanel;
/**
 *
 * @author alexandraloynaz
 */
public class MetricsChartPanel extends JPanel{
    private final ComputerMetrics metricas;
    private final boolean mostrarCPU;

    public MetricsChartPanel(
            ComputerMetrics metricas, boolean mostrarCPU) {

        this.metricas = metricas;
        this.mostrarCPU = mostrarCPU;

        setPreferredSize(new Dimension(500, 300));
        setBackground(Color.WHITE);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D grafico = (Graphics2D) g;
        grafico.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        int ancho = getWidth();
        int alto = getHeight();

        int margenIzquierdo = 60;
        int margenSuperior = 45;
        int margenInferior = 45;
        int margenDerecho = 25;

        int anchoGrafica = ancho
                - margenIzquierdo - margenDerecho;

        int altoGrafica = alto
                - margenSuperior - margenInferior;

        grafico.setColor(Color.BLACK);

        String titulo = mostrarCPU
                ? "Uso acumulado de CPU"
                : "Procesos terminados acumulados";

        grafico.drawString(titulo, margenIzquierdo, 25);

        grafico.drawLine(
                margenIzquierdo, margenSuperior,
                margenIzquierdo, alto - margenInferior);

        grafico.drawLine(
                margenIzquierdo, alto - margenInferior,
                ancho - margenDerecho, alto - margenInferior);

        grafico.drawString(
                "Ciclo",
                ancho / 2 - 15,
                alto - 10);
        int cantidad = metricas.getCantidadRegistros();

        if (cantidad == 0) {
            grafico.drawString(
                    "No hay datos registrados.",
                    margenIzquierdo + 20,
                    margenSuperior + 30);
            return;
        }
        long cicloInicial = metricas.getCicloHistorial(0);
        long cicloFinal = metricas.getCicloHistorial(cantidad - 1);

        long rangoCiclos = cicloFinal - cicloInicial;

        if (rangoCiclos == 0) {
            rangoCiclos = 1;
        }

        double valorMaximo = 100;

        if (!mostrarCPU) {
            valorMaximo = 1;

            for (int i = 0; i < cantidad; i++) {
                long valor =
                        metricas.getProcesosTerminadosHistorial(i);

                if (valor > valorMaximo) {
                    valorMaximo = valor;
                }
            }
        }

        grafico.drawString(
                String.format("%.0f", valorMaximo),
                15,
                margenSuperior + 5);

        grafico.setColor(Color.BLUE);

        int xAnterior = 0;
        int yAnterior = 0;

        for (int i = 0; i < cantidad; i++) {

            long ciclo = metricas.getCicloHistorial(i);

            double valor;

            if (mostrarCPU) {
                valor = metricas.getUsoCPUHistorial(i);
            } else {
                valor = metricas
                        .getProcesosTerminadosHistorial(i);
            }
            int x = margenIzquierdo
                    + (int) ((ciclo - cicloInicial)
                    * anchoGrafica / rangoCiclos);
            int y = alto - margenInferior
                    - (int) (valor / valorMaximo * altoGrafica);
            if (i > 0) {
                grafico.drawLine(
                        xAnterior, yAnterior, x, y);
            }

            grafico.fillOval(x - 3, y - 3, 6, 6);

            xAnterior = x;
            yAnterior = y;
        }
    }
}
    
    

