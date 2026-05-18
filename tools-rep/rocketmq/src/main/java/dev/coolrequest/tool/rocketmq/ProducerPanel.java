package dev.coolrequest.tool.rocketmq;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;

import javax.swing.*;
import java.awt.*;
import java.nio.charset.StandardCharsets;

public class ProducerPanel extends JPanel {

    private final Project project;
    private final HostManager hostManager;
    private final JBTextField topicField;
    private final JBTextField tagField;
    private final JBTextField keyField;
    private final EditorTextField bodyEditor;
    private final EditorTextField resultEditor;
    private final JButton sendButton;

    public ProducerPanel(Project project, HostManager hostManager) {
        this.project = project;
        this.hostManager = hostManager;
        setLayout(new BorderLayout(0, 4));
        setBorder(JBUI.Borders.empty(8));

        // Top row: Topic, Tag, Key in one line
        JPanel topRow = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = JBUI.insets(2, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridy = 0;

        gbc.gridx = 0; gbc.weightx = 0;
        topRow.add(new JBLabel("Topic:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        topicField = new JBTextField();
        topRow.add(topicField, gbc);

        gbc.gridx = 2; gbc.weightx = 0;
        topRow.add(new JBLabel("Tag:"), gbc);
        gbc.gridx = 3; gbc.weightx = 0.6;
        tagField = new JBTextField();
        topRow.add(tagField, gbc);

        gbc.gridx = 4; gbc.weightx = 0;
        topRow.add(new JBLabel("Key:"), gbc);
        gbc.gridx = 5; gbc.weightx = 0.6;
        keyField = new JBTextField();
        topRow.add(keyField, gbc);

        add(topRow, BorderLayout.NORTH);

        // Body editor (upper part of splitter)
        bodyEditor = new EditorTextField("", project, com.intellij.openapi.fileTypes.PlainTextFileType.INSTANCE) {
            @Override
            protected EditorEx createEditor() {
                EditorEx editor = super.createEditor();
                setupEditorSettings(editor, true);
                return editor;
            }
        };
        bodyEditor.setOneLineMode(false);
        bodyEditor.setPlaceholder("Message body...");

        // Send button between editors
        JPanel bodyPanel = new JPanel(new BorderLayout(0, 4));
        bodyPanel.add(bodyEditor, BorderLayout.CENTER);
        JPanel sendPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        sendButton = new JButton("Send");
        sendButton.addActionListener(e -> sendMessage());
        sendPanel.add(sendButton);
        bodyPanel.add(sendPanel, BorderLayout.SOUTH);

        // Result editor (lower part of splitter)
        resultEditor = new EditorTextField("", project, com.intellij.openapi.fileTypes.PlainTextFileType.INSTANCE) {
            @Override
            protected EditorEx createEditor() {
                EditorEx editor = super.createEditor();
                setupEditorSettings(editor, false);
                return editor;
            }
        };
        resultEditor.setOneLineMode(false);
        resultEditor.setEnabled(false);

        // Splitter
        JBSplitter splitter = new JBSplitter(true, 0.65f);
        splitter.setFirstComponent(bodyPanel);
        splitter.setSecondComponent(resultEditor);
        splitter.setDividerWidth(3);
        splitter.setShowDividerControls(false);

        add(splitter, BorderLayout.CENTER);
    }

    private void setupEditorSettings(EditorEx editor, boolean editable) {
        EditorSettings settings = editor.getSettings();
        settings.setLineNumbersShown(true);
        settings.setFoldingOutlineShown(false);
        settings.setAdditionalLinesCount(1);
        settings.setAdditionalColumnsCount(0);
        settings.setLineMarkerAreaShown(false);
        settings.setIndentGuidesShown(true);
        settings.setVirtualSpace(false);
        settings.setUseSoftWraps(true);
        settings.setGutterIconsShown(false);
        editor.setHorizontalScrollbarVisible(true);
        editor.setVerticalScrollbarVisible(true);
    }

    private void sendMessage() {
        String nameServer = hostManager.getSelectedHost();
        String topic = topicField.getText().trim();
        String tag = tagField.getText().trim();
        String key = keyField.getText().trim();
        String body = bodyEditor.getText();

        if (topic.isEmpty() || body.isEmpty()) {
            setResultText("Error: Topic and Body are required.");
            return;
        }

        sendButton.setEnabled(false);
        setResultText("Sending...");

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            DefaultMQProducer producer = new DefaultMQProducer("CoolRequest_Producer_Group");
            producer.setNamesrvAddr(nameServer);
            producer.setSendMsgTimeout(10000);
            try {
                producer.start();
                Message msg = new Message(topic, tag.isEmpty() ? null : tag, body.getBytes(StandardCharsets.UTF_8));
                if (!key.isEmpty()) {
                    msg.setKeys(key);
                }
                SendResult result = producer.send(msg);
                SwingUtilities.invokeLater(() -> {
                    setResultText("Send OK\n"
                            + "MsgId: " + result.getMsgId() + "\n"
                            + "Status: " + result.getSendStatus() + "\n"
                            + "Queue: " + result.getMessageQueue());
                    sendButton.setEnabled(true);
                });
            } catch (Exception ex) {
                java.io.StringWriter sw = new java.io.StringWriter();
                ex.printStackTrace(new java.io.PrintWriter(sw));
                SwingUtilities.invokeLater(() -> {
                    setResultText("Send Failed:\n" + ex.getMessage() + "\n\n--- Stack Trace ---\n" + sw.toString());
                    sendButton.setEnabled(true);
                });
            } finally {
                producer.shutdown();
            }
        });
    }

    private void setResultText(String text) {
        String normalized = text.replace("\r\n", "\n").replace("\r", "\n");
        resultEditor.setText(normalized);
    }
}
