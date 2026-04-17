package dev.coolrequest.tool.hash;

import com.intellij.icons.AllIcons;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.fields.ExtendableTextField;
import com.intellij.ui.components.fields.ExtendableTextComponent;
import com.intellij.util.ui.JBUI;
import dev.coolrequest.tool.CoolToolPanel;
import dev.coolrequest.tool.ToolPanelFactory;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;

public class HashToolFactory implements ToolPanelFactory {

    @Override
    public CoolToolPanel createToolPanel() {
        return new CoolToolPanel() {
            @Override
            public JPanel createPanel() {
                return new HashMainPanel();
            }

            @Override
            public void showTool() {
            }

            @Override
            public void closeTool() {
            }
        };
    }

    public static class HashMainPanel extends JPanel {

        private final JBTextArea inputArea;
        private final Map<String, ExtendableTextField> hashFields = new LinkedHashMap<>();

        private static final String[] ALGORITHMS = {
                "MD5", "SHA-1", "SHA-224", "SHA-256", "SHA-384", "SHA-512",
                "SHA3-256", "RIPEMD160"
        };

        private static final String[] DISPLAY_NAMES = {
                "MD5", "SHA1", "SHA224", "SHA256", "SHA384", "SHA512",
                "SHA3-256", "RIPEMD160"
        };

        public HashMainPanel() {
            setLayout(new BorderLayout(0, 0));

            // ── Input panel ──────────────────────────────────────────────
            inputArea = new JBTextArea(5, 40);
            inputArea.setLineWrap(true);
            inputArea.setWrapStyleWord(true);
            inputArea.getEmptyText().setText("Enter text to hash…");

            JBScrollPane inputScroll = new JBScrollPane(inputArea);
            TitledBorder inputBorder = BorderFactory.createTitledBorder(
                    BorderFactory.createLineBorder(JBColor.border()), "Input Text");
            inputScroll.setBorder(JBUI.Borders.merge(inputBorder,
                    JBUI.Borders.empty(4, 6, 4, 6), true));

            add(inputScroll, BorderLayout.NORTH);

            // ── Results panel ─────────────────────────────────────────────
            JPanel resultsPanel = new JPanel(new GridBagLayout());
            resultsPanel.setBorder(JBUI.Borders.empty(8, 6, 6, 6));

            GridBagConstraints labelGbc = new GridBagConstraints();
            labelGbc.fill = GridBagConstraints.NONE;
            labelGbc.anchor = GridBagConstraints.LINE_END;
            labelGbc.insets = JBUI.insets(4, 0, 4, 8);

            GridBagConstraints fieldGbc = new GridBagConstraints();
            fieldGbc.fill = GridBagConstraints.HORIZONTAL;
            fieldGbc.weightx = 1.0;
            fieldGbc.insets = JBUI.insets(4, 0, 4, 0);

            for (int i = 0; i < ALGORITHMS.length; i++) {
                String algo = ALGORITHMS[i];
                String displayName = DISPLAY_NAMES[i];

                JBLabel label = new JBLabel(displayName + ":");
                label.setFont(label.getFont().deriveFont(Font.BOLD));

                ExtendableTextField field = new ExtendableTextField();
                field.setEditable(false);
                field.setFont(new Font(Font.MONOSPACED, Font.PLAIN, field.getFont().getSize()));
                field.getEmptyText().setText("—");

                // 内嵌复制图标，放在文本框右侧内部
                ExtendableTextComponent.Extension copyExt = ExtendableTextComponent.Extension.create(
                        AllIcons.Actions.Copy,          // 普通状态图标
                        AllIcons.Actions.Copy,          // 悬停状态图标
                        "Copy " + displayName,          // tooltip
                        () -> {
                            String text = field.getText();
                            if (text != null && !text.isEmpty()) {
                                Toolkit.getDefaultToolkit()
                                        .getSystemClipboard()
                                        .setContents(new StringSelection(text), null);
                            }
                        }
                );
                field.addExtension(copyExt);

                hashFields.put(algo, field);

                labelGbc.gridx = 0;
                labelGbc.gridy = i;
                fieldGbc.gridx = 1;
                fieldGbc.gridy = i;

                resultsPanel.add(label, labelGbc);
                resultsPanel.add(field, fieldGbc);
            }

            JBScrollPane resultsScroll = new JBScrollPane(resultsPanel);
            TitledBorder resultsBorder = BorderFactory.createTitledBorder(
                    BorderFactory.createLineBorder(JBColor.border()), "Hash Results");
            resultsScroll.setBorder(JBUI.Borders.merge(resultsBorder,
                    JBUI.Borders.empty(4, 6, 4, 6), true));
            resultsScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

            add(resultsScroll, BorderLayout.CENTER);

            // ── Live update ───────────────────────────────────────────────
            inputArea.getDocument().addDocumentListener(new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent e)  { updateHashes(); }
                @Override public void removeUpdate(DocumentEvent e)  { updateHashes(); }
                @Override public void changedUpdate(DocumentEvent e) { updateHashes(); }
            });
        }

        private void updateHashes() {
            String input = inputArea.getText();
            if (input == null || input.isEmpty()) {
                hashFields.values().forEach(f -> f.setText(""));
                return;
            }
            byte[] bytes = input.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            for (Map.Entry<String, ExtendableTextField> entry : hashFields.entrySet()) {
                entry.getValue().setText(computeHash(entry.getKey(), bytes));
            }
        }

        private String computeHash(String algorithm, byte[] input) {
            if ("RIPEMD160".equals(algorithm)) {
                return computeRipemd160(input);
            }
            try {
                MessageDigest md = MessageDigest.getInstance(algorithm);
                return bytesToHex(md.digest(input));
            } catch (NoSuchAlgorithmException e) {
                return "Unsupported";
            }
        }

        private String computeRipemd160(byte[] input) {
            try {
                MessageDigest md = MessageDigest.getInstance("RIPEMD160");
                return bytesToHex(md.digest(input));
            } catch (NoSuchAlgorithmException e1) {
                try {
                    MessageDigest md = MessageDigest.getInstance("RIPEMD160",
                            new org.bouncycastle.jce.provider.BouncyCastleProvider());
                    return bytesToHex(md.digest(input));
                } catch (Exception e2) {
                    return "Unsupported";
                }
            }
        }

        private static String bytesToHex(byte[] bytes) {
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        }
    }
}
