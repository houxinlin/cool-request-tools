package dev.coolrequest.tool.qrcode;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileChooser.FileSaverDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBUI;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

public class QrCodePanel extends JPanel {

    private static final int[] SIZE_VALUES   = {128, 200, 256, 300, 400, 500, 800, 1000};
    private static final String[] SIZE_LABELS = {"128 × 128", "200 × 200", "256 × 256",
            "300 × 300", "400 × 400", "500 × 500", "800 × 800", "1000 × 1000", "自定义"};
    private static final int DEFAULT_SIZE_INDEX = 5;   // 500
    private static final int CUSTOM_INDEX       = SIZE_LABELS.length - 1;

    private final Project       project;
    private final JBTextArea    textArea;
    private final QrImagePanel  imagePanel;
    private final JComboBox<String> sizeCombo;
    private final JSpinner      widthSpinner;
    private final JSpinner      heightSpinner;

    public QrCodePanel(Project project) {
        this.project = project;
        setLayout(new BorderLayout());

        // ── Top: text input ──────────────────────────────────────────────
        JPanel inputWrapper = new JPanel(new BorderLayout(0, 6));
        inputWrapper.setBorder(JBUI.Borders.empty(10, 10, 6, 10));

        JBLabel inputLabel = new JBLabel("请输入文本内容：");

        textArea = new JBTextArea();
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);

