package dev.coolrequest.tool.wallhaven;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class WallhavenPanel extends JBPanel<WallhavenPanel> {

    private static final Logger LOG = Logger.getInstance(WallhavenPanel.class);

    private final Project project;
    private final WallhavenClient client = new WallhavenClient();
    private final ExecutorService thumbExecutor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "wallhaven-thumb");
        t.setDaemon(true);
        return t;
    });
    private final ConcurrentHashMap<String, ImageIcon> thumbCache = new ConcurrentHashMap<>();
    private final AtomicInteger requestSeq = new AtomicInteger();

    // search controls
    private final JBTextField queryField = new JBTextField();
    private final JCheckBox catGeneral = new JCheckBox("General", true);
    private final JCheckBox catAnime = new JCheckBox("Anime", true);
    private final JCheckBox catPeople = new JCheckBox("People", true);
    private final JCheckBox puritySfw = new JCheckBox("SFW", true);
    private final JCheckBox puritySketchy = new JCheckBox("Sketchy", false);
    private final JCheckBox purityNsfw = new JCheckBox("NSFW", false);
    private final ComboBox<String> atleastCombo = new ComboBox<>(
            new String[]{"", "1280x720", "1920x1080", "2560x1440", "3840x2160"});

    private final JBLabel statusLabel = new JBLabel(" ");
    private final JButton prevBtn = iconButton("上一页", AllIcons.Actions.Back, "上一页");
    private final JButton nextBtn = iconButton("下一页", AllIcons.Actions.Forward, "下一页");
    private final JBLabel pageLabel = new JBLabel("第 1 / 1 页");

    private static final int CELL_W = 240;
    private static final int CELL_H = 180;
    private static final int THUMB_W = 230;
    private static final int THUMB_H = 130;

    private final JPanel resultsPanel = new JPanel(new AdaptiveGridLayout(CELL_W, CELL_H, 8, 8));

    private int currentPage = 1;
    private int lastPage = 1;
    private String currentSorting = "toplist";

    public WallhavenPanel(Project project) {
        super(new BorderLayout());
        this.project = project;
        setBorder(JBUI.Borders.empty(8));
        add(buildToolbar(), BorderLayout.NORTH);

        JBScrollPane scroll = new JBScrollPane(resultsPanel,
                JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getViewport().addChangeListener(e -> resultsPanel.revalidate());
        scroll.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) {
                resultsPanel.revalidate();
                resultsPanel.repaint();
            }
        });
        add(scroll, BorderLayout.CENTER);
        add(buildBottom(), BorderLayout.SOUTH);

        prevBtn.addActionListener(e -> { if (currentPage > 1) { currentPage--; doSearch(); } });
        nextBtn.addActionListener(e -> { if (currentPage < lastPage) { currentPage++; doSearch(); } });

        SwingUtilities.invokeLater(this::doSearch);
    }

    private static JButton iconButton(String text, Icon icon, String tooltip) {
        JButton b = new JButton(text, icon);
        b.setToolTipText(tooltip);
        b.setFocusPainted(false);
        b.setHorizontalTextPosition(SwingConstants.RIGHT);
        b.setIconTextGap(6);
        b.setMargin(JBUI.insets(4, 10));
        return b;
    }

    private JComponent buildToolbar() {
        JPanel root = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = JBUI.insets(3);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        c.gridy = 0; c.gridx = 0;
        root.add(new JBLabel("关键词:"), c);
        c.gridx = 1; c.weightx = 1; c.gridwidth = 4;
        queryField.getEmptyText().setText("支持 +tag、-tag、@user、id:123 ...");
        root.add(queryField, c);

        c.gridx = 5; c.weightx = 0; c.gridwidth = 1;
        JButton searchBtn = iconButton("搜索", AllIcons.Actions.Find, "搜索壁纸");
        searchBtn.addActionListener(e -> {
            currentPage = 1;
            currentSorting = queryField.getText().trim().isEmpty() ? "toplist" : "relevance";
            doSearch();
        });
        root.add(searchBtn, c);
        queryField.addActionListener(e -> {
            currentPage = 1;
            currentSorting = queryField.getText().trim().isEmpty() ? "toplist" : "relevance";
            doSearch();
        });

        c.gridx = 6;
        JButton clearBtn = iconButton("清除壁纸", AllIcons.Actions.GC, "清除当前 IDEA 壁纸（编辑器+IDE）");
        clearBtn.addActionListener(e -> WallpaperApplier.clear(WallpaperApplier.Target.BOTH));
        root.add(clearBtn, c);

        c.gridy = 1; c.gridx = 0;
        root.add(new JBLabel("分类:"), c);
        c.gridx = 1; c.gridwidth = 2;
        root.add(horizontal(catGeneral, catAnime, catPeople), c);
        c.gridx = 3; c.gridwidth = 1;
        root.add(new JBLabel("纯净度:"), c);
        c.gridx = 4; c.gridwidth = 1;
        root.add(horizontal(puritySfw, puritySketchy, purityNsfw), c);
        c.gridx = 5; c.gridwidth = 1;
        root.add(new JBLabel("最小分辨率:"), c);
        c.gridx = 6; c.gridwidth = 1;
        root.add(atleastCombo, c);

        return root;
    }

    private JPanel horizontal(JComponent... items) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        for (JComponent it : items) p.add(it);
        return p;
    }

    private JComponent buildBottom() {
        JPanel p = new JPanel(new BorderLayout());
        statusLabel.setForeground(JBColor.GRAY);
        p.add(statusLabel, BorderLayout.WEST);
        JPanel pager = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        pager.add(prevBtn);
        pager.add(pageLabel);
        pager.add(nextBtn);
        p.add(pager, BorderLayout.EAST);
        return p;
    }

    private String catString() {
        return (catGeneral.isSelected() ? "1" : "0")
                + (catAnime.isSelected() ? "1" : "0")
                + (catPeople.isSelected() ? "1" : "0");
    }

    private String purityString() {
        return (puritySfw.isSelected() ? "1" : "0")
                + (puritySketchy.isSelected() ? "1" : "0")
                + (purityNsfw.isSelected() ? "1" : "0");
    }

    private void doSearch() {
        WallhavenClient.SearchQuery q = new WallhavenClient.SearchQuery();
        q.q = queryField.getText().trim();
        q.categories = catString();
        if ("000".equals(q.categories)) q.categories = "111";
        q.purity = purityString();
        if ("000".equals(q.purity)) q.purity = "100";
        q.sorting = currentSorting;
        q.atleast = (String) atleastCombo.getSelectedItem();
        q.page = currentPage;

        statusLabel.setText("搜索中...");
        resultsPanel.removeAll();
        resultsPanel.revalidate();
        resultsPanel.repaint();

        int seq = requestSeq.incrementAndGet();
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Wallhaven 搜索", true) {
            @Override
            public void run(ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                try {
                    WallhavenClient.SearchResult r = client.search(q);
                    if (seq != requestSeq.get()) return;
                    SwingUtilities.invokeLater(() -> renderResults(r));
                } catch (Exception ex) {
                    LOG.warn("search failed", ex);
                    SwingUtilities.invokeLater(() -> statusLabel.setText("搜索失败: " + ex.getMessage()));
                }
            }
        });
    }

    private void renderResults(WallhavenClient.SearchResult r) {
        currentPage = r.currentPage;
        lastPage = r.lastPage;
        pageLabel.setText("第 " + currentPage + " / " + lastPage + " 页");
        prevBtn.setEnabled(currentPage > 1);
        nextBtn.setEnabled(currentPage < lastPage);
        statusLabel.setText("共 " + r.data.size() + " 张");
        resultsPanel.removeAll();
        for (Wallpaper w : r.data) {
            resultsPanel.add(buildThumbCell(w));
        }
        if (r.data.isEmpty()) {
            JBLabel empty = new JBLabel("没有找到结果", SwingConstants.CENTER);
            empty.setForeground(JBColor.GRAY);
            resultsPanel.add(empty);
        }
        resultsPanel.revalidate();
        resultsPanel.repaint();
    }

    private JComponent buildThumbCell(Wallpaper w) {
        JBPanel<?> cell = new JBPanel<>(new BorderLayout(0, 4));
        Border line = BorderFactory.createLineBorder(JBColor.border(), 1, true);
        cell.setBorder(BorderFactory.createCompoundBorder(line, JBUI.Borders.empty(4)));
        cell.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JBLabel image = new JBLabel("加载中...", SwingConstants.CENTER);
        image.setPreferredSize(new Dimension(THUMB_W, THUMB_H));
        image.setForeground(JBColor.GRAY);
        image.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        image.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                new PreviewDialog(project, w, client).show();
            }
        });
        cell.add(image, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(4, 0));
        bottom.setOpaque(false);
        JBLabel meta = new JBLabel(w.resolution + " · " + w.category);
        meta.setForeground(JBColor.GRAY);
        bottom.add(meta, BorderLayout.CENTER);

        JButton applyBtn = new JButton(AllIcons.Actions.Install);
        applyBtn.setToolTipText("一键设为 IDEA 壁纸（默认 15% / 缩放铺满 / 居中）");
        applyBtn.setBorder(JBUI.Borders.empty(2));
        applyBtn.setFocusPainted(false);
        applyBtn.setContentAreaFilled(false);
        applyBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        applyBtn.setHorizontalAlignment(SwingConstants.RIGHT);
        applyBtn.addActionListener(e -> applyAsWallpaper(w, applyBtn));

        JPanel rightWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        rightWrap.setOpaque(false);
        rightWrap.add(applyBtn);
        bottom.add(rightWrap, BorderLayout.EAST);
        cell.add(bottom, BorderLayout.SOUTH);

        loadThumbAsync(w.pickThumb(), image);
        return cell;
    }

    private void applyAsWallpaper(Wallpaper w, JButton btn) {
        Icon original = btn.getIcon();
        btn.setIcon(new com.intellij.ui.AnimatedIcon.Default());
        btn.setEnabled(false);
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "下载并设置壁纸", true) {
            @Override
            public void run(ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                try {
                    byte[] bytes = client.download(w.path);
                    Path file = WallpaperApplier.saveImage(w, bytes);
                    WallpaperApplier.apply(file, WallpaperApplier.Target.BOTH, 15,
                            WallpaperApplier.Fill.SCALE, WallpaperApplier.Anchor.CENTER);
                    ApplicationManager.getApplication().invokeLater(() -> {
                        statusLabel.setText("已设为壁纸：" + file.getFileName());
                        btn.setIcon(AllIcons.Actions.Checked);
                        Timer t = new Timer(1500, ev -> {
                            btn.setIcon(original);
                            btn.setEnabled(true);
                        });
                        t.setRepeats(false);
                        t.start();
                    });
                } catch (Exception ex) {
                    LOG.warn("one-click apply failed", ex);
                    ApplicationManager.getApplication().invokeLater(() -> {
                        btn.setIcon(original);
                        btn.setEnabled(true);
                        Messages.showErrorDialog(project, "设置失败: " + ex.getMessage(), "Wallhaven");
                    });
                }
            }
        });
    }

    private void loadThumbAsync(String url, JBLabel target) {
        if (url == null) {
            target.setText("无缩略图");
            return;
        }
        ImageIcon cached = thumbCache.get(url);
        if (cached != null) {
            target.setIcon(cached);
            target.setText(null);
            return;
        }
        thumbExecutor.submit(() -> {
            try {
                byte[] bytes = client.download(url);
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
                if (img == null) return;
                Image scaled = img.getScaledInstance(THUMB_W, THUMB_H, Image.SCALE_SMOOTH);
                ImageIcon icon = new ImageIcon(scaled);
                thumbCache.put(url, icon);
                ApplicationManager.getApplication().invokeLater(() -> {
                    target.setIcon(icon);
                    target.setText(null);
                });
            } catch (Exception ex) {
                LOG.debug("thumb load failed: " + url, ex);
                ApplicationManager.getApplication().invokeLater(() -> target.setText("加载失败"));
            }
        });
    }

    public void dispose() {
        thumbExecutor.shutdownNow();
        thumbCache.clear();
    }
}
