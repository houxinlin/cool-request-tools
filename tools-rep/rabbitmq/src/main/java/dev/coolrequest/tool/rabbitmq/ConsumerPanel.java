package dev.coolrequest.tool.rabbitmq;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * RabbitMQ 消费面板：订阅队列、实时表格、多策略 Body 解码、消息详情。
 */
public class ConsumerPanel extends JPanel {

    private final Project project;
    private final ConnectionManager connectionManager;

    private final JBTextField queueField;
    private final JBTextField consumerPrefixField;
    private final ComboBox<Integer> limitComboBox;
    private final ComboBox<Integer> prefetchComboBox;
    private final JBCheckBox autoDeclareCheck;
    private final JBCheckBox autoAckCheck;
    private final ComboBox<String> decodeCombo;
    private final JButton subscribeButton;
    private final JButton clearButton;
    private final JButton ackButton;
    private final JButton requeueButton;
    private final JButton rejectButton;
    private final JBLabel depthLabel;
    private final JButton refreshButton;
    /** 订阅中每 2 秒刷新队列深度（EDT 定时器，实际 RPC 在后台线程） */
    private final javax.swing.Timer depthTimer;
    private final JBTable messageTable;
    private final DefaultTableModel tableModel;
    /** 与表格行一一对应的原始 body；add 于底部、limit 超限移除头部，避免行号随 removeRow 错位 */
    private final java.util.List<byte[]> rowBodies = new java.util.ArrayList<>();
    /** 与表格行一一对应的 delivery tag（手动确认用；-1 表示该行不可确认） */
    private final java.util.List<Long> rowTags = new java.util.ArrayList<>();
    /** 与表格行一一对应的确认状态：Unacked / Acked / Requeued / Rejected / Auto */
    private final java.util.List<String> rowStates = new java.util.ArrayList<>();

    private volatile Connection connection;
    private volatile Channel consumeChannel;
    private volatile String consumerTag;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private RabbitConnection activeConnection;

