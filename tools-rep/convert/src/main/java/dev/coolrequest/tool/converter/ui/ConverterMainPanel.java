package dev.coolrequest.tool.converter.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import dev.coolrequest.tool.converter.ConverterFormats;
import dev.coolrequest.tool.converter.model.Converter;
import dev.coolrequest.tool.converter.model.ConverterRegistry;
import dev.coolrequest.tool.converter.model.Format;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ConverterMainPanel extends JBPanel<ConverterMainPanel> {

    private final ConverterRegistry registry;
    private final FormatChooser sourceChooser;
    private final FormatChooser targetChooser;
    private final JBTextArea sourceArea;
    private final JBTextArea targetArea;
    private final JBLabel statusLabel;
    private final Timer convertTimer;
    private boolean updatingTarget;
    private boolean updatingSource;

    public ConverterMainPanel() {
        super(new BorderLayout());
        setBorder(JBUI.Borders.empty(8));

        this.registry = new ConverterRegistry();
        ConverterFormats.register(registry);

        List<Format> formats = new ArrayList<>(registry.getAllFormats());
        Collections.sort(formats, Comparator
                .comparing(Format::getCategory)
                .thenComparing(Format::getName));

        Format defaultSource = ConverterFormats.PLAIN_TEXT;
        Format defaultTarget = ConverterFormats.MD5;

        this.sourceArea = createTextArea("在此输入源文本…");
        this.targetArea = createTextArea("转换结果将在此显示");
        this.statusLabel = new JBLabel(" ");
        this.statusLabel.setForeground(UIUtil.getInactiveTextColor());
        this.statusLabel.setBorder(JBUI.Borders.empty(4, 8));

        this.sourceChooser = new FormatChooser("源格式", formats, defaultSource, f -> triggerConvert());
        this.targetChooser = new FormatChooser("目标格式", formats, defaultTarget, f -> triggerConvert());

        this.convertTimer = new Timer(150, e -> doConvert());
        this.convertTimer.setRepeats(false);

        add(buildTopBar(), BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);

        sourceArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { triggerConvert(); }
            @Override public void removeUpdate(DocumentEvent e) { triggerConvert(); }
            @Override public void changedUpdate(DocumentEvent e) { triggerConvert(); }
        });

        targetArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { triggerReverseConvert(); }
            @Override public void removeUpdate(DocumentEvent e) { triggerReverseConvert(); }
            @Override public void changedUpdate(DocumentEvent e) { triggerReverseConvert(); }
        });

        SwingUtilities.invokeLater(this::doConvert);
    }

    private JComponent buildTopBar() {
        JPanel top = new JPanel(new GridLayout(1, 3, 8, 0));
        top.setBorder(JBUI.Borders.emptyBottom(8));
        top.add(sourceChooser);

        JPanel center = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
        JButton swap = new JButton("⇄ 交换");
        swap.setFocusPainted(false);
        swap.setToolTipText("交换源/目标，并互换文本");
        swap.addActionListener(e -> swapFormats());
        center.add(swap);

        JButton copy = new JButton("复制结果");
        copy.setFocusPainted(false);
        copy.addActionListener(e -> copyTarget());
        center.add(copy);

        JButton clear = new JButton("清空");
        clear.setFocusPainted(false);
        clear.addActionListener(e -> {
            sourceArea.setText("");
            targetArea.setText("");
        });
        center.add(clear);

        top.add(center);
        top.add(targetChooser);
        return top;
    }

    private JComponent buildBody() {
        JBScrollPane top = new JBScrollPane(sourceArea);
        top.setBorder(IdeBorderFactory.createTitledBorder("源文本", false));
        JBScrollPane bottom = new JBScrollPane(targetArea);
        bottom.setBorder(IdeBorderFactory.createTitledBorder("目标结果", false));
        OnePixelSplitter splitter = new OnePixelSplitter(true, "CoolRequest.Converter.Splitter", 0.5f);
        splitter.setFirstComponent(top);
        splitter.setSecondComponent(bottom);
        return splitter;
    }

    private JBTextArea createTextArea(String emptyText) {
        JBTextArea area = new JBTextArea();
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        area.setLineWrap(true);
        area.setWrapStyleWord(false);
        area.setTabSize(2);
        area.setMargin(JBUI.insets(6));
        area.getEmptyText().setText(emptyText);
        return area;
    }

    private void triggerConvert() {
        if (updatingSource) return;
        convertTimer.restart();
    }

    private void triggerReverseConvert() {
        if (updatingTarget) return;
        Format from = sourceChooser.getSelected();
        Format to = targetChooser.getSelected();
        if (from == null || to == null) return;
        Converter back = registry.findConverter(to, from);
        if (back == null) return;
        String text = targetArea.getText();
        if (text.isEmpty()) return;
        try {
            String result = back.convert(text);
            updatingSource = true;
            try {
                if (!result.equals(sourceArea.getText())) {
                    sourceArea.setText(result);
                }
                statusLabel.setText("已从目标反向同步到源");
                statusLabel.setForeground(UIUtil.getInactiveTextColor());
            } finally {
                updatingSource = false;
            }
        } catch (Exception ignored) {
        }
    }

    private void doConvert() {
        Format from = sourceChooser.getSelected();
        Format to = targetChooser.getSelected();
        if (from == null || to == null) return;
        String input = sourceArea.getText();
        if (input.isEmpty()) {
            updatingTarget = true;
            try {
                targetArea.setText("");
            } finally {
                updatingTarget = false;
            }
            statusLabel.setText(" ");
            return;
        }
        Converter converter = registry.findConverter(from, to);
        if (converter == null) {
            updatingTarget = true;
            try {
                targetArea.setText("格式不支持");
            } finally {
                updatingTarget = false;
            }
            statusLabel.setText("不支持 " + from.getName() + " → " + to.getName());
            statusLabel.setForeground(JBColor.namedColor("Label.errorForeground", JBColor.RED));
            return;
        }
        try {
            String result = converter.convert(input);
            updatingTarget = true;
            try {
                targetArea.setText(result);
                targetArea.setCaretPosition(0);
            } finally {
                updatingTarget = false;
            }
            statusLabel.setText(from.getName() + " → " + to.getName() + " · " + result.length() + " 字符");
            statusLabel.setForeground(UIUtil.getInactiveTextColor());
        } catch (Exception ex) {
            updatingTarget = true;
            try {
                targetArea.setText("格式不支持");
            } finally {
                updatingTarget = false;
            }
            String msg = ex.getMessage();
            statusLabel.setText("转换失败：" + (msg == null ? ex.getClass().getSimpleName() : msg));
            statusLabel.setForeground(JBColor.namedColor("Label.errorForeground", JBColor.RED));
        }
    }

    private void swapFormats() {
        Format from = sourceChooser.getSelected();
        Format to = targetChooser.getSelected();
        String src = sourceArea.getText();
        String tgt = targetArea.getText();
        sourceChooser.setSelected(to);
        targetChooser.setSelected(from);
        updatingSource = true;
        try {
            sourceArea.setText(tgt);
        } finally {
            updatingSource = false;
        }
        updatingTarget = true;
        try {
            targetArea.setText(src);
        } finally {
            updatingTarget = false;
        }
        triggerConvert();
    }

    private void copyTarget() {
        String text = targetArea.getText();
        if (text.isEmpty()) return;
        ApplicationManager.getApplication().invokeLater(() ->
                CopyPasteManager.getInstance().setContents(new StringSelection(text)));
        statusLabel.setText("已复制到剪贴板");
        statusLabel.setForeground(UIUtil.getInactiveTextColor());
    }
}
