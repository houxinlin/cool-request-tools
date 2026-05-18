package dev.coolrequest.tool.rocketmq;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ConsumerPanel extends JPanel {

    private final Project project;
    private final HostManager hostManager;
    private final JBTextField topicField;
    private final JBTextField consumerGroupField;
    private final JBTextField tagExpressionField;
    private final ComboBox<Integer> limitComboBox;
    private final JButton subscribeButton;
    private final JButton clearButton;
    private final JBTable messageTable;
    private final DefaultTableModel tableModel;

    private volatile DefaultMQPushConsumer consumer;
    private volatile boolean running = false;

    public ConsumerPanel(Project project, HostManager hostManager) {
        this.project = project;
        this.hostManager = hostManager;
        setLayout(new BorderLayout(0, 4));
        setBorder(JBUI.Borders.empty(8));

        // Top config row: Topic, Group, Tag in one line
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
        topRow.add(new JBLabel("Group:"), gbc);
        gbc.gridx = 3; gbc.weightx = 0.8;
        consumerGroupField = new JBTextField("CoolRequest_Consumer_Group");
        topRow.add(consumerGroupField, gbc);

        gbc.gridx = 4; gbc.weightx = 0;
        topRow.add(new JBLabel("Tag:"), gbc);
        gbc.gridx = 5; gbc.weightx = 0.4;
        tagExpressionField = new JBTextField("*");
        topRow.add(tagExpressionField, gbc);

        // Message table (init early for button reference)
        String[] columns = {"Time", "MsgId", "Tag", "Key", "Body"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        // Second row: Limit + buttons
        JPanel controlRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        controlRow.add(new JBLabel("Limit:"));
        limitComboBox = new ComboBox<>(new Integer[]{10, 100, 1000, 5000});
        limitComboBox.setSelectedItem(1000);
        controlRow.add(limitComboBox);

        clearButton = new JButton("Clear");
        clearButton.addActionListener(e -> tableModel.setRowCount(0));
        controlRow.add(clearButton);

        subscribeButton = new JButton("Subscribe");
        subscribeButton.addActionListener(e -> toggleSubscription());
        controlRow.add(subscribeButton);

        JPanel northPanel = new JPanel(new BorderLayout());
        northPanel.add(topRow, BorderLayout.NORTH);
        northPanel.add(controlRow, BorderLayout.SOUTH);
        add(northPanel, BorderLayout.NORTH);
        messageTable = new JBTable(tableModel);
        messageTable.setRowHeight(40);
        messageTable.getColumnModel().getColumn(0).setPreferredWidth(140);
        messageTable.getColumnModel().getColumn(1).setPreferredWidth(200);
        messageTable.getColumnModel().getColumn(2).setPreferredWidth(80);
        messageTable.getColumnModel().getColumn(3).setPreferredWidth(80);
        messageTable.getColumnModel().getColumn(4).setPreferredWidth(300);

        messageTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 1) {
                    int row = messageTable.getSelectedRow();
                    if (row >= 0) {
                        String time = (String) tableModel.getValueAt(row, 0);
                        String msgId = (String) tableModel.getValueAt(row, 1);
                        String tag = (String) tableModel.getValueAt(row, 2);
                        String key = (String) tableModel.getValueAt(row, 3);
                        String body = (String) tableModel.getValueAt(row, 4);
                        String topic = topicField.getText().trim();
                        new MessageDetailDialog(project, time, msgId, topic, tag, key, body).show();
                    }
                }
            }
        });

        JBScrollPane tableScroll = new JBScrollPane(messageTable);
        add(tableScroll, BorderLayout.CENTER);
    }

    private void toggleSubscription() {
        if (running) {
            stopConsumer();
        } else {
            startConsumer();
        }
    }

    private void startConsumer() {
        String nameServer = hostManager.getSelectedHost();
        String topic = topicField.getText().trim();
        String group = consumerGroupField.getText().trim();
        String tagExpression = tagExpressionField.getText().trim();

        if (topic.isEmpty() || group.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Topic and Group are required.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (tagExpression.isEmpty()) {
            tagExpression = "*";
        }

        setFieldsEnabled(false);
        subscribeButton.setText("Stop");
        running = true;

        String finalTagExpression = tagExpression;
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                consumer = new DefaultMQPushConsumer(group);
                consumer.setNamesrvAddr(nameServer);
                consumer.subscribe(topic, finalTagExpression);
                consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    for (MessageExt msg : msgs) {
                        String time = sdf.format(new Date(msg.getStoreTimestamp()));
                        String msgId = msg.getMsgId();
                        String tag = msg.getTags() != null ? msg.getTags() : "";
                        String key = msg.getKeys() != null ? msg.getKeys() : "";
                        String body = new String(msg.getBody(), StandardCharsets.UTF_8);
                        SwingUtilities.invokeLater(() -> {
                            int limit = (Integer) limitComboBox.getSelectedItem();
                            if (tableModel.getRowCount() >= limit) {
                                tableModel.removeRow(0);
                            }
                            tableModel.addRow(new Object[]{time, msgId, tag, key, body});
                        });
                    }
                    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                });
                consumer.start();
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(ConsumerPanel.this,
                            "Failed to start consumer:\n" + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                    stopConsumer();
                });
            }
        });
    }

    private void stopConsumer() {
        running = false;
        if (consumer != null) {
            try {
                consumer.shutdown();
            } catch (Exception ignored) {
            }
            consumer = null;
        }
        setFieldsEnabled(true);
        subscribeButton.setText("Subscribe");
    }

    private void setFieldsEnabled(boolean enabled) {
        topicField.setEnabled(enabled);
        consumerGroupField.setEnabled(enabled);
        tagExpressionField.setEnabled(enabled);
        limitComboBox.setEnabled(enabled);
    }
}