    public ConsumerPanel(Project project, ConnectionManager connectionManager) {
        this.project = project;
        this.connectionManager = connectionManager;
        this.activeConnection = connectionManager.getSelected();
        setLayout(new BorderLayout(0, 4));
        setBorder(JBUI.Borders.empty(8));

        // Top config row
        JPanel topRow = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = JBUI.insets(2, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridy = 0;

        gbc.gridx = 0; gbc.weightx = 0;
        topRow.add(new JBLabel("Queue:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        queueField = new JBTextField();
        topRow.add(queueField, gbc);

        gbc.gridx = 2; gbc.weightx = 0;
        topRow.add(new JBLabel("ConsumerPrefix:"), gbc);
        gbc.gridx = 3; gbc.weightx = 0.6;
        consumerPrefixField = new JBTextField("CoolRequest-");
        topRow.add(consumerPrefixField, gbc);

        // 队列即时深度（ready）+ 手动刷新：确认/退回后一眼看出队列余量与操作是否生效
        gbc.gridx = 4; gbc.weightx = 0;
        topRow.add(new JBLabel("Ready:"), gbc);
        gbc.gridx = 5; gbc.weightx = 0;
        depthLabel = new JBLabel("—");
        depthLabel.setToolTipText("队列当前可投递消息数（AMQP queueDeclarePassive 即时读数）；"
                + "订阅中每 2 秒自动刷新。在途未确认数 AMQP 不提供，需管理 API 查询。");
        topRow.add(depthLabel, gbc);
        gbc.gridx = 6; gbc.weightx = 0;
        refreshButton = new JButton("Refresh");
        refreshButton.setToolTipText("立即重新读取队列深度");
        refreshButton.addActionListener(e -> refreshQueueDepth());
        topRow.add(refreshButton, gbc);

        // Table columns
        String[] columns = {"Time", "DeliveryTag", "Ack", "RoutingKey", "Size", "Body"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        // Second row: 高频控件留主行（Decode/Auto ack/Ack/Subscribe/More），低频与配置类收进 More 弹出面板
        JPanel controlRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        prefetchComboBox = new ComboBox<>(new Integer[]{0, 1, 10, 50, 100});
        prefetchComboBox.setSelectedItem(10);
        limitComboBox = new ComboBox<>(new Integer[]{10, 100, 500, 1000, 5000});
        limitComboBox.setSelectedItem(200);
        autoDeclareCheck = new JBCheckBox("Auto-declare", true);
        requeueButton = new JButton("Requeue");
        requeueButton.setToolTipText("退回选中行：消息回到队列并重投（标记 redeliver）");
        requeueButton.addActionListener(e -> settleSelected(Settle.REQUEUE));
        rejectButton = new JButton("Reject");
        rejectButton.setToolTipText("拒绝选中行：消息丢弃（未配置死信队列时不进 DLX）");
        rejectButton.addActionListener(e -> settleSelected(Settle.REJECT));
        clearButton = new JButton("Clear");
        clearButton.addActionListener(e -> {
            tableModel.setRowCount(0);
            rowBodies.clear();
            rowTags.clear();
            rowStates.clear();
            updateAckButtons();
        });


        controlRow.add(new JBLabel("Decode:"));
        decodeCombo = new ComboBox<>(new String[]{"JSON", "Text", "Hex"});
        controlRow.add(decodeCombo);

        autoAckCheck = new JBCheckBox("Auto ack", false);
        autoAckCheck.setToolTipText("勾选后消息投递即确认（立刻从队列删除，不可恢复）；"
                + "不勾选为手动确认：用 Ack / Requeue / Reject 处理选中行");
        autoAckCheck.addActionListener(e -> {
            boolean auto = autoAckCheck.isSelected();
            prefetchComboBox.setEnabled(!auto && !running.get());
            updateAckButtons();
        });
        controlRow.add(autoAckCheck);

        ackButton = new JButton("Ack");
        ackButton.setToolTipText("确认选中行：消息从队列删除");
        ackButton.addActionListener(e -> settleSelected(Settle.ACK));
        controlRow.add(ackButton);

        subscribeButton = new JButton("Subscribe");
        subscribeButton.addActionListener(e -> toggleSubscription());
        controlRow.add(subscribeButton);

        // --- More 弹出面板（同一组件实例从主行挪入，enablement 联动/ack 终态守卫照常生效） ---
        JPanel morePanel = new JPanel(new GridBagLayout());
        GridBagConstraints mgbc = new GridBagConstraints();
        mgbc.insets = JBUI.insets(4, 8);
        mgbc.anchor = GridBagConstraints.WEST;
        mgbc.gridy = 0;
        mgbc.gridx = 0;
        morePanel.add(new JBLabel("Prefetch:"), mgbc);
        mgbc.gridx = 1;
        morePanel.add(prefetchComboBox, mgbc);
        mgbc.gridx = 2;
        morePanel.add(new JBLabel("Limit:"), mgbc);
        mgbc.gridx = 3;
        morePanel.add(limitComboBox, mgbc);
        mgbc.gridy = 1;
        mgbc.gridx = 0;
        morePanel.add(autoDeclareCheck, mgbc);
        mgbc.gridx = 1;
        morePanel.add(requeueButton, mgbc);
        mgbc.gridx = 2;
        morePanel.add(rejectButton, mgbc);
        mgbc.gridx = 3;
        morePanel.add(clearButton, mgbc);

        JButton moreButton = new JButton("More ▾");
        moreButton.setToolTipText("Prefetch / Limit / Auto-declare / Requeue / Reject / Clear");
        moreButton.addActionListener(e -> {
            JBPopup popup = JBPopupFactory.getInstance()
                    .createComponentPopupBuilder(morePanel, null)
                    .setTitle("More")
                    .setMovable(false)
                    .setCancelOnClickOutside(true)
                    .setCancelOnOtherWindowOpen(true)
                    .createPopup();
            popup.show(new RelativePoint(moreButton,
                    new Point(moreButton.getWidth() / 2, moreButton.getHeight())));
        });
        controlRow.add(moreButton);

        JPanel northPanel = new JPanel(new BorderLayout());
        northPanel.add(topRow, BorderLayout.NORTH);
        northPanel.add(controlRow, BorderLayout.SOUTH);
        add(northPanel, BorderLayout.NORTH);

        messageTable = new JBTable(tableModel);
        messageTable.setRowHeight(40);
        messageTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        messageTable.getColumnModel().getColumn(0).setPreferredWidth(140);
        messageTable.getColumnModel().getColumn(1).setPreferredWidth(90);
        messageTable.getColumnModel().getColumn(2).setPreferredWidth(80);
        messageTable.getColumnModel().getColumn(3).setPreferredWidth(160);
        messageTable.getColumnModel().getColumn(4).setPreferredWidth(60);
        messageTable.getColumnModel().getColumn(5).setPreferredWidth(340);
        messageTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateAckButtons();
            }
        });

        messageTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = messageTable.getSelectedRow();
                    if (row >= 0) {
                        openDetail(row);
                    }
                }
            }
        });

        JBScrollPane tableScroll = new JBScrollPane(messageTable);
        add(tableScroll, BorderLayout.CENTER);

        depthTimer = new javax.swing.Timer(2000, e -> refreshQueueDepth());
        depthTimer.setRepeats(true);
    }

    public void onConnectionChanged(RabbitConnection conn) {
        RabbitConnection prev = activeConnection;
        this.activeConnection = conn;
        if (running.get() && (prev == null || !prev.name.equals(conn.name))) {
            stopConsumer();
        }
    }

    private void toggleSubscription() {
        if (running.get()) {
            stopConsumer();
        } else {
            startConsumer();
        }
    }

    private void startConsumer() {
        RabbitConnection conn = connectionManager.getSelected();
        String queue = queueField.getText().trim();
        String consumerPrefix = consumerPrefixField.getText().trim();
        if (consumerPrefix.isEmpty()) {
            consumerPrefix = "CoolRequest-";
        }
        int prefetch = (Integer) prefetchComboBox.getSelectedItem();
        boolean autoAck = autoAckCheck.isSelected();
        int decodeMode = decodeMode();

        if (queue.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Queue is required.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        setFieldsEnabled(false);
        subscribeButton.setText("Stop");
        running.set(true);

        String finalPrefix = consumerPrefix;
        if (finalPrefix == null || finalPrefix.isEmpty()) {
            finalPrefix = "CoolRequest-";
        }
        final String prefixForThread = finalPrefix;
        int finalMode = decodeMode;

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            Connection conn2 = null;
            try {
                conn2 = openConnection(conn);
                this.connection = conn2;
                Channel channel = conn2.createChannel();
                this.consumeChannel = channel;
                if (!autoAck && prefetch > 0) {
                    channel.basicQos(prefetch);
                }
                String declaredQueue = queue;
                if (autoDeclareCheck.isSelected()) {
                    channel.queueDeclare(queue, true, false, false, null);
                } else {
                    channel.queueDeclarePassive(queue);
                }

                DefaultConsumer consumer = new DefaultConsumer(channel) {
                    @Override
                    public void handleDelivery(String tag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) {
                        receive(envelope, properties, body, finalMode, autoAck);
                    }
                };
                String tagName = channel.basicConsume(declaredQueue, autoAck, prefixForThread + "-" + System.currentTimeMillis(), false, false, null, consumer);
                this.consumerTag = tagName;
                SwingUtilities.invokeLater(() -> {
                    tableModel.setRowCount(0);
                    rowBodies.clear();
                    rowTags.clear();
                    rowStates.clear();
                    updateAckButtons();
                    depthTimer.start();
                    refreshQueueDepth();
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(ConsumerPanel.this,
                            "Failed to start consumer:\n" + AmqpErrors.describe(ex),
                            "Error", JOptionPane.ERROR_MESSAGE);
                    stopConsumer();
                });
            }
        });
    }

    private void receive(Envelope envelope, AMQP.BasicProperties properties, byte[] body, int decodeMode, boolean autoAck) {
        final String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        final String tag = envelope.getDeliveryTag() + (envelope.isRedeliver() ? " (redeliver)" : "");
        final String routingKey = envelope.getRoutingKey();
        final String exchange = envelope.getExchange();
        final int size = body == null ? 0 : body.length;
        final byte[] bodyBytes = body == null ? new byte[0] : body;
        final long deliveryTag = envelope.getDeliveryTag();
        final String state = autoAck ? "Auto" : "Unacked";
        String decoded;
        try {
            decoded = BodyCodec.decode(bodyBytes, decodeMode);
        } catch (Exception ex) {
            decoded = "(decode error: " + AmqpErrors.describe(ex) + ")";
        }
        String bodyPreview = decoded.length() > 200 ? decoded.substring(0, 200) + "…" : decoded;

        SwingUtilities.invokeLater(() -> {
            int limit = (Integer) limitComboBox.getSelectedItem();
            if (tableModel.getRowCount() >= limit) {
                tableModel.removeRow(0);
                if (!rowBodies.isEmpty()) {
                    rowBodies.remove(0);
                }
                if (!rowTags.isEmpty()) {
                    rowTags.remove(0);
                }
                if (!rowStates.isEmpty()) {
                    rowStates.remove(0);
                }
            }
            tableModel.addRow(new Object[]{time, tag, state, routingKey, size, bodyPreview});
            rowBodies.add(bodyBytes);
            rowTags.add(deliveryTag);
            rowStates.add(state);
        });
    }

    private enum Settle {
        ACK, REQUEUE, REJECT
    }

    /** 对选中行逐条确认（tag 可能不连续，故逐条 single，不用 multiple）。 */
    private void settleSelected(Settle action) {
        int[] rows = messageTable.getSelectedRows();
        if (rows.length == 0) {
            return;
        }
        Channel ch = consumeChannel;
        if (ch == null || !ch.isOpen()) {
            JOptionPane.showMessageDialog(this, "未在订阅中或通道已关闭，无法确认。", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        // 收集 (行号, tag)，跳过已处于终态的行
        java.util.List<int[]> targets = new java.util.ArrayList<>();
        java.util.List<Long> tags = new java.util.ArrayList<>();
        for (int row : rows) {
            if (row < 0 || row >= rowTags.size()) {
                continue;
            }
            String st = rowStates.get(row);
            if (!"Unacked".equals(st)) {
                continue;
            }
            targets.add(new int[]{row});
            tags.add(rowTags.get(row));
        }
        if (tags.isEmpty()) {
            JOptionPane.showMessageDialog(this, "选中行没有待确认的消息（已确认/已退回/已拒绝或为 Auto ack 模式）。",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        setAckButtonsEnabled(false);
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            String fail = null;
            java.util.List<Integer> doneRows = new java.util.ArrayList<>();
            for (int i = 0; i < tags.size(); i++) {
                long tagValue = tags.get(i);
                int row = targets.get(i)[0];
                try {
                    if (action == Settle.ACK) {
                        ch.basicAck(tagValue, false);
                    } else if (action == Settle.REQUEUE) {
                        ch.basicNack(tagValue, false, true);
                    } else {
                        ch.basicNack(tagValue, false, false);
                    }
                    doneRows.add(row);
                } catch (Exception ex) {
                    fail = AmqpErrors.describe(ex);
                    break;
                }
            }
            final String failMsg = fail;
            SwingUtilities.invokeLater(() -> {
                String newState = action == Settle.ACK ? "Acked" : (action == Settle.REQUEUE ? "Requeued" : "Rejected");
                for (int row : doneRows) {
                    if (row < rowStates.size()) {
                        rowStates.set(row, newState);
                        tableModel.setValueAt(newState, row, 2);
                    }
                }
                updateAckButtons();
                refreshQueueDepth();
                if (failMsg != null) {
                    JOptionPane.showMessageDialog(this, "确认操作失败：\n" + failMsg, "Error", JOptionPane.ERROR_MESSAGE);
                }
            });
        });
    }

    private void updateAckButtons() {
        boolean manual = !autoAckCheck.isSelected();
        boolean hasSelection = messageTable != null && messageTable.getSelectedRowCount() > 0;
        setAckButtonsEnabled(manual && hasSelection && running.get());
    }

    /** 读取队列即时深度（ready）。订阅中复用消费连接，未订阅时临时开一条连接后关闭。 */
    private void refreshQueueDepth() {
        RabbitConnection conn = connectionManager.getSelected();
        String queue = queueField.getText().trim();
        if (conn == null || queue.isEmpty()) {
            depthLabel.setText("—");
            return;
        }
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            String text;
            try {
                Connection c = connection;
                boolean own = c == null || !c.isOpen();
                if (own) {
                    c = openConnection(conn);
                }
                Channel probe = c.createChannel();
                int ready = probe.queueDeclarePassive(queue).getMessageCount();
                probe.close();
                if (own) {
                    c.close();
                }
                text = String.valueOf(ready);
            } catch (Exception ex) {
                text = "N/A";
            }
            final String value = text;
            SwingUtilities.invokeLater(() -> depthLabel.setText(value));
        });
    }

    private void setAckButtonsEnabled(boolean enabled) {
        ackButton.setEnabled(enabled);
        requeueButton.setEnabled(enabled);
        rejectButton.setEnabled(enabled);
    }

    private void openDetail(int row) {
        String time = (String) tableModel.getValueAt(row, 0);
        String deliveryTag = (String) tableModel.getValueAt(row, 1);
        String routingKey = (String) tableModel.getValueAt(row, 3);
        String size = String.valueOf(tableModel.getValueAt(row, 4));
        byte[] body = row >= 0 && row < rowBodies.size() ? rowBodies.get(row) : null;
        new MessageDetailDialog(project, time, deliveryTag, routingKey, size, body, queueField.getText().trim()).show();
    }

    private void stopConsumer() {
        running.set(false);
        String tag = consumerTag;
        Channel ch = consumeChannel;
        Connection c = connection;
        consumeChannel = null;
        consumerTag = null;
        connection = null;
        if (tag != null && ch != null && ch.isOpen()) {
            try {
                ch.basicCancel(tag);
            } catch (Exception ignored) {
            }
        }
        if (c != null && c.isOpen()) {
            try {
                c.close();
            } catch (Exception ignored) {
            }
        }
        setFieldsEnabled(true);
        subscribeButton.setText("Subscribe");
        depthTimer.stop();
        updateAckButtons();
    }

    public void dispose() {
        depthTimer.stop();
        stopConsumer();
    }

    private void setFieldsEnabled(boolean enabled) {
        queueField.setEnabled(enabled);
        consumerPrefixField.setEnabled(enabled);
        prefetchComboBox.setEnabled(enabled && !autoAckCheck.isSelected());
        limitComboBox.setEnabled(enabled);
        decodeCombo.setEnabled(enabled);
        autoDeclareCheck.setEnabled(enabled);
        autoAckCheck.setEnabled(enabled);
    }

    private int decodeMode() {
        Object sel = decodeCombo.getSelectedItem();
        if ("Hex".equals(sel)) {
            return BodyCodec.MODE_HEX;
        }
        return BodyCodec.MODE_JSON;
    }

    private Connection openConnection(RabbitConnection conn) throws Exception {
        return RabbitClient.factory(conn).newConnection();
    }
}