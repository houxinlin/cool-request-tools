package dev.coolrequest.tool.rabbitmq;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.intellij.openapi.ui.ComboBox;

import javax.swing.*;
import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * RabbitMQ 生产面板：Exchange + RoutingKey + 类型 + 消息体 + Headers + 发送。
 */
public class ProducerPanel extends JPanel {

    private final Project project;
    private final ConnectionManager connectionManager;

    private final JBTextField exchangeField;
    private final JBTextField routingKeyField;
    private final ComboBox<String> typeCombo;
    private final JBTextField contentTypeField;
    private final JBTextField headersField;
    private final EditorTextField bodyEditor;
    private final EditorTextField resultEditor;
    private final JButton sendButton;

    public ProducerPanel(Project project, ConnectionManager connectionManager) {
        this.project = project;
        this.connectionManager = connectionManager;
        setLayout(new BorderLayout(0, 4));
        setBorder(JBUI.Borders.empty(8));

        // Exchange / RoutingKey / Type
        JPanel topRow = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = JBUI.insets(2, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridy = 0;

        gbc.gridx = 0; gbc.weightx = 0;
        topRow.add(new JBLabel("Exchange:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        exchangeField = new JBTextField("");
        topRow.add(exchangeField, gbc);

        gbc.gridx = 2; gbc.weightx = 0;
        topRow.add(new JBLabel("Type:"), gbc);
        gbc.gridx = 3; gbc.weightx = 0;
        // AUTO = 不主动声明，直接按现有交换机发送；显式选类型则维持自动声明（新建时用）
        typeCombo = new ComboBox<>(new String[]{"AUTO", "DIRECT", "TOPIC", "FANOUT", "HEADERS"});
        typeCombo.setSelectedItem("AUTO");
        topRow.add(typeCombo, gbc);

        gbc.gridx = 4; gbc.weightx = 0;
        topRow.add(new JBLabel("RoutingKey:"), gbc);
        gbc.gridx = 5; gbc.weightx = 1.0;
        routingKeyField = new JBTextField("");
        topRow.add(routingKeyField, gbc);

        // second row: Content-Type + Headers
        JPanel midRow = new JPanel(new GridBagLayout());
        GridBagConstraints g2 = new GridBagConstraints();
        g2.fill = GridBagConstraints.HORIZONTAL;
        g2.insets = JBUI.insets(2, 4);
        g2.anchor = GridBagConstraints.WEST;
        g2.gridy = 0;

        g2.gridx = 0; g2.weightx = 0;
        midRow.add(new JBLabel("ContentType:"), g2);
        g2.gridx = 1; g2.weightx = 0.5;
        contentTypeField = new JBTextField("text/plain");
        midRow.add(contentTypeField, g2);

        g2.gridx = 2; g2.weightx = 0;
        midRow.add(new JBLabel("Headers(json):"), g2);
        g2.gridx = 3; g2.weightx = 1.2;
        headersField = new JBTextField("");
        midRow.add(headersField, g2);

        JPanel north = new JPanel(new GridLayout(2, 1, 0, 2));
        north.add(topRow);
        north.add(midRow);
        add(north, BorderLayout.NORTH);

        // Body editor + send button
        // 无参构造（纯 Swing document）：带 project 的重载构造时经 PsiDocumentManager 同步建 document，
        // EDT 无读权限时硬抛 RuntimeExceptionWithAttachments（262 平台实证）；
        // 文件类型用 setNewDocumentAndFileType 补上（EditorEx 无 setFileType）
        bodyEditor = new EditorTextField("") {
            @Override
            protected EditorEx createEditor() {
                EditorEx editor = super.createEditor();
                setupEditorSettings(editor, true);
                return editor;
            }
        };
        // JSON 高亮对齐主插件：JsonFileType（宿主类加载器可解析，主插件生产环境同款用法）
        bodyEditor.setNewDocumentAndFileType(com.intellij.json.JsonFileType.INSTANCE, bodyEditor.getDocument());
        bodyEditor.setOneLineMode(false);
        bodyEditor.setPlaceholder("Message body...");

        JPanel bodyPanel = new JPanel(new BorderLayout(0, 4));
        bodyPanel.add(bodyEditor, BorderLayout.CENTER);
        JPanel sendPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        JButton formatButton = new JButton("Format");
        formatButton.setToolTipText("校验并格式化 Body 中的 JSON（非法时报错位置，不改原文）");
        formatButton.addActionListener(e -> formatBody());
        sendPanel.add(formatButton);
        sendButton = new JButton("Send");
        sendButton.addActionListener(e -> sendMessage());
        sendPanel.add(sendButton);
        bodyPanel.add(sendPanel, BorderLayout.SOUTH);

        // Result editor
        resultEditor = new EditorTextField("") {
            @Override
            protected EditorEx createEditor() {
                EditorEx editor = super.createEditor();
                setupEditorSettings(editor, false);
                return editor;
            }
        };
        resultEditor.setOneLineMode(false);
        resultEditor.setEnabled(false);
        resultEditor.setNewDocumentAndFileType(com.intellij.openapi.fileTypes.PlainTextFileType.INSTANCE, resultEditor.getDocument());

        JBSplitter splitter = new JBSplitter(true, 0.65f);
        splitter.setFirstComponent(bodyPanel);
        splitter.setSecondComponent(resultEditor);
        splitter.setDividerWidth(3);
        splitter.setShowDividerControls(false);
        add(splitter, BorderLayout.CENTER);
    }

    private void formatBody() {
        String body = bodyEditor.getText();
        if (!JsonUtil.isJsonLike(body)) {
            setResultText("Error: Body 不是 JSON（格式化只针对 { 或 [ 开头的内容）。");
            return;
        }
        try {
            bodyEditor.setText(JsonUtil.prettyPrint(body));
            setResultText("Format OK（已格式化，内容未变只调排版）。");
        } catch (IllegalArgumentException ex) {
            setResultText("Error: JSON 不合法——" + ex.getMessage());
        }
    }

    private void setupEditorSettings(EditorEx editor, boolean editable) {        EditorSettings settings = editor.getSettings();
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
        RabbitConnection conn = connectionManager.getSelected();
        String exchange = exchangeField.getText().trim();
        String routingKey = routingKeyField.getText().trim();
        String typeSel = (String) typeCombo.getSelectedItem();
        // AUTO = 不声明直接发；展示用 type 固定 DIRECT（AUTO 下 type 由服务端既有交换机决定）
        final BuiltinExchangeType declareType = "AUTO".equals(typeSel) ? null : BuiltinExchangeType.valueOf(typeSel);
        final BuiltinExchangeType type = declareType == null ? BuiltinExchangeType.DIRECT : declareType;
        String body = bodyEditor.getText();
        String headersJson = headersField.getText().trim();
        String contentType = contentTypeField.getText().trim();

        if (body.isEmpty()) {
            setResultText("Error: Body is required.");
            return;
        }

        Map<String, Object> headers = parseHeaders(headersJson);
        if (headers == null && !headersJson.isEmpty()) {
            setResultText("Error: Headers(Json) 不是合法的 JSON 对象（示例 {\"foo\":\"bar\"}），已取消发送。\n收到内容: " + headersJson);
            return;
        }

        sendButton.setEnabled(false);
        setResultText("Connecting & sending...");

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            long start = System.currentTimeMillis();
            Connection connection = null;
            try {
                connection = openConnection(conn);
                Channel channel = connection.createChannel();
                if ((exchange != null && !exchange.isEmpty()) && declareType != null) {
                    // 显式选了类型 = 声明口径：已存在的交换机按 durable×autoDelete 四组合逐值试探（406 换通道试下一组合，
                    // 命中沿用服务端现状，避免 PRECONDITION_FAILED 打断发送）；不存在才按 (false,true) 创建。
                    // AUTO 类型（declareType==null）= 已有交换机直接发，不声明、绝不自动创建。
                    boolean declared = false;
                    for (boolean[] da : new boolean[][]{{false, true}, {true, true}, {false, false}, {true, false}}) {
                        try {
                            channel.exchangeDeclare(exchange, declareType, da[0], da[1], null);
                            declared = true;
                            break;
                        } catch (Exception declEx) {
                            String dm = String.valueOf(declEx.getMessage())
                                    + (declEx.getCause() != null ? declEx.getCause().getMessage() : "");
                            if (dm.contains("406") || dm.contains("PRECONDITION")) {
                                channel = connection.createChannel(); // 406 会关通道，换新通道再试下一组合
                                continue;
                            }
                            throw declEx;
                        }
                    }
                    if (!declared) {
                        channel = connection.createChannel();
                        channel.exchangeDeclare(exchange, declareType, false, true, null);
                    }
                }
                AMQP.BasicProperties props = buildProperties(contentType, headers);
                byte[] payload = body.getBytes(StandardCharsets.UTF_8);
                // mandatory: 未路由到任何队列时 broker 以 basic.return 退回，据此区分「已路由/被丢弃」
                java.util.List<com.rabbitmq.client.Return> returned = new java.util.concurrent.CopyOnWriteArrayList<>();
                channel.addReturnListener(returned::add);
                channel.basicPublish(exchange, routingKey, true, props, payload);
                TimeUnit.MILLISECONDS.sleep(800);
                channel.close();
                long ms = System.currentTimeMillis() - start;
                boolean unroutable = !returned.isEmpty();
                SwingUtilities.invokeLater(() -> {
                    setResultText((unroutable
                            ? "Send 完成，但消息未被任何队列接收（已由 broker 退回）\n建议: 检查交换机与队列的绑定是否存在；headers 交换机核对 Headers(Json) 是否匹配绑定条件（如 x-match）\n"
                            : "Send OK（已路由到至少一个队列）\n")
                            + "Exchange: " + (exchange.isEmpty() ? "(default)" : exchange) + "  Type: " + type + "\n"
                            + "RoutingKey: " + (routingKey.isEmpty() ? "(default)" : routingKey) + "\n"
                            + "Bytes: " + payload.length + "\n"
                            + "Time: " + ms + " ms\n"
                            + "Host: " + conn.username + "@" + conn.host + ":" + conn.port);
                    sendButton.setEnabled(true);
                });
            } catch (Exception ex) {
                java.io.StringWriter sw = new java.io.StringWriter();
                ex.printStackTrace(new java.io.PrintWriter(sw));
                SwingUtilities.invokeLater(() -> {
                    setResultText("Send Failed:\n" + AmqpErrors.describe(ex) + "\n\n--- Stack Trace ---\n" + sw);
                    sendButton.setEnabled(true);
                });
            } finally {
                if (connection != null) {
                    try {
                        connection.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        });
    }

    /**
     * 解析 Headers(Json)，非法输入返回 null（调用方据此取消发送，不再静默忽略）。
     */
    private Map<String, Object> parseHeaders(String headersJson) {
        if (headersJson == null || headersJson.isEmpty()) {
            return new HashMap<>();
        }
        try {
            Object parsed = new com.google.gson.Gson().fromJson(headersJson, Object.class);
            if (!(parsed instanceof Map)) {
                return null;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> headers = (Map<String, Object>) parsed;
            return headers;
        } catch (Exception ex) {
            return null;
        }
    }

    private AMQP.BasicProperties buildProperties(String contentType, Map<String, Object> headers) {
        AMQP.BasicProperties.Builder b = new AMQP.BasicProperties.Builder();
        if (contentType != null && !contentType.isEmpty()) {
            b.contentType(contentType);
        }
        b.deliveryMode(2); // persistent
        if (headers != null && !headers.isEmpty()) {
            b.headers(new HashMap<>(headers));
        }
        return b.build();
    }

    private Connection openConnection(RabbitConnection conn) throws Exception {
        return RabbitClient.factory(conn).newConnection();
    }

    private void setResultText(String text) {
        String normalized = text.replace("\r\n", "\n").replace("\r", "\n");
        resultEditor.setText(normalized);
    }
}