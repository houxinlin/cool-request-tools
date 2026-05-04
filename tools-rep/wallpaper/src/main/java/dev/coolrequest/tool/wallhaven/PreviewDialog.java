package dev.coolrequest.tool.wallhaven;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;

public class PreviewDialog extends DialogWrapper {

    private static final Logger LOG = Logger.getInstance(PreviewDialog.class);

    private final Project project;
    private final Wallpaper wallpaper;
    private final WallhavenClient client;

    private final PreviewCanvas canvas = new PreviewCanvas();
    private byte[] fullBytes;

    private final ComboBox<WallpaperApplier.Target> targetCombo =
            new ComboBox<>(WallpaperApplier.Target.values());
    private final ComboBox<WallpaperApplier.Fill> fillCombo =
            new ComboBox<>(WallpaperApplier.Fill.values());
    private final ComboBox<WallpaperApplier.Anchor> anchorCombo =
            new ComboBox<>(WallpaperApplier.Anchor.values());
    private final JSlider opacitySlider = new JSlider(5, 100, 15);
    private final JBLabel opacityValueLabel = new JBLabel("15%");

    public PreviewDialog(Project project, Wallpaper wallpaper, WallhavenClient client) {
        super(project, true);
        this.project = project;
        this.wallpaper = wallpaper;
        this.client = client;
        setTitle("Wallhaven 预览 - " + wallpaper.id + " (" + wallpaper.resolution + ")");
        setOKButtonText("设为 IDEA 壁纸");
        setCancelButtonText("关闭");
        init();
        loadFullImage();
    }

    @Override
    protected JComponent createCenterPanel() {
        JPanel main = new JPanel(new BorderLayout(8, 8));
        main.setPreferredSize(new Dimension(900, 640));
        main.setBorder(JBUI.Borders.empty(8));

        canvas.setOpacityPercent(opacitySlider.getValue());
        JBScrollPane sp = new JBScrollPane(canvas);
        main.add(sp, BorderLayout.CENTER);

        JPanel south = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = JBUI.insets(4);
        c.anchor = GridBagConstraints.WEST;
        c.gridy = 0;
        c.gridx = 0;
        south.add(new JBLabel("应用到:"), c);
        c.gridx = 1; south.add(targetCombo, c);
        c.gridx = 2; south.add(new JBLabel("填充:"), c);
        c.gridx = 3; south.add(fillCombo, c);
        c.gridx = 4; south.add(new JBLabel("位置:"), c);
        c.gridx = 5; south.add(anchorCombo, c);

        c.gridy = 1; c.gridx = 0;
        south.add(new JBLabel("不透明度:"), c);
        c.gridx = 1; c.gridwidth = 4; c.fill = GridBagConstraints.HORIZONTAL;
        opacitySlider.setMajorTickSpacing(20);
        opacitySlider.setPaintTicks(true);
        opacitySlider.setPaintLabels(true);
        opacitySlider.addChangeListener(e -> {
            int v = opacitySlider.getValue();
            opacityValueLabel.setText(v + "%");
            canvas.setOpacityPercent(v);
        });
        south.add(opacitySlider, c);
        c.gridx = 5; c.gridwidth = 1; c.fill = GridBagConstraints.NONE;
        south.add(opacityValueLabel, c);

        targetCombo.setSelectedItem(WallpaperApplier.Target.BOTH);
        fillCombo.setSelectedItem(WallpaperApplier.Fill.SCALE);
        anchorCombo.setSelectedItem(WallpaperApplier.Anchor.CENTER);

        main.add(south, BorderLayout.SOUTH);
        return main;
    }

    private void loadFullImage() {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "下载壁纸大图", true) {
            @Override
            public void run(ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                try {
                    byte[] bytes = client.download(wallpaper.path);
                    BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
                    if (img == null) throw new RuntimeException("无法解析图片");
                    BufferedImage scaled = scaleToFit(img, 880, 540);
                    SwingUtilities.invokeLater(() -> {
                        fullBytes = bytes;
                        canvas.setImage(scaled);
                    });
                } catch (Exception ex) {
                    LOG.warn("download failed", ex);
                    SwingUtilities.invokeLater(() -> canvas.setError("下载失败: " + ex.getMessage()));
                }
            }
        });
    }

    private static BufferedImage scaleToFit(BufferedImage src, int maxW, int maxH) {
        int w = src.getWidth(), h = src.getHeight();
        double r = Math.min((double) maxW / w, (double) maxH / h);
        if (r >= 1.0) return src;
        int nw = Math.max(1, (int) (w * r));
        int nh = Math.max(1, (int) (h * r));
        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, nw, nh, null);
        g.dispose();
        return out;
    }

    @Override
    protected void doOKAction() {
        if (fullBytes == null) {
            Messages.showWarningDialog(project, "图片尚未下载完成", "请稍候");
            return;
        }
        WallpaperApplier.Target target = (WallpaperApplier.Target) targetCombo.getSelectedItem();
        WallpaperApplier.Fill fill = (WallpaperApplier.Fill) fillCombo.getSelectedItem();
        WallpaperApplier.Anchor anchor = (WallpaperApplier.Anchor) anchorCombo.getSelectedItem();
        int opacity = opacitySlider.getValue();
        try {
            Path file = WallpaperApplier.saveImage(wallpaper, fullBytes);
            WallpaperApplier.apply(file, target, opacity, fill, anchor);
            close(OK_EXIT_CODE);
        } catch (Exception ex) {
            LOG.warn("apply wallpaper failed", ex);
            Messages.showErrorDialog(project, "设置失败: " + ex.getMessage(), "Wallhaven");
        }
    }

    private static class PreviewCanvas extends JPanel {
        private BufferedImage image;
        private String message = "加载大图中...";
        private int opacityPercent = 15;

        PreviewCanvas() {
            setPreferredSize(new Dimension(880, 540));
            setOpaque(true);
        }

        void setImage(BufferedImage img) {
            this.image = img;
            this.message = null;
            if (img != null) setPreferredSize(new Dimension(img.getWidth(), img.getHeight()));
            revalidate();
            repaint();
        }

        void setError(String text) {
            this.image = null;
            this.message = text;
            repaint();
        }

        void setOpacityPercent(int percent) {
            this.opacityPercent = Math.max(0, Math.min(100, percent));
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                Color bg = UIUtil.getPanelBackground();
                g2.setColor(bg);
                g2.fillRect(0, 0, getWidth(), getHeight());

                if (image != null) {
                    int iw = image.getWidth();
                    int ih = image.getHeight();
                    int x = Math.max(0, (getWidth() - iw) / 2);
                    int y = Math.max(0, (getHeight() - ih) / 2);
                    float alpha = opacityPercent / 100f;
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                    g2.drawImage(image, x, y, null);
                } else if (message != null) {
                    g2.setColor(JBColor.GRAY);
                    FontMetrics fm = g2.getFontMetrics();
                    int tw = fm.stringWidth(message);
                    g2.drawString(message, (getWidth() - tw) / 2,
                            getHeight() / 2 + fm.getAscent() / 2);
                }
            } finally {
                g2.dispose();
            }
        }
    }
}
