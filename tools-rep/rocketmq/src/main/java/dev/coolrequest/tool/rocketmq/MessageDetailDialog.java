package dev.coolrequest.tool.rocketmq;

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

public class MessageDetailDialog extends DialogWrapper {

    private final Project project;
    private final String time;
    private final String msgId;
    private final String topic;
    private final String tag;
    private final String key;
    private final String body;

    public MessageDetailDialog(Project project, String time, String msgId, String topic, String tag, String key, String body) {
        super(project, true);
        this.project = project;
        this.time = time;
        this.msgId = msgId;
        this.topic = topic;
        this.tag = tag;
        this.key = key;
        this.body = body;
        setTitle("Message Detail");
        setSize(600, 450);
        init();
    }

    @Override
    protected JComponent createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(JBUI.Borders.empty(8));

        JPanel infoPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = JBUI.insets(2, 4);
        gbc.anchor = GridBagConstraints.WEST;

        int row = 0;
        addInfoRow(infoPanel, gbc, row++, "Time:", time);
        addInfoRow(infoPanel, gbc, row++, "MsgId:", msgId);
        addInfoRow(infoPanel, gbc, row++, "Topic:", topic);
        addInfoRow(infoPanel, gbc, row++, "Tag:", tag);
        addInfoRow(infoPanel, gbc, row++, "Key:", key);

        panel.add(infoPanel, BorderLayout.NORTH);

        String normalizedBody = body != null ? body.replace("\r\n", "\n").replace("\r", "\n") : "";
        EditorTextField bodyEditor = new EditorTextField(normalizedBody, project, PlainTextFileType.INSTANCE) {
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
        bodyEditor.setOneLineMode(false);
        bodyEditor.setEnabled(false);
        bodyEditor.setFont(bodyEditor.getFont().deriveFont(14f));
        bodyEditor.setPreferredSize(new Dimension(0, 300));

        panel.add(bodyEditor, BorderLayout.CENTER);

        return panel;
    }

    private void addInfoRow(JPanel panel, GridBagConstraints gbc, int row, String label, String value) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        JBLabel labelComp = new JBLabel(label);
        labelComp.setPreferredSize(new Dimension(50, 40));
        panel.add(labelComp, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        JBTextField valueField = new JBTextField(value != null ? value : "");
        valueField.setEditable(false);
        valueField.setPreferredSize(new Dimension(0, 40));
        panel.add(valueField, gbc);
    }
}
