package dev.coolrequest.tool.codestatistics;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.impl.JBEditorTabs;
import com.intellij.util.ui.JBUI;
import dev.coolrequest.tool.CoolToolPanel;
import dev.coolrequest.tool.ToolPanelFactory;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CodeStatisticsToolFactory implements ToolPanelFactory {


    @Override
    public CoolToolPanel createToolPanel() {
        return new CoolToolPanel() {
            private Project project;

            private CodeStatisticsPanel panel;
            private Disposable disposable;

            @Override
            public JPanel createPanel() {
                disposable = Disposer.newDisposable("CodeStatisticsTool");
                panel = new CodeStatisticsPanel(project, disposable);
                return panel;
            }

            @Override
            public void showTool() {
            }

            @Override
            public void closeTool() {
                if (disposable != null) {
                    Disposer.dispose(disposable);
                    disposable = null;
                }
            }
        };
    }

    public static class FileLineInfo {
        final String name;
        final String path;
        final long lines;

        FileLineInfo(String name, String path, long lines) {
            this.name = name;
            this.path = path;
            this.lines = lines;
        }
    }

    public static class CodeStatisticsPanel extends JPanel {

        private final Project project;
        private final Disposable parentDisposable;
        private final JBEditorTabs tabs;
        private final JButton refreshButton;
        private final JBLabel statusLabel;
        private final Map<String, List<FileLineInfo>> dataByExt = new HashMap<>();

        public CodeStatisticsPanel(Project project, Disposable parentDisposable) {
            this.project = project;
            this.parentDisposable = parentDisposable;
            setLayout(new BorderLayout());

            JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
            toolbar.setBorder(JBUI.Borders.empty(4, 6));

            refreshButton = new JButton("Refresh", AllIcons.Actions.Refresh);
            refreshButton.addActionListener(e -> scan());

            statusLabel = new JBLabel(" ");

            toolbar.add(refreshButton);
            toolbar.add(statusLabel);

            tabs = new JBEditorTabs(project, parentDisposable);

            add(toolbar, BorderLayout.NORTH);
            add(tabs.getComponent(), BorderLayout.CENTER);

            if (project != null) {
                scan();
            } else {
                statusLabel.setText("No project available.");
            }
        }

        private void scan() {
            if (project == null) return;
            refreshButton.setEnabled(false);
            statusLabel.setText("Scanning...");
            tabs.removeAllTabs();
            dataByExt.clear();

            ProgressManager.getInstance().run(new Task.Backgroundable(project, "Scanning code statistics", true) {
                @Override
                public void run(ProgressIndicator indicator) {
                    Map<String, List<FileLineInfo>> result = new HashMap<>();
                    ReadAction.run(() -> {
                        ProjectFileIndex index = ProjectFileIndex.getInstance(project);
                        index.iterateContent(file -> {
                            if (indicator.isCanceled()) return false;
                            if (file.isDirectory()) return true;
                            FileType type = file.getFileType();
                            if (type.isBinary()) return true;
                            String ext = file.getExtension();
                            if (ext == null || ext.isEmpty()) {
                                ext = file.getName();
                            }
                            String tabName = "." + ext;
                            long lines = countLines(file);
                            if (lines < 0) return true;
                            FileLineInfo info = new FileLineInfo(file.getName(), file.getPath(), lines);
                            result.computeIfAbsent(tabName, k -> new ArrayList<>()).add(info);
                            indicator.setText2(file.getPath());
                            return true;
                        });
                    });

                    ApplicationManager.getApplication().invokeLater(() -> {
                        dataByExt.putAll(result);
                        rebuildTabs();
                        long totalFiles = result.values().stream().mapToLong(List::size).sum();
                        long totalLines = result.values().stream()
                                .flatMap(List::stream).mapToLong(i -> i.lines).sum();
                        statusLabel.setText(String.format(
                                "  %d types, %d files, %d lines", result.size(), totalFiles, totalLines));
                        refreshButton.setEnabled(true);
                    });
                }

                @Override
                public void onCancel() {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        statusLabel.setText("Cancelled");
                        refreshButton.setEnabled(true);
                    });
                }
            });
        }

        private void openFile(String path) {
            if (project == null || path == null) return;
            VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
            if (vf == null || !vf.isValid()) return;
            new OpenFileDescriptor(project, vf).navigate(true);
        }

        private static long countLines(VirtualFile file) {
            try {
                byte[] bytes = file.contentsToByteArray();
                if (bytes.length == 0) return 0;
                long count = 1;
                for (byte b : bytes) {
                    if (b == '\n') count++;
                }
                if (bytes[bytes.length - 1] == '\n') count--;
                return count;
            } catch (IOException e) {
                return -1;
            }
        }

        private void rebuildTabs() {
            tabs.removeAllTabs();

            List<Map.Entry<String, Long>> typeTotals = new ArrayList<>();
            for (Map.Entry<String, List<FileLineInfo>> e : dataByExt.entrySet()) {
                long total = e.getValue().stream().mapToLong(i -> i.lines).sum();
                typeTotals.add(new java.util.AbstractMap.SimpleEntry<>(e.getKey(), total));
            }
            typeTotals.sort(Map.Entry.<String, Long>comparingByValue().reversed());
            List<Map.Entry<String, Long>> top10 =
                    typeTotals.subList(0, Math.min(10, typeTotals.size()));
            tabs.addTab(new TabInfo(new TopFilesChartPanel(top10)).setText("Top 10"));

            List<String> tabNames = new ArrayList<>(dataByExt.keySet());
            tabNames.sort(Comparator.comparingLong(
                    (String n) -> dataByExt.get(n).stream().mapToLong(i -> i.lines).sum()).reversed());

            for (String tabName : tabNames) {
                List<FileLineInfo> list = dataByExt.get(tabName);
                long total = list.stream().mapToLong(i -> i.lines).sum();
                String summary = String.format("%s — %d files, %d lines",
                        tabName, list.size(), total);
                tabs.addTab(new TabInfo(buildTablePanel(list, summary)).setText(tabName));
            }
        }

        private JPanel buildTablePanel(List<FileLineInfo> list, String summary) {
            DefaultTableModel model = new DefaultTableModel(
                    new Object[]{"File", "Lines", "Path"}, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }

                @Override
                public Class<?> getColumnClass(int columnIndex) {
                    if (columnIndex == 1) return Long.class;
                    return String.class;
                }
            };
            for (FileLineInfo info : list) {
                model.addRow(new Object[]{info.name, info.lines, info.path});
            }
            JBTable table = new JBTable(model);
            TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
            table.setRowSorter(sorter);
            sorter.setSortKeys(java.util.Collections.singletonList(
                    new RowSorter.SortKey(1, SortOrder.DESCENDING)));
            sorter.sort();

            table.getColumnModel().getColumn(0).setPreferredWidth(220);
            table.getColumnModel().getColumn(1).setPreferredWidth(80);
            table.getColumnModel().getColumn(2).setPreferredWidth(500);

            table.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    if (e.getClickCount() != 2 || e.getButton() != java.awt.event.MouseEvent.BUTTON1) return;
                    int viewRow = table.rowAtPoint(e.getPoint());
                    if (viewRow < 0) return;
                    int modelRow = table.convertRowIndexToModel(viewRow);
                    Object pathVal = model.getValueAt(modelRow, 2);
                    if (pathVal != null) {
                        openFile(pathVal.toString());
                    }
                }
            });

            JBLabel header = new JBLabel(summary);
            header.setBorder(JBUI.Borders.empty(6, 8));
            header.setFont(header.getFont().deriveFont(Font.BOLD));

            JPanel wrapper = new JPanel(new BorderLayout());
            wrapper.add(header, BorderLayout.NORTH);
            wrapper.add(new JBScrollPane(table), BorderLayout.CENTER);
            return wrapper;
        }
    }

    public static class TopFilesChartPanel extends JPanel {
        private final List<Map.Entry<String, Long>> items;

        public TopFilesChartPanel(List<Map.Entry<String, Long>> items) {
            this.items = items;
            setBorder(JBUI.Borders.empty(16, 20));
            setBackground(com.intellij.ui.JBColor.background());
        }

        @Override
        public Dimension getPreferredSize() {
            int rows = Math.max(items.size(), 1);
            return new Dimension(600, 32 + rows * 32);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (items.isEmpty()) {
                g.setColor(com.intellij.ui.JBColor.foreground());
                g.drawString("No data to display.", 20, 30);
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                Font labelFont = getFont().deriveFont(12f);

                int padX = JBUI.scale(20);
                int padY = JBUI.scale(12);
                int rowH = JBUI.scale(28);
                int labelW = JBUI.scale(140);
                int valueW = JBUI.scale(110);

                int width = getWidth();
                int barAreaX = padX + labelW + JBUI.scale(8);
                int barAreaW = Math.max(JBUI.scale(60),
                        width - barAreaX - valueW - padX);

                long max = items.get(0).getValue();
                if (max <= 0) max = 1;

                g2.setFont(labelFont);
                FontMetrics fm = g2.getFontMetrics();
                int y = padY;
                java.awt.Color barColor = new com.intellij.ui.JBColor(
                        new java.awt.Color(0x4A90E2), new java.awt.Color(0x5C9DEA));

                for (Map.Entry<String, Long> info : items) {
                    int textY = y + (rowH + fm.getAscent()) / 2 - JBUI.scale(2);

                    g2.setColor(com.intellij.ui.JBColor.foreground());
                    String name = info.getKey();
                    if (fm.stringWidth(name) > labelW) {
                        while (name.length() > 4 && fm.stringWidth(name + "...") > labelW) {
                            name = name.substring(0, name.length() - 1);
                        }
                        name = name + "...";
                    }
                    g2.drawString(name, padX, textY);

                    long lines = info.getValue();
                    int barW = (int) Math.round(barAreaW * (lines / (double) max));
                    if (barW < 2) barW = 2;
                    int barY = y + JBUI.scale(4);
                    int barH = rowH - JBUI.scale(8);

                    g2.setColor(new com.intellij.ui.JBColor(
                            new java.awt.Color(0xE8EEF6), new java.awt.Color(0x3C3F41)));
                    g2.fillRoundRect(barAreaX, barY, barAreaW, barH,
                            JBUI.scale(6), JBUI.scale(6));

                    g2.setColor(barColor);
                    g2.fillRoundRect(barAreaX, barY, barW, barH,
                            JBUI.scale(6), JBUI.scale(6));

                    g2.setColor(com.intellij.ui.JBColor.foreground());
                    g2.drawString(lines + " lines",
                            barAreaX + barAreaW + JBUI.scale(8), textY);

                    y += rowH;
                }
            } finally {
                g2.dispose();
            }
        }
    }
}