        JBScrollPane scrollPane = new JBScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(0, 120));

        inputWrapper.add(inputLabel, BorderLayout.NORTH);
        inputWrapper.add(scrollPane, BorderLayout.CENTER);

        // ── Middle: controls ─────────────────────────────────────────────
        JPanel controlBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        controlBar.setBorder(JBUI.Borders.empty(0, 10, 0, 10));

        sizeCombo = new JComboBox<>(SIZE_LABELS);
        sizeCombo.setSelectedIndex(DEFAULT_SIZE_INDEX);
        sizeCombo.setMaximumRowCount(SIZE_LABELS.length);

        // 自定义宽高 Spinner（仅"自定义"选项时可用）
        widthSpinner  = new JSpinner(new SpinnerNumberModel(500, 1, 4096, 1));
        heightSpinner = new JSpinner(new SpinnerNumberModel(500, 1, 4096, 1));
        Dimension spinnerSize = new Dimension(80, widthSpinner.getPreferredSize().height);
        widthSpinner.setPreferredSize(spinnerSize);
        heightSpinner.setPreferredSize(spinnerSize);
        widthSpinner.setEnabled(false);
        heightSpinner.setEnabled(false);

        JButton saveButton = new JButton("保存图片");
        saveButton.setFocusPainted(false);

        controlBar.add(new JBLabel("尺寸："));
        controlBar.add(sizeCombo);
        controlBar.add(new JBLabel("宽："));
        controlBar.add(widthSpinner);
        controlBar.add(new JBLabel("高："));
        controlBar.add(heightSpinner);
        controlBar.add(Box.createHorizontalStrut(12));
        controlBar.add(saveButton);

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(inputWrapper, BorderLayout.NORTH);
        topPanel.add(controlBar, BorderLayout.SOUTH);

        // ── Bottom: QR display ───────────────────────────────────────────
        imagePanel = new QrImagePanel();

        add(topPanel, BorderLayout.NORTH);
        add(imagePanel, BorderLayout.CENTER);

        // ── Listeners ────────────────────────────────────────────────────
        textArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { generateQrCode(); }
            @Override public void removeUpdate(DocumentEvent e)  { generateQrCode(); }
            @Override public void changedUpdate(DocumentEvent e) { generateQrCode(); }
        });

        sizeCombo.addActionListener(e -> {
            boolean custom = sizeCombo.getSelectedIndex() == CUSTOM_INDEX;
            widthSpinner.setEnabled(custom);
            heightSpinner.setEnabled(custom);
            if (!custom) {
                int val = SIZE_VALUES[sizeCombo.getSelectedIndex()];
                widthSpinner.setValue(val);
                heightSpinner.setValue(val);
            }
            generateQrCode();
        });

        // ChangeListener 负责箭头按钮触发的变化
        ChangeListener regenOnSpin = e -> {
            if (sizeCombo.getSelectedIndex() == CUSTOM_INDEX) generateQrCode();
        };
        widthSpinner.addChangeListener(regenOnSpin);
        heightSpinner.addChangeListener(regenOnSpin);

        // DocumentListener 负责直接在文本框中输入时实时触发
        JFormattedTextField widthField  = ((JSpinner.DefaultEditor) widthSpinner.getEditor()).getTextField();
        JFormattedTextField heightField = ((JSpinner.DefaultEditor) heightSpinner.getEditor()).getTextField();
        DocumentListener spinnerTextListener = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { onSpinnerTextChange(); }
            @Override public void removeUpdate(DocumentEvent e)  { onSpinnerTextChange(); }
            @Override public void changedUpdate(DocumentEvent e) {}
            private void onSpinnerTextChange() {
                SwingUtilities.invokeLater(() -> {
                    if (sizeCombo.getSelectedIndex() == CUSTOM_INDEX) generateQrCode();
                });
            }
        };
        widthField.getDocument().addDocumentListener(spinnerTextListener);
        heightField.getDocument().addDocumentListener(spinnerTextListener);

        saveButton.addActionListener(e -> saveQrCode());
    }

    // ── Size helpers ─────────────────────────────────────────────────────

    /** 优先从文本框直接读取（未 commit 时也能取到正在输入的值） */
    private int readSpinnerValue(JSpinner spinner, int defaultVal) {
        try {
            String text = ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().getText().trim();
            int val = Integer.parseInt(text);
            return Math.max(1, val);
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    private int getSelectedWidth() {
        if (sizeCombo.getSelectedIndex() == CUSTOM_INDEX) return readSpinnerValue(widthSpinner, SIZE_VALUES[DEFAULT_SIZE_INDEX]);
        int idx = sizeCombo.getSelectedIndex();
        return (idx >= 0 && idx < SIZE_VALUES.length) ? SIZE_VALUES[idx] : SIZE_VALUES[DEFAULT_SIZE_INDEX];
    }

    private int getSelectedHeight() {
        if (sizeCombo.getSelectedIndex() == CUSTOM_INDEX) return readSpinnerValue(heightSpinner, SIZE_VALUES[DEFAULT_SIZE_INDEX]);
        int idx = sizeCombo.getSelectedIndex();
        return (idx >= 0 && idx < SIZE_VALUES.length) ? SIZE_VALUES[idx] : SIZE_VALUES[DEFAULT_SIZE_INDEX];
    }

    // ── QR generation ────────────────────────────────────────────────────

    private void generateQrCode() {
        String text = textArea.getText();
        if (text == null || text.isEmpty()) {
            imagePanel.update(null, null);
            return;
        }
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 1);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            BitMatrix matrix = new QRCodeWriter()
                    .encode(text, BarcodeFormat.QR_CODE, getSelectedWidth(), getSelectedHeight(), hints);
            imagePanel.update(toImage(matrix), null);
        } catch (WriterException e) {
            imagePanel.update(null, "生成失败：" + e.getMessage());
        }
    }

    // ── Save via IDEA FileSaverDialog ─────────────────────────────────────

    private void saveQrCode() {
        BufferedImage img = imagePanel.getImage();
        if (img == null) {
            Messages.showWarningDialog(project, "请先输入文本生成二维码", "提示");
            return;
        }

        FileSaverDescriptor descriptor = new FileSaverDescriptor("保存二维码", "选择保存位置", "png");
        FileSaverDialog dialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project);
        VirtualFileWrapper wrapper = dialog.save((com.intellij.openapi.vfs.VirtualFile) null, "qrcode");
        if (wrapper == null) return;

        File file = wrapper.getFile();
        if (!file.getName().toLowerCase().endsWith(".png")) {
            file = new File(file.getAbsolutePath() + ".png");
        }
        try {
            ImageIO.write(img, "PNG", file);
            Messages.showInfoMessage(project, "已保存到：" + file.getAbsolutePath(), "保存成功");
        } catch (IOException ex) {
            Messages.showErrorDialog(project, "保存失败：" + ex.getMessage(), "错误");
        }
    }

    // ── Utility ──────────────────────────────────────────────────────────

    private static BufferedImage toImage(BitMatrix matrix) {
        int w = matrix.getWidth(), h = matrix.getHeight();
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                img.setRGB(x, y, matrix.get(x, y) ? Color.BLACK.getRGB() : Color.WHITE.getRGB());
            }
        }
        return img;
    }

    // ── QrImagePanel ─────────────────────────────────────────────────────

    static class QrImagePanel extends JPanel {

        private BufferedImage image;
        private String        error;

        QrImagePanel() {
            setBackground(JBColor.background());
        }

        void update(BufferedImage image, String error) {
            this.image = image;
            this.error = error;
            repaint();
        }

        BufferedImage getImage() { return image; }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,  RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING,     RenderingHints.VALUE_RENDER_QUALITY);

            int w = getWidth(), h = getHeight();

            if (error != null) {
                g2.setColor(JBColor.RED);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(error, (w - fm.stringWidth(error)) / 2, h / 2);
                return;
            }

            if (image == null) {
                String hint = "在上方输入文本，二维码将自动生成";
                g2.setColor(JBColor.GRAY);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(hint, (w - fm.stringWidth(hint)) / 2, h / 2);
                return;
            }

            int pad   = 20;
            int availW = w - pad * 2;
            int availH = h - pad * 2;
            if (availW < 10 || availH < 10) return;

            // 按图片真实宽高比缩放，保持比例填满可用区域
            int imgW = image.getWidth();
            int imgH = image.getHeight();
            int drawW, drawH;
            if ((double) imgW / imgH >= (double) availW / availH) {
                drawW = availW;
                drawH = (int) ((double) availW * imgH / imgW);
            } else {
                drawH = availH;
                drawW = (int) ((double) availH * imgW / imgH);
            }

            int x = (w - drawW) / 2;
            int y = (h - drawH) / 2;

            g2.setColor(Color.WHITE);
            g2.fillRect(x - 8, y - 8, drawW + 16, drawH + 16);
            g2.setColor(new JBColor(new Color(210, 210, 210), new Color(80, 80, 80)));
            g2.drawRect(x - 8, y - 8, drawW + 16, drawH + 16);

            g2.drawImage(image, x, y, drawW, drawH, this);
        }
    }
}
