package dev.coolrequest.tool.staticserver;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.JBColor;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.TreeSet;

public class StaticServerPanel extends JPanel {

    private final Project project;

    private final ServerTableModel tableModel = new ServerTableModel();
    private final JBTable serverTable = new JBTable(tableModel);

    private final JBTextField portField = new JBTextField("8000", 6);
    private final JBTextField dirField = new JBTextField(28);
    private final JButton chooseDirButton = new JButton("浏览…");

    private final JBTextArea logArea = new JBTextArea();

    private final SimpleDateFormat logTime = new SimpleDateFormat("HH:mm:ss");

    public StaticServerPanel(Project project) {
        this.project = project;
        setLayout(new BorderLayout());

        add(buildTopPanel(), BorderLayout.NORTH);

        JBSplitter splitter = new JBSplitter(true, 0.6f);
        splitter.setFirstComponent(buildServerListPanel());
        splitter.setSecondComponent(buildLogPanel());
        add(splitter, BorderLayout.CENTER);

        loadSavedServers();
    }

    private void loadSavedServers() {
        for (ServerConfigStore.ServerConfig cfg : ServerConfigStore.load()) {
            File dir = new File(cfg.dirPath == null ? "" : cfg.dirPath);
            StaticFileServer server = new StaticFileServer(cfg.port, dir, this::log);
            tableModel.add(new ServerEntry(server));
        }
        if (tableModel.getRowCount() > 0) {
            log("INFO", "已加载 " + tableModel.getRowCount() + " 条服务配置");
        }
    }

    private void persistServers() {
        List<ServerConfigStore.ServerConfig> list = new ArrayList<>();
        for (ServerEntry e : tableModel.entries) {
            list.add(new ServerConfigStore.ServerConfig(
                    e.server.getPort(), e.server.getRootDir().getAbsolutePath()));
        }
        try {
            ServerConfigStore.save(list);
        } catch (IOException ex) {
            log("ERR", "保存配置失败: " + ex.getMessage());
        }
    }

    // ── Top: add-server form ───────────────────────────────────────────────
    private JPanel buildTopPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(JBUI.Borders.empty(8, 10, 4, 10));

        GridBagConstraints c = new GridBagConstraints();
        c.insets = JBUI.insets(2, 4);
        c.gridy = 0;
        c.fill = GridBagConstraints.HORIZONTAL;

        c.gridx = 0; c.weightx = 0;
        p.add(new JBLabel("端口："), c);
        c.gridx = 1;
        p.add(portField, c);

        c.gridx = 2;
        p.add(new JBLabel("资源目录："), c);

        c.gridx = 3; c.weightx = 1;
        p.add(dirField, c);

        c.gridx = 4; c.weightx = 0;
        p.add(chooseDirButton, c);

        JButton addBtn = new JButton("添加并启动", AllIcons.Actions.Execute);
        c.gridx = 5;
        p.add(addBtn, c);

        chooseDirButton.addActionListener(e -> chooseDirectory());
        addBtn.addActionListener(e -> addAndStartServer());

