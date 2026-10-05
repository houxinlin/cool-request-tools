package dev.coolrequest.tool.rabbitmq;

import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.Toolkit;

/**
 * 消息详情弹窗：完整元数据 + 可切换的 Body 解码。
 */
public class MessageDetailDialog extends DialogWrapper {

    private final Project project;
    private final String time;
    private final String deliveryTag;
    private final String routingKey;
    private final String size;
    private final String queue;
    private final byte[] body;

    public MessageDetailDialog(Project project, String time, String deliveryTag, String routingKey, String size, byte[] body, String queue) {
        super(project, true);
        this.project = project;
        this.time = time;
        this.deliveryTag = deliveryTag;
        this.routingKey = routingKey;
        this.size = size;
        this.queue = queue;
        this.body = body;
        setTitle("Message Detail");
        init();
    }

    @Override
    protected JComponent createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(JBUI.Borders.empty(8));
        panel.setPreferredSize(new Dimension(620, 480));

        JPanel infoPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = JBUI.insets(2, 4);
        gbc.anchor = GridBagConstraints.WEST;

        int row = 0;
        addInfoRow(infoPanel, gbc, row++, "Time:", time);
        addInfoRow(infoPanel, gbc, row++, "Queue:", queue);
        addInfoRow(infoPanel, gbc, row++, "DeliveryTag:", deliveryTag);
        addInfoRow(infoPanel, gbc, row++, "RoutingKey:", routingKey);
        addInfoRow(infoPanel, gbc, row++, "Size:", size);
        panel.add(infoPanel, BorderLayout.NORTH);

        // Decode mode selector + copy
        JPanel bodyHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JBLabel modeLabel = new JBLabel("Decode:");
        bodyHeader.add(modeLabel);
        JComboBox<String> modeCombo = new JComboBox<>(new String[]{"Text", "JSON", "Hex"});
        bodyHeader.add(modeCombo);

        // 无参构造（纯 Swing document）：详情弹窗可能由消费回调链触发，带 project 的重载
        // 构造时经 PsiDocumentManager 同步建 document，EDT 无读权限时硬抛 RuntimeExceptionWithAttachments
        final EditorTextField bodyEditor = new EditorTextField(BodyCodec.decode(body, BodyCodec.MODE_JSON)) {
            @Override
            protected EditorEx createEditor() {
                EditorEx editor = super.createEditor();
                EditorSettings settings = editor.getSettings();
                settings.setLineNumbersShown(true);
                settings.setFoldingOutlineShown(false);
                settings.setLineMarkerAreaShown(false);
                settings.setIndentGuidesShown(false);
                settings.setVirtualSpace(false);
                settings.setUseSoftWraps(true);
                settings.setGutterIconsShown(false);
                editor.setHorizontalScrollbarVisible(true);
                editor.setVerticalScrollbarVisible(true);
                return editor;
            }
        };
        bodyEditor.setNewDocumentAndFileType(com.intellij.json.JsonFileType.INSTANCE, bodyEditor.getDocument());
        bodyEditor.setOneLineMode(false);
        bodyEditor.setEnabled(false);
        bodyEditor.setFont(bodyEditor.getFont().deriveFont(14f));

        modeCombo.addActionListener(e -> {
            String m = (String) modeCombo.getSelectedItem();
            int mode = "Hex".equals(m) ? BodyCodec.MODE_HEX : BodyCodec.MODE_JSON;
            // JSON 模式挂平台 JSON 高亮（主插件同款 JsonFileType），Text/Hex 回纯文本
            if (mode == BodyCodec.MODE_JSON) {
                bodyEditor.setNewDocumentAndFileType(com.intellij.json.JsonFileType.INSTANCE,
                        bodyEditor.getDocument());
            } else {
                bodyEditor.setNewDocumentAndFileType(PlainTextFileType.INSTANCE,
                        bodyEditor.getDocument());
            }
            bodyEditor.setText(BodyCodec.decode(body, mode));
        });

        JPanel bodyWrapper = new JPanel(new BorderLayout(0, 4));
        bodyWrapper.add(bodyHeader, BorderLayout.NORTH);
        bodyWrapper.add(bodyEditor, BorderLayout.CENTER);

        JButton formatButton = new JButton("Format");
        formatButton.setToolTipText("校验并格式化 JSON（非法时报错位置，不改原文）");
        formatButton.addActionListener(e -> {
            try {
                bodyEditor.setText(JsonUtil.prettyPrint(bodyEditor.getText()));
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(getContentPane(), "JSON 不合法：\n" + ex.getMessage(),
                        "Format", JOptionPane.ERROR_MESSAGE);
            }
        });
        bodyHeader.add(formatButton);
        bodyHeader.add(Box.createHorizontalStrut(8));

        JButton copyPayload = new JButton("Copy payload");
        copyPayload.addActionListener(e -> copyToClipboard(bodyEditor.getText()));
        bodyHeader.add(copyPayload);
        bodyHeader.add(Box.createHorizontalStrut(8));

        panel.add(bodyWrapper, BorderLayout.CENTER);

        return panel;
    }

    private void copyToClipboard(String text) {
        if (text == null) {
            text = "";
        }
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(text), null);
    }

    private void addInfoRow(JPanel panel, GridBagConstraints gbc, int row, String label, String value) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        JBLabel labelComp = new JBLabel(label);
        labelComp.setPreferredSize(new Dimension(90, 30));
        panel.add(labelComp, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        JBTextField valueField = new JBTextField(value != null ? value : "");
        valueField.setEditable(false);
        valueField.setPreferredSize(new Dimension(0, 30));
        panel.add(valueField, gbc);
    }
}