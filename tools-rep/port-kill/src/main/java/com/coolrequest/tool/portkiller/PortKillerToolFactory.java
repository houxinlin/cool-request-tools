package com.coolrequest.tool.portkiller;


import com.intellij.ui.JBColor;
import dev.coolrequest.tool.CoolToolPanel;
import dev.coolrequest.tool.ToolPanelFactory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class PortKillerToolFactory implements ToolPanelFactory {

    @Override
    public CoolToolPanel createToolPanel() {
        return new CoolToolPanel() {
            @Override
            public JPanel createPanel() {
                return new PortKillerPanel();
            }

            @Override
            public void showTool() {
            }

            @Override
            public void closeTool() {
            }
        };
    }

    // -------------------------------------------------------------------------
    // Main panel
    // -------------------------------------------------------------------------
    public static class PortKillerPanel extends JPanel {

        private static final Color COLOR_SUCCESS = new JBColor(new Color(0, 153, 51), new Color(98, 181, 67));
        private static final Color COLOR_ERROR   = new JBColor(new Color(200, 0, 0),  new Color(220, 80, 80));
        private static final Color COLOR_INFO    = new JBColor(new Color(30, 100, 200), new Color(100, 160, 230));
        private static final Color COLOR_WARN    = new JBColor(new Color(180, 120, 0), new Color(210, 170, 50));

        private final JTextField portField;
        private final JButton    killButton;
        private final JButton    clearButton;
        private final JTextPane  logPane;
        private final JLabel     statusLabel;

        private static final DateTimeFormatter TIME_FMT =
                DateTimeFormatter.ofPattern("HH:mm:ss");

        public PortKillerPanel() {
            setLayout(new BorderLayout(0, 8));
            setBorder(new EmptyBorder(12, 12, 12, 12));

            // ---- top input row ----
            JPanel inputRow = new JPanel(new BorderLayout(8, 0));
            inputRow.setOpaque(false);

            JLabel portLabel = new JLabel("目标端口：");
            portLabel.setFont(portLabel.getFont().deriveFont(Font.BOLD, 13f));

            portField = new JTextField();
            portField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
            portField.setToolTipText("输入要终止的端口号，例如：8080");
            portField.putClientProperty("JTextField.placeholderText", "例如：8080");

            killButton = new JButton("终止进程");
            killButton.setFont(killButton.getFont().deriveFont(Font.BOLD, 13f));
            killButton.setFocusPainted(false);
            killButton.setPreferredSize(new Dimension(110, 32));

            clearButton = new JButton("清空日志");
            clearButton.setFont(clearButton.getFont().deriveFont(13f));
            clearButton.setFocusPainted(false);
            clearButton.setPreferredSize(new Dimension(90, 32));

            JPanel portLeft = new JPanel(new BorderLayout(4, 0));
            portLeft.setOpaque(false);
            portLeft.add(portLabel, BorderLayout.WEST);
            portLeft.add(portField, BorderLayout.CENTER);

            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            buttons.setOpaque(false);
            buttons.add(clearButton);
            buttons.add(killButton);

            inputRow.add(portLeft, BorderLayout.CENTER);
            inputRow.add(buttons,  BorderLayout.EAST);

            // ---- log pane ----
            logPane = new JTextPane();
            logPane.setEditable(false);
            logPane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            logPane.setBackground(new JBColor(new Color(248, 248, 248), new Color(43, 43, 43)));

            JScrollPane scroll = new JScrollPane(logPane);
            scroll.setBorder(BorderFactory.createTitledBorder("执行日志"));

            // ---- status bar ----
            statusLabel = new JLabel("就绪");
            statusLabel.setFont(statusLabel.getFont().deriveFont(11f));
            statusLabel.setForeground(JBColor.GRAY);
            statusLabel.setBorder(new EmptyBorder(4, 2, 0, 0));

            // ---- assemble ----
            add(inputRow, BorderLayout.NORTH);
            add(scroll,   BorderLayout.CENTER);
            add(statusLabel, BorderLayout.SOUTH);

            // ---- listeners ----
            killButton.addActionListener(e -> startKill());
            clearButton.addActionListener(e -> clearLog());

            // Allow Enter key in port field
            portField.addActionListener(e -> startKill());
        }

        // -------------------------------------------------------------------------
        // Kill logic (runs on background thread)
        // -------------------------------------------------------------------------
        private void startKill() {
            String rawPort = portField.getText().trim();
            if (rawPort.isEmpty()) {
                setStatus("请输入端口号", COLOR_WARN);
                return;
            }
            int port;
            try {
                port = Integer.parseInt(rawPort);
                if (port < 1 || port > 65535) throw new NumberFormatException();
            } catch (NumberFormatException ex) {
                appendLog("× 无效端口号：" + rawPort + "（范围 1-65535）", COLOR_ERROR);
                setStatus("输入有误", COLOR_ERROR);
                return;
            }

            setButtonsEnabled(false);
            setStatus("正在查询端口 " + port + " 的进程…", COLOR_INFO);
            appendLog("─────────────────────────────────────────────", JBColor.GRAY);
            appendLog("[" + now() + "] 开始处理端口 " + port, COLOR_INFO);

            int finalPort = port;
            new Thread(() -> {
                try {
                    List<String> pids = findPids(finalPort);
                    if (pids.isEmpty()) {
                        SwingUtilities.invokeLater(() -> {
                            appendLog("✓ 端口 " + finalPort + " 未被任何进程占用", COLOR_SUCCESS);
                            setStatus("端口 " + finalPort + " 空闲", COLOR_SUCCESS);
                            setButtonsEnabled(true);
                        });
                        return;
                    }
                    appendLogEdt("  发现 " + pids.size() + " 个进程：" + String.join(", ", pids), COLOR_INFO);
                    boolean allOk = true;
                    for (String pid : pids) {
                        boolean ok = killPid(pid, finalPort);
                        if (!ok) allOk = false;
                    }
                    final boolean success = allOk;
                    SwingUtilities.invokeLater(() -> {
                        if (success) {
                            appendLog("[" + now() + "] 操作完成，进程已终止", COLOR_SUCCESS);
                            setStatus("端口 " + finalPort + " 进程已终止", COLOR_SUCCESS);
                        } else {
                            appendLog("[" + now() + "] 部分进程终止失败，请以管理员身份运行", COLOR_WARN);
                            setStatus("部分失败，请以管理员权限重试", COLOR_WARN);
                        }
                        setButtonsEnabled(true);
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        appendLog("× 执行异常：" + ex.getMessage(), COLOR_ERROR);
                        setStatus("执行异常", COLOR_ERROR);
                        setButtonsEnabled(true);
                    });
                }
            }, "port-killer-thread").start();
        }

        // -------------------------------------------------------------------------
        // Platform helpers
        // -------------------------------------------------------------------------
        private List<String> findPids(int port) throws Exception {
            List<String> pids = new ArrayList<>();
            String os = System.getProperty("os.name", "").toLowerCase();

            if (os.contains("win")) {
                // netstat -ano | findstr :PORT
                Process p = Runtime.getRuntime().exec(
                        new String[]{"cmd", "/c", "netstat -ano | findstr :" + port});
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty()) continue;
                        // Example: TCP  0.0.0.0:8080  0.0.0.0:0  LISTENING  1234
                        // We only care about lines containing ":PORT " (LISTENING or ESTABLISHED)
                        if (!line.contains(":" + port + " ") && !line.contains(":" + port + "\t")) continue;
                        String[] parts = line.split("\\s+");
                        if (parts.length >= 5) {
                            String pid = parts[parts.length - 1];
                            if (pid.matches("\\d+") && !pids.contains(pid)) {
                                pids.add(pid);
                                appendLogEdt("  PID " + pid + "  [" + line + "]", JBColor.foreground());
                            }
                        }
                    }
                }
            } else {
                // lsof -ti :PORT
                Process p = Runtime.getRuntime().exec(
                        new String[]{"sh", "-c", "lsof -ti :" + port});
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        line = line.trim();
                        if (!line.isEmpty() && line.matches("\\d+") && !pids.contains(line)) {
                            pids.add(line);
                            appendLogEdt("  PID " + line, JBColor.foreground());
                        }
                    }
                }
            }
            return pids;
        }

        private boolean killPid(String pid, int port) {
            String os = System.getProperty("os.name", "").toLowerCase();
            try {
                Process p;
                if (os.contains("win")) {
                    appendLogEdt("  → 执行: taskkill /PID " + pid + " /F", COLOR_INFO);
                    p = Runtime.getRuntime().exec(
                            new String[]{"cmd", "/c", "taskkill /PID " + pid + " /F"});
                } else {
                    appendLogEdt("  → 执行: kill -9 " + pid, COLOR_INFO);
                    p = Runtime.getRuntime().exec(new String[]{"sh", "-c", "kill -9 " + pid});
                }
                int exitCode = p.waitFor();
                String msg = readStream(p);
                if (exitCode == 0) {
                    appendLogEdt("  ✓ PID " + pid + " 已终止", COLOR_SUCCESS);
                    return true;
                } else {
                    appendLogEdt("  × PID " + pid + " 终止失败（退出码 " + exitCode + "）" +
                            (msg.isEmpty() ? "" : "：" + msg.trim()), COLOR_ERROR);
                    return false;
                }
            } catch (Exception ex) {
                appendLogEdt("  × 终止 PID " + pid + " 异常：" + ex.getMessage(), COLOR_ERROR);
                return false;
            }
        }

        private String readStream(Process p) throws Exception {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line).append("\n");
            }
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(p.getErrorStream()))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line).append("\n");
            }
            return sb.toString();
        }

        // -------------------------------------------------------------------------
        // UI helpers
        // -------------------------------------------------------------------------
        private void appendLogEdt(String text, Color color) {
            SwingUtilities.invokeLater(() -> appendLog(text, color));
        }

        private void appendLog(String text, Color color) {
            StyledDocument doc = logPane.getStyledDocument();
            SimpleAttributeSet attrs = new SimpleAttributeSet();
            StyleConstants.setForeground(attrs, color);
            try {
                doc.insertString(doc.getLength(), text + "\n", attrs);
            } catch (BadLocationException ignored) {
            }
            // auto-scroll to bottom
            logPane.setCaretPosition(doc.getLength());
        }

        private void clearLog() {
            logPane.setText("");
            setStatus("就绪", JBColor.GRAY);
        }

        private void setStatus(String text, Color color) {
            statusLabel.setText(text);
            statusLabel.setForeground(color);
        }

        private void setButtonsEnabled(boolean enabled) {
            killButton.setEnabled(enabled);
            killButton.setText(enabled ? "终止进程" : "处理中…");
        }

        private String now() {
            return LocalTime.now().format(TIME_FMT);
        }
    }
}
