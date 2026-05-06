package dev.coolrequest.tool.urlencoding;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.JBColor;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;

public class UrlEncodingPanel extends JBPanel<UrlEncodingPanel> {

    private static final List<String> CHARSETS = Arrays.asList(
            "UTF-8", "GBK", "GB2312", "ISO-8859-1", "US-ASCII", "UTF-16"
    );

    private final JBTextArea inputArea = new JBTextArea();
    private final JBTextArea outputArea = new JBTextArea();
    private final ComboBox<String> charsetCombo = new ComboBox<>(CHARSETS.toArray(new String[0]));
    private final JBLabel statusLabel = new JBLabel(" ");

    public UrlEncodingPanel() {
        super(new BorderLayout());
        setBorder(JBUI.Borders.empty(8));

        add(buildTopBar(), BorderLayout.NORTH);
        add(buildSplitter(), BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);
    }

    private JComponent buildTopBar() {
        JBPanel<?> bar = new JBPanel<>(new WrapLayout(FlowLayout.LEFT, 8, 4));
        bar.setBorder(JBUI.Borders.emptyBottom(6));

        bar.add(new JBLabel("Charset:"));
        charsetCombo.setSelectedItem("UTF-8");
        bar.add(charsetCombo);

        bar.add(spacer(12));

        JButton encodeBtn = primaryButton("Encode", AllIcons.Actions.Download);
        encodeBtn.addActionListener(e -> doEncode());
        bar.add(encodeBtn);

        JButton decodeBtn = primaryButton("Decode", AllIcons.Actions.Upload);
        decodeBtn.addActionListener(e -> doDecode());
        bar.add(decodeBtn);

        JButton swapBtn = new JButton("Swap", AllIcons.Actions.Refresh);
        swapBtn.setToolTipText("Swap input and output");
        swapBtn.addActionListener(e -> swap());
        bar.add(swapBtn);

        return bar;
    }

    private JComponent buildSplitter() {
        OnePixelSplitter splitter = new OnePixelSplitter(true, 0.5f);
        splitter.setFirstComponent(buildEditor("Input", inputArea, true));
        splitter.setSecondComponent(buildEditor("Output", outputArea, false));
        return splitter;
    }

    private JComponent buildEditor(String title, JBTextArea area, boolean isInput) {
        area.setLineWrap(true);
        area.setWrapStyleWord(false);
        area.setFont(JBUI.Fonts.create(Font.MONOSPACED, JBUI.Fonts.label().getSize()));
        area.setBorder(JBUI.Borders.empty(6));

        JBPanel<?> wrap = new JBPanel<>(new BorderLayout());
        wrap.setBorder(JBUI.Borders.empty(2));

        JBPanel<?> header = new JBPanel<>(new BorderLayout());
        header.setBorder(JBUI.Borders.empty(2, 4));

        JBLabel titleLabel = new JBLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
        header.add(titleLabel, BorderLayout.WEST);

        JBPanel<?> actions = new JBPanel<>(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        if (isInput) {
            JButton paste = iconButton(AllIcons.Actions.MenuPaste, "Paste from clipboard");
            paste.addActionListener(e -> pasteInto(area));
            actions.add(paste);
        } else {
            JButton copy = iconButton(AllIcons.Actions.Copy, "Copy to clipboard");
            copy.addActionListener(e -> copyFrom(area));
            actions.add(copy);
        }
        JButton clear = iconButton(AllIcons.Actions.GC, "Clear");
        clear.addActionListener(e -> {
            area.setText("");
            setStatus(title + " cleared", false);
        });
        actions.add(clear);
        header.add(actions, BorderLayout.EAST);

        wrap.add(header, BorderLayout.NORTH);
        JBScrollPane scroll = new JBScrollPane(area);
        scroll.setBorder(roundedBorder());
        wrap.add(scroll, BorderLayout.CENTER);

        return wrap;
    }

    private JComponent buildStatusBar() {
        JBPanel<?> bar = new JBPanel<>(new BorderLayout());
        bar.setBorder(JBUI.Borders.empty(4, 2, 0, 2));
        statusLabel.setForeground(JBColor.GRAY);
        bar.add(statusLabel, BorderLayout.WEST);
        return bar;
    }

    private void doEncode() {
        String text = inputArea.getText();
        if (text.isEmpty()) {
            setStatus("Input is empty", true);
            return;
        }
        try {
            Charset cs = Charset.forName((String) charsetCombo.getSelectedItem());
            String encoded = URLEncoder.encode(text, cs);
            outputArea.setText(encoded);
            outputArea.setCaretPosition(0);
            setStatus("Encoded " + text.length() + " chars → " + encoded.length() + " chars", false);
        } catch (Exception ex) {
            setStatus("Encode failed: " + ex.getMessage(), true);
        }
    }

    private void doDecode() {
        String text = inputArea.getText();
        if (text.isEmpty()) {
            setStatus("Input is empty", true);
            return;
        }
        try {
            Charset cs = Charset.forName((String) charsetCombo.getSelectedItem());
            String decoded = URLDecoder.decode(text, cs);
            outputArea.setText(decoded);
            outputArea.setCaretPosition(0);
            setStatus("Decoded " + text.length() + " chars → " + decoded.length() + " chars", false);
        } catch (IllegalArgumentException ex) {
            setStatus("Decode failed: malformed input (" + ex.getMessage() + ")", true);
        } catch (Exception ex) {
            setStatus("Decode failed: " + ex.getMessage(), true);
        }
    }

    private void swap() {
        String in = inputArea.getText();
        inputArea.setText(outputArea.getText());
        outputArea.setText(in);
        inputArea.setCaretPosition(0);
        outputArea.setCaretPosition(0);
        setStatus("Swapped input and output", false);
    }

    private void copyFrom(JBTextArea area) {
        String text = area.getText();
        if (text.isEmpty()) {
            setStatus("Nothing to copy", true);
            return;
        }
        CopyPasteManager.getInstance().setContents(new StringSelection(text));
        setStatus("Copied " + text.length() + " chars", false);
    }

    private void pasteInto(JBTextArea area) {
        Transferable contents = CopyPasteManager.getInstance().getContents();
        if (contents != null && contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
            try {
                String text = (String) contents.getTransferData(DataFlavor.stringFlavor);
                area.setText(text);
                area.setCaretPosition(0);
                setStatus("Pasted " + text.length() + " chars", false);
                return;
            } catch (Exception ignore) {
            }
        }
        setStatus("Clipboard is empty or not text", true);
    }

    private void setStatus(String msg, boolean error) {
        statusLabel.setText(msg);
        statusLabel.setForeground(error ? JBColor.RED : JBColor.GRAY);
    }

    private static JButton primaryButton(String text, Icon icon) {
        JButton b = new JButton(text, icon);
        b.setFocusPainted(false);
        return b;
    }

    private static JButton iconButton(Icon icon, String tooltip) {
        JButton b = new JButton(icon);
        b.setToolTipText(tooltip);
        b.setBorder(JBUI.Borders.empty(2));
        b.setContentAreaFilled(false);
        b.setFocusPainted(false);
        return b;
    }

    private static Component spacer(int width) {
        return Box.createHorizontalStrut(width);
    }

    private static Border roundedBorder() {
        return JBUI.Borders.customLine(JBColor.border(), 1);
    }
}
