package dev.coolrequest.tool.wallhaven;

import java.awt.*;

/** Wraps children into rows, computing column count from current width. */
public class AdaptiveGridLayout implements LayoutManager {

    private final int cellWidth;
    private final int cellHeight;
    private final int hgap;
    private final int vgap;

    public AdaptiveGridLayout(int cellWidth, int cellHeight, int hgap, int vgap) {
        this.cellWidth = cellWidth;
        this.cellHeight = cellHeight;
        this.hgap = hgap;
        this.vgap = vgap;
    }

    private int cols(Container parent) {
        Insets in = parent.getInsets();
        int w = parent.getWidth() - in.left - in.right;
        if (w <= 0 && parent.getParent() != null) {
            w = parent.getParent().getWidth() - in.left - in.right;
        }
        if (w <= 0) return 1;
        return Math.max(1, (w + hgap) / (cellWidth + hgap));
    }

    @Override
    public void layoutContainer(Container parent) {
        int n = parent.getComponentCount();
        if (n == 0) return;
        int cols = cols(parent);
        Insets in = parent.getInsets();
        int x0 = in.left;
        int y0 = in.top;
        for (int i = 0; i < n; i++) {
            int row = i / cols;
            int col = i % cols;
            int x = x0 + col * (cellWidth + hgap);
            int y = y0 + row * (cellHeight + vgap);
            parent.getComponent(i).setBounds(x, y, cellWidth, cellHeight);
        }
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
        int n = parent.getComponentCount();
        Insets in = parent.getInsets();
        int cols = Math.max(1, cols(parent));
        int rows = (n + cols - 1) / cols;
        int w = cols * cellWidth + Math.max(0, cols - 1) * hgap + in.left + in.right;
        int h = Math.max(1, rows) * cellHeight + Math.max(0, rows - 1) * vgap + in.top + in.bottom;
        return new Dimension(w, h);
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        Insets in = parent.getInsets();
        return new Dimension(cellWidth + in.left + in.right, cellHeight + in.top + in.bottom);
    }

    @Override public void addLayoutComponent(String name, Component comp) {}
    @Override public void removeLayoutComponent(Component comp) {}
}