        return p;
    }

    private void chooseDirectory() {
        FileChooserDescriptor descriptor = FileChooserDescriptorFactory
                .createSingleFolderDescriptor()
                .withTitle("选择资源目录");
        VirtualFile vf = FileChooser.chooseFile(descriptor, project, null);
        if (vf != null) {
            dirField.setText(vf.getPath());
        }
    }

    // ── Center: server table ───────────────────────────────────────────────
    private JPanel buildServerListPanel() {
        serverTable.setShowGrid(false);
        serverTable.setStriped(true);
        serverTable.setRowHeight(JBUI.scale(24));
        serverTable.getEmptyText().setText("暂无服务，添加端口和目录后启动");

        // 状态列渲染：圆点 + 文本
        serverTable.getColumnModel().getColumn(0).setCellRenderer(new StatusRenderer());
        serverTable.getColumnModel().getColumn(0).setPreferredWidth(JBUI.scale(70));
        serverTable.getColumnModel().getColumn(1).setPreferredWidth(JBUI.scale(70));
        serverTable.getColumnModel().getColumn(2).setPreferredWidth(JBUI.scale(360));
        serverTable.getColumnModel().getColumn(3).setPreferredWidth(JBUI.scale(220));

        // 双击地址列时复制
        serverTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    int row = serverTable.rowAtPoint(e.getPoint());
                    int col = serverTable.columnAtPoint(e.getPoint());
                    if (row >= 0 && col == 3) {
                        Object val = serverTable.getValueAt(row, col);
                        if (val != null) {
                            CopyPasteManager.getInstance().setContents(new StringSelection(val.toString()));
                            log("INFO", "已复制到剪贴板: " + val);
                        }
                    }
                }
            }
        });

        ToolbarDecorator decorator = ToolbarDecorator.createDecorator(serverTable)
                .disableAddAction()
                .disableUpDownActions()
                .setRemoveAction(b -> removeSelected())
                .addExtraAction(new AnActionButton("启动", AllIcons.Actions.Execute) {
                    @Override
                    public void actionPerformed(com.intellij.openapi.actionSystem.AnActionEvent e) {
                        startSelected();
                    }
                })
                .addExtraAction(new AnActionButton("停止", AllIcons.Actions.Suspend) {
                    @Override
                    public void actionPerformed(com.intellij.openapi.actionSystem.AnActionEvent e) {
                        stopSelected();
                    }
                })
                .addExtraAction(new AnActionButton("打开浏览器", AllIcons.General.Web) {
                    @Override
                    public void actionPerformed(com.intellij.openapi.actionSystem.AnActionEvent e) {
                        openInBrowser();
                    }
                })
                .addExtraAction(new AnActionButton("打开目录", AllIcons.Actions.MenuOpen) {
                    @Override
                    public void actionPerformed(com.intellij.openapi.actionSystem.AnActionEvent e) {
                        openDirectory();
                    }
                });

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBorder(JBUI.Borders.empty(0, 6, 4, 6));
        wrap.add(decorator.createPanel(), BorderLayout.CENTER);
        return wrap;
    }

    // ── Bottom: log area ───────────────────────────────────────────────────
    private JPanel buildLogPanel() {
        logArea.setEditable(false);
        logArea.setLineWrap(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, JBUI.scale(12)));

        JBScrollPane scroll = new JBScrollPane(logArea);

        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(JBUI.Borders.empty(2, 8));
        header.add(new JBLabel("运行日志"), BorderLayout.WEST);
        JButton clearBtn = new JButton("清空", AllIcons.Actions.GC);
        clearBtn.setFocusable(false);
        clearBtn.addActionListener(e -> logArea.setText(""));
        header.add(clearBtn, BorderLayout.EAST);

        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(JBUI.Borders.empty(0, 6, 6, 6));
        p.add(header, BorderLayout.NORTH);
        p.add(scroll, BorderLayout.CENTER);
        return p;
    }

    // ── Actions ────────────────────────────────────────────────────────────

    private void addAndStartServer() {
        String portText = portField.getText().trim();
        String dirText = dirField.getText().trim();
        if (portText.isEmpty() || dirText.isEmpty()) {
            Messages.showWarningDialog(project, "请填写端口和资源目录", "提示");
            return;
        }
        int port;
        try {
            port = Integer.parseInt(portText);
        } catch (NumberFormatException e) {
            Messages.showWarningDialog(project, "端口必须是数字", "提示");
            return;
        }
        if (port < 1 || port > 65535) {
            Messages.showWarningDialog(project, "端口范围 1-65535", "提示");
            return;
        }
        File dir = new File(dirText);
        if (!dir.isDirectory()) {
            Messages.showWarningDialog(project, "目录不存在: " + dirText, "提示");
            return;
        }
        for (ServerEntry entry : tableModel.entries) {
            if (entry.server.getPort() == port) {
                Messages.showWarningDialog(project, "端口 " + port + " 已被列表中其它服务占用", "提示");
                return;
            }
        }

        StaticFileServer server = new StaticFileServer(port, dir, this::log);
        ServerEntry entry = new ServerEntry(server);
        tableModel.add(entry);
        try {
            server.start();
            entry.refreshAddresses();
            tableModel.fireRowChanged(entry);
            persistServers();
        } catch (IOException ex) {
            tableModel.remove(entry);
            log("ERR", "启动失败 (端口 " + port + "): " + ex.getMessage());
            Messages.showErrorDialog(project, "启动失败: " + ex.getMessage(), "错误");
        }
    }

    private void startSelected() {
        ServerEntry e = selectedEntry();
        if (e == null) return;
        if (e.server.isRunning()) {
            log("INFO", "服务已在运行 (端口 " + e.server.getPort() + ")");
            return;
        }
        try {
            e.server.start();
            e.refreshAddresses();
            tableModel.fireRowChanged(e);
        } catch (IOException ex) {
            log("ERR", "启动失败: " + ex.getMessage());
            Messages.showErrorDialog(project, "启动失败: " + ex.getMessage(), "错误");
        }
    }

    private void stopSelected() {
        ServerEntry e = selectedEntry();
        if (e == null) return;
        e.server.stop();
        tableModel.fireRowChanged(e);
    }

    private void removeSelected() {
        ServerEntry e = selectedEntry();
        if (e == null) return;
        if (e.server.isRunning()) e.server.stop();
        tableModel.remove(e);
        persistServers();
    }

    private void openInBrowser() {
        ServerEntry e = selectedEntry();
        if (e == null) return;
        if (!e.server.isRunning()) {
            Messages.showWarningDialog(project, "服务未启动", "提示");
            return;
        }
        try {
            Desktop.getDesktop().browse(URI.create("http://localhost:" + e.server.getPort() + "/"));
        } catch (Exception ex) {
            log("ERR", "打开浏览器失败: " + ex.getMessage());
        }
    }

    private void openDirectory() {
        ServerEntry e = selectedEntry();
        if (e == null) return;
        try {
            Desktop.getDesktop().open(e.server.getRootDir());
        } catch (Exception ex) {
            log("ERR", "打开目录失败: " + ex.getMessage());
        }
    }

    private ServerEntry selectedEntry() {
        int row = serverTable.getSelectedRow();
        if (row < 0 || row >= tableModel.entries.size()) return null;
        return tableModel.entries.get(row);
    }

    public void stopAllServers() {
        for (ServerEntry e : new ArrayList<>(tableModel.entries)) {
            if (e.server.isRunning()) e.server.stop();
        }
    }

    // ── Logging ────────────────────────────────────────────────────────────
    private void log(String level, String msg) {
        Runnable r = () -> {
            String line = "[" + logTime.format(new Date()) + "][" + level + "] " + msg + "\n";
            logArea.append(line);
            logArea.setCaretPosition(logArea.getDocument().getLength());
        };
        if (SwingUtilities.isEventDispatchThread()) r.run();
        else ApplicationManager.getApplication().invokeLater(r);
    }

    // ── Network helpers ────────────────────────────────────────────────────
    private static List<String> localIPv4() {
        TreeSet<String> ips = new TreeSet<>();
        try {
            Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
            while (ifs != null && ifs.hasMoreElements()) {
                NetworkInterface ni = ifs.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    String ip = a.getHostAddress();
                    if (a.isLoopbackAddress()) continue;
                    if (ip.contains(":")) continue; // skip IPv6
                    ips.add(ip);
                }
            }
        } catch (Exception ignore) {}
        return new ArrayList<>(ips);
    }

    // ── Inner types ────────────────────────────────────────────────────────

    static class ServerEntry {
        final StaticFileServer server;
        String addressesText = "";

        ServerEntry(StaticFileServer server) {
            this.server = server;
            refreshAddresses();
        }

        void refreshAddresses() {
            StringBuilder sb = new StringBuilder("http://localhost:").append(server.getPort()).append("/");
            for (String ip : localIPv4()) {
                sb.append("  ").append("http://").append(ip).append(":").append(server.getPort()).append("/");
            }
            addressesText = sb.toString();
        }
    }

    static class ServerTableModel extends AbstractTableModel {
        private final String[] cols = {"状态", "端口", "目录", "地址（双击复制首项）"};
        final List<ServerEntry> entries = new ArrayList<>();

        @Override public int getRowCount() { return entries.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int c) { return cols[c]; }
        @Override public boolean isCellEditable(int r, int c) { return false; }

        @Override
        public Object getValueAt(int row, int col) {
            ServerEntry e = entries.get(row);
            return switch (col) {
                case 0 -> e.server.isRunning() ? "运行中" : "已停止";
                case 1 -> e.server.getPort();
                case 2 -> e.server.getRootDir().getAbsolutePath();
                case 3 -> "http://localhost:" + e.server.getPort() + "/";
                default -> "";
            };
        }

        void add(ServerEntry e) {
            entries.add(e);
            int row = entries.size() - 1;
            fireTableRowsInserted(row, row);
        }

        void remove(ServerEntry e) {
            int idx = entries.indexOf(e);
            if (idx >= 0) {
                entries.remove(idx);
                fireTableRowsDeleted(idx, idx);
            }
        }

        void fireRowChanged(ServerEntry e) {
            int idx = entries.indexOf(e);
            if (idx >= 0) fireTableRowsUpdated(idx, idx);
        }
    }

    /** 状态列：左侧画一个圆点表示运行/停止。 */
    static class StatusRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                        boolean hasFocus, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            boolean running = "运行中".equals(value);
            label.setIcon(new DotIcon(running
                    ? new JBColor(new Color(46, 160, 67), new Color(86, 211, 100))
                    : new JBColor(Color.GRAY, Color.GRAY)));
            label.setIconTextGap(JBUI.scale(6));
            return label;
        }
    }

    static class DotIcon implements Icon {
        private final Color color;
        DotIcon(Color color) { this.color = color; }
        @Override public int getIconWidth() { return JBUI.scale(10); }
        @Override public int getIconHeight() { return JBUI.scale(10); }
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.fillOval(x, y, getIconWidth(), getIconHeight());
            g2.dispose();
        }
    }
}
