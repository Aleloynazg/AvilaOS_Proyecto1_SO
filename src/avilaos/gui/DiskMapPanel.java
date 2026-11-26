package avilaos.gui;

import avilaos.hardware.disk.Disk;
import avilaos.hardware.disk.DiskBlock;
import avilaos.hardware.disk.HeadDirection;
import avilaos.kernel.fs.FileSystem;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import javax.swing.JPanel;

/**
 * Mapa del disco: un cuadro numerado por bloque. Libre en blanco, cada archivo con
 * su color, el swap ocupado en gris azulado con el ID del proceso suspendido, y el
 * bloque donde está el cabezal con borde rojo y una flecha con su dirección.
 * Al pasar el mouse por un bloque se ve a quién pertenece.
 */
public class DiskMapPanel extends JPanel {

    private static final int COLUMNS = 20;
    private static final int CELL = 34;
    private static final int GAP = 2;
    private static final int MARGIN = 6;

    private final Disk disk;
    private final FileSystem fileSystem;

    public DiskMapPanel(Disk disk, FileSystem fileSystem) {
        this.disk = disk;
        this.fileSystem = fileSystem;
        setBackground(Color.WHITE);
        setToolTipText("");
        int rows = (disk.getTotalBlocks() + COLUMNS - 1) / COLUMNS;
        setPreferredSize(new Dimension(MARGIN * 2 + COLUMNS * (CELL + GAP),
                MARGIN * 2 + rows * (CELL + GAP)));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Font small = getFont().deriveFont(9f);
        g2.setFont(small);
        FontMetrics fm = g2.getFontMetrics();

        int total = disk.getTotalBlocks();
        int head = disk.getCurrentHead();
        for (int i = 0; i < total; i++) {
            int x = MARGIN + (i % COLUMNS) * (CELL + GAP);
            int y = MARGIN + (i / COLUMNS) * (CELL + GAP);
            DiskBlock block = disk.getBlock(i);
            Color fill = colorOf(block);
            g2.setColor(fill);
            g2.fillRect(x, y, CELL, CELL);
            g2.setColor(new Color(190, 190, 190));
            g2.drawRect(x, y, CELL, CELL);

            g2.setColor(FileColors.textOn(fill));
            String number = String.valueOf(i);
            g2.drawString(number, x + 2, y + fm.getAscent());
            String owner = ownerShort(block);
            if (owner != null) {
                g2.drawString(owner, x + (CELL - fm.stringWidth(owner)) / 2, y + CELL - 3);
            }

            if (i == head) {
                g2.setColor(FileColors.HEAD);
                g2.setStroke(new BasicStroke(3f));
                g2.drawRect(x + 1, y + 1, CELL - 2, CELL - 2);
                g2.setStroke(new BasicStroke(1f));
                String arrow = disk.getHeadDirection() == HeadDirection.UP ? "▶" : "◀";
                g2.setFont(getFont().deriveFont(Font.BOLD, 12f));
                g2.drawString(arrow, x + CELL - 13, y + 13);
                g2.setFont(small);
            }
        }
    }

    private Color colorOf(DiskBlock block) {
        if (block.isSwapBlock()) {
            return block.getOwner() != null ? FileColors.SWAP_USED : FileColors.FREE_SWAP;
        }
        if (block.getFile() != null) {
            return FileColors.forIndex(block.getFile().getColorIndex());
        }
        return FileColors.FREE;
    }

    /** Texto corto dentro del bloque: inicial del archivo o "P<id>" del proceso suspendido. */
    private String ownerShort(DiskBlock block) {
        if (block.isSwapBlock() && block.getOwner() != null) {
            return "P" + block.getOwner().getId();
        }
        if (block.getFile() != null) {
            String name = block.getFile().getName();
            return name.length() > 5 ? name.substring(0, 5) : name;
        }
        return null;
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        int col = (e.getX() - MARGIN) / (CELL + GAP);
        int row = (e.getY() - MARGIN) / (CELL + GAP);
        if (col < 0 || col >= COLUMNS || row < 0) {
            return null;
        }
        int index = row * COLUMNS + col;
        if (index >= disk.getTotalBlocks()) {
            return null;
        }
        DiskBlock block = disk.getBlock(index);
        String text = "Bloque " + index + (block.isSwapBlock() ? " (swap)" : "") + ": ";
        if (block.isSwapBlock() && block.getOwner() != null) {
            text += "proceso suspendido " + block.getOwner().getTag() + " " + block.getOwner().getName();
        } else if (block.getFile() != null) {
            text += "archivo " + fileSystem.pathOf(block.getFile())
                    + (block.getNext() >= 0 ? " → siguiente " + block.getNext() : " (último)");
        } else {
            text += "libre";
        }
        if (index == disk.getCurrentHead()) {
            text += " — cabezal aquí";
        }
        return text;
    }
}