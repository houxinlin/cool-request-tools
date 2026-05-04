package dev.coolrequest.tool.json;

import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.icons.AllIcons;
import com.intellij.json.JsonFileType;
import com.intellij.json.psi.JsonArray;
import com.intellij.json.psi.JsonBooleanLiteral;
import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonNullLiteral;
import com.intellij.json.psi.JsonNumberLiteral;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.json.psi.JsonValue;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.ui.JBColor;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.SimpleTree;
import com.intellij.util.Alarm;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.Enumeration;

public class JsonMainPanel extends JPanel {

    private final Project project;
    private final Editor editor;
    private final Document document;
    private final JLabel statusLabel;

    private final JBSplitter splitter;
    private final JPanel treeContainer;
    private final SimpleTree tree;
    private final DefaultTreeModel treeModel;
    private boolean treeVisible = false;
    private final Alarm treeUpdateAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);

    public JsonMainPanel(Project project) {
        this.project = project != null ? project : ProjectManager.getInstance().getDefaultProject();

        setLayout(new BorderLayout(0, 0));
        setBorder(new EmptyBorder(8, 8, 8, 8));

        // PSI-backed JSON document for folding/highlighting/indents.
        this.document = ReadAction.compute(() -> {
            PsiFile psiFile = PsiFileFactory.getInstance(this.project)
                    .createFileFromText("snippet.json", JsonFileType.INSTANCE, "", 0, true, false);
            return PsiDocumentManager.getInstance(this.project).getDocument(psiFile);
        });

        Document doc = this.document != null
                ? this.document
                : EditorFactory.getInstance().createDocument("");

        EditorEx[] holder = new EditorEx[1];
        Runnable createEditor = () -> {
            EditorEx ex = (EditorEx) EditorFactory.getInstance()
                    .createEditor(doc, this.project, JsonFileType.INSTANCE, false);

            ex.setHighlighter(EditorHighlighterFactory.getInstance()
                    .createEditorHighlighter(this.project, JsonFileType.INSTANCE));

            EditorSettings settings = ex.getSettings();
            settings.setLineNumbersShown(true);
            settings.setFoldingOutlineShown(true);
            settings.setAutoCodeFoldingEnabled(true);
            settings.setLineMarkerAreaShown(true);
            settings.setIndentGuidesShown(true);
            settings.setAdditionalLinesCount(0);
            settings.setAdditionalColumnsCount(0);
            ex.setHorizontalScrollbarVisible(true);
            ex.setVerticalScrollbarVisible(true);

            CodeFoldingManager.getInstance(this.project).updateFoldRegions(ex);
            holder[0] = ex;
        };
        if (ApplicationManager.getApplication().isDispatchThread()) {
            createEditor.run();
        } else {
            ApplicationManager.getApplication().invokeAndWait(createEditor);
        }
        this.editor = holder[0];

        // Live-sync the tree when the document changes (debounced).
        doc.addDocumentListener(new DocumentListener() {
            @Override
            public void documentChanged(DocumentEvent event) {
                scheduleTreeRebuild();
            }
        });

        // Tree side
        treeModel = new DefaultTreeModel(new DefaultMutableTreeNode(new JsonNode("root", null, NodeKind.NULL)));
        tree = new SimpleTree(treeModel);
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.setOpaque(false);
        tree.setBackground(new Color(0, 0, 0, 0));
        tree.setCellRenderer(new JsonTreeCellRenderer());
        JBScrollPane treeScroll = new JBScrollPane(tree);
        treeScroll.setOpaque(false);
        treeScroll.getViewport().setOpaque(false);
        treeContainer = new JPanel(new BorderLayout());
        treeContainer.add(treeScroll, BorderLayout.CENTER);

        statusLabel = new JLabel(" ");
        statusLabel.setForeground(JBColor.GRAY);
        statusLabel.setBorder(new EmptyBorder(2, 8, 2, 8));

        DefaultActionGroup group = new DefaultActionGroup();
        group.add(new AnAction("Format", "Pretty-print JSON", AllIcons.Actions.PrettyPrint) {
            @Override
            public void actionPerformed(AnActionEvent e) {
                formatJson();
            }
        });
        group.add(new AnAction("Compress", "Strip whitespace", AllIcons.Actions.Collapseall) {
            @Override
            public void actionPerformed(AnActionEvent e) {
                compressJson();
            }
        });
        group.addSeparator();
        group.add(new ToggleAction("Tree View", "Show JSON as a tree", AllIcons.Actions.ShowAsTree) {
            @Override
            public boolean isSelected(AnActionEvent e) {
                return treeVisible;
            }

            @Override
            public void setSelected(AnActionEvent e, boolean state) {
                toggleTree(state);
            }
        });
        group.add(new AnAction("Expand All", "Expand all tree nodes", AllIcons.Actions.Expandall) {
            @Override
            public void actionPerformed(AnActionEvent e) {
                expandAll(true);
            }

            @Override
            public void update(AnActionEvent e) {
                e.getPresentation().setEnabled(treeVisible);
            }
        });
        group.add(new AnAction("Collapse All", "Collapse all tree nodes", AllIcons.Actions.Collapseall) {
            @Override
            public void actionPerformed(AnActionEvent e) {
                expandAll(false);
            }

            @Override
            public void update(AnActionEvent e) {
                e.getPresentation().setEnabled(treeVisible);
            }
        });
        group.addSeparator();
        group.add(new AnAction("Copy", "Copy to clipboard", AllIcons.Actions.Copy) {
            @Override
            public void actionPerformed(AnActionEvent e) {
                copyToClipboard();
            }
        });
        group.add(new AnAction("Clear", "Clear input", AllIcons.Actions.GC) {
            @Override
            public void actionPerformed(AnActionEvent e) {
                setEditorText("");
                rebuildTree();
                setStatus(" ", JBColor.GRAY);
            }
        });

        ActionToolbar actionToolbar = ActionManager.getInstance()
                .createActionToolbar("JsonTool", group, true);
        actionToolbar.setTargetComponent(this);

        splitter = new JBSplitter(false, 0.6f);
        splitter.setFirstComponent(this.editor.getComponent());
        splitter.setSecondComponent(null);
        splitter.setShowDividerControls(true);

        add(actionToolbar.getComponent(), BorderLayout.NORTH);
        add(splitter, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);
    }

    // ── Tree toggle ─────────────────────────────────────────────────────────

    private void scheduleTreeRebuild() {
        if (!treeVisible) return;
        treeUpdateAlarm.cancelAllRequests();
        treeUpdateAlarm.addRequest(this::rebuildTree, 250);
    }

    private void toggleTree(boolean show) {
        treeVisible = show;
        if (show) {
            rebuildTree();
            splitter.setSecondComponent(treeContainer);
            splitter.setProportion(0.55f);
        } else {
            splitter.setSecondComponent(null);
        }
        splitter.revalidate();
        splitter.repaint();
    }

    private void rebuildTree() {
        if (!treeVisible) return;
        String text = document.getText();
        if (text.trim().isEmpty()) {
            treeModel.setRoot(new DefaultMutableTreeNode(new JsonNode("(empty)", null, NodeKind.NULL)));
            return;
        }
        ReadAction.run(() -> {
            try {
                PsiFile psi = PsiFileFactory.getInstance(project)
                        .createFileFromText("preview.json", JsonFileType.INSTANCE, text, 0, false, false);
                if (!(psi instanceof JsonFile)) {
                    treeModel.setRoot(new DefaultMutableTreeNode(new JsonNode("Invalid JSON", null, NodeKind.NULL)));
                    return;
                }
                JsonValue top = ((JsonFile) psi).getTopLevelValue();
                DefaultMutableTreeNode root = buildNode("root", top);
                treeModel.setRoot(root);
                expandAll(true);
            } catch (Exception ex) {
                treeModel.setRoot(new DefaultMutableTreeNode(new JsonNode("Error: " + ex.getMessage(), null, NodeKind.NULL)));
            }
        });
    }

    private DefaultMutableTreeNode buildNode(String name, JsonValue value) {
        if (value instanceof JsonObject) {
            JsonObject obj = (JsonObject) value;
            int size = obj.getPropertyList().size();
            DefaultMutableTreeNode node = new DefaultMutableTreeNode(
                    new JsonNode(name, "{" + size + "}", NodeKind.OBJECT));
            for (JsonProperty prop : obj.getPropertyList()) {
                JsonValue v = prop.getValue();
                node.add(buildNode(prop.getName(), v));
            }
            return node;
        }
        if (value instanceof JsonArray) {
            JsonArray arr = (JsonArray) value;
            int size = arr.getValueList().size();
            DefaultMutableTreeNode node = new DefaultMutableTreeNode(
                    new JsonNode(name, "[" + size + "]", NodeKind.ARRAY));
            int i = 0;
            for (JsonValue v : arr.getValueList()) {
                node.add(buildNode("[" + i + "]", v));
                i++;
            }
            return node;
        }
        if (value instanceof JsonStringLiteral) {
            return new DefaultMutableTreeNode(
                    new JsonNode(name, "\"" + ((JsonStringLiteral) value).getValue() + "\"", NodeKind.STRING));
        }
        if (value instanceof JsonNumberLiteral) {
            return new DefaultMutableTreeNode(
                    new JsonNode(name, value.getText(), NodeKind.NUMBER));
        }
        if (value instanceof JsonBooleanLiteral) {
            return new DefaultMutableTreeNode(
                    new JsonNode(name, value.getText(), NodeKind.BOOLEAN));
        }
        if (value instanceof JsonNullLiteral) {
            return new DefaultMutableTreeNode(new JsonNode(name, "null", NodeKind.NULL));
        }
        String txt = value == null ? "null" : value.getText();
        return new DefaultMutableTreeNode(new JsonNode(name, txt, NodeKind.NULL));
    }

    private void expandAll(boolean expand) {
        TreeNode root = (TreeNode) tree.getModel().getRoot();
        if (root == null) return;
        expandRecursive(new javax.swing.tree.TreePath(root), expand);
    }

    private void expandRecursive(javax.swing.tree.TreePath path, boolean expand) {
        TreeNode node = (TreeNode) path.getLastPathComponent();
        Enumeration<?> children = node.children();
        while (children.hasMoreElements()) {
            TreeNode child = (TreeNode) children.nextElement();
            expandRecursive(path.pathByAddingChild(child), expand);
        }
        if (expand) tree.expandPath(path);
        else if (path.getParentPath() != null) tree.collapsePath(path);
    }

    // ── Existing JSON ops ──────────────────────────────────────────────────

    private void formatJson() {
        String text = document.getText();
        if (text.trim().isEmpty()) {
            setStatus("Input is empty.", JBColor.ORANGE);
            return;
        }
        WriteCommandAction.runWriteCommandAction(project, () -> {
            PsiDocumentManager.getInstance(project).commitDocument(document);
            PsiFile psi = PsiDocumentManager.getInstance(project).getPsiFile(document);
            if (psi != null) {
                CodeStyleManager.getInstance(project).reformat(psi);
            }
            CodeFoldingManager.getInstance(project).updateFoldRegions(editor);
            setStatus("Formatted", new JBColor(new Color(0, 150, 0), new Color(0, 200, 0)));
        });
        rebuildTree();
    }

    private void compressJson() {
        String text = document.getText();
        if (text.trim().isEmpty()) {
            setStatus("Input is empty.", JBColor.ORANGE);
            return;
        }
        try {
            String compact = stripJsonWhitespace(text);
            setEditorText(compact);
            setStatus("Compressed", new JBColor(new Color(0, 150, 0), new Color(0, 200, 0)));
            rebuildTree();
        } catch (Exception ex) {
            setStatus("Invalid JSON: " + ex.getMessage(), JBColor.RED);
        }
    }

    private void setEditorText(String text) {
        WriteCommandAction.runWriteCommandAction(project, () -> {
            document.setText(text);
            PsiDocumentManager.getInstance(project).commitDocument(document);
            CodeFoldingManager.getInstance(project).updateFoldRegions(editor);
        });
    }

    private String stripJsonWhitespace(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        boolean inString = false;
        boolean escape = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                sb.append(c);
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
            } else {
                if (c == '"') {
                    inString = true;
                    sb.append(c);
                } else if (!Character.isWhitespace(c)) {
                    sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    private void copyToClipboard() {
        String text = document.getText();
        if (text.isEmpty()) {
            setStatus("Nothing to copy.", JBColor.ORANGE);
            return;
        }
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(text), null);
        setStatus("Copied!", new JBColor(new Color(0, 100, 200), new Color(80, 160, 255)));
    }

    private void setStatus(String msg, Color color) {
        statusLabel.setText(msg);
        statusLabel.setForeground(color);
    }

    public void dispose() {
        treeUpdateAlarm.cancelAllRequests();
        if (editor != null && !editor.isDisposed()) {
            EditorFactory.getInstance().releaseEditor(editor);
        }
    }

    // ── Tree node model & renderer ─────────────────────────────────────────

    private enum NodeKind { OBJECT, ARRAY, STRING, NUMBER, BOOLEAN, NULL }

    private static final class JsonNode {
        final String name;
        final String value;
        final NodeKind kind;

        JsonNode(String name, String value, NodeKind kind) {
            this.name = name;
            this.value = value;
            this.kind = kind;
        }
    }

    private static final class JsonTreeCellRenderer extends DefaultTreeCellRenderer {
        private static final JBColor KEY_COLOR = new JBColor(new Color(0x871094), new Color(0xCF8E6D));
        private static final JBColor STRING_COLOR = new JBColor(new Color(0x067D17), new Color(0x6AAB73));
        private static final JBColor NUMBER_COLOR = new JBColor(new Color(0x1750EB), new Color(0x2AACB8));
        private static final JBColor BOOLEAN_COLOR = new JBColor(new Color(0x0033B3), new Color(0xCC7832));
        private static final JBColor NULL_COLOR = new JBColor(new Color(0x808080), new Color(0x808080));
        private static final JBColor BRACE_COLOR = new JBColor(new Color(0x4A86E8), new Color(0x6EA8FF));
        private static final JBColor BRACKET_COLOR = new JBColor(new Color(0x36A85B), new Color(0x6AAB73));
        private static final JBColor SIZE_COLOR = new JBColor(new Color(0x9E9E9E), new Color(0x808080));
        private static final JBColor SEP_COLOR = new JBColor(new Color(0x666666), new Color(0xA9B7C6));

        JsonTreeCellRenderer() {
            setOpaque(false);
            setBackgroundNonSelectionColor(new Color(0, 0, 0, 0));
            setBackgroundSelectionColor(new Color(0, 0, 0, 0));
            setBorderSelectionColor(null);
            setFont(UIUtil.getLabelFont());
            setIconTextGap(JBUI.scale(4));
        }

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
                                                      boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            setOpaque(false);
            Object userObj = (value instanceof DefaultMutableTreeNode)
                    ? ((DefaultMutableTreeNode) value).getUserObject() : null;
            if (!(userObj instanceof JsonNode)) return this;
            JsonNode n = (JsonNode) userObj;
            setIcon(iconFor(n.kind));
            setText(renderHtml(n));
            return this;
        }

        private Icon iconFor(NodeKind kind) {
            switch (kind) {
                case OBJECT:  return AllIcons.Json.Object;
                case ARRAY:   return AllIcons.Json.Array;
                case STRING:  return new GlyphIcon("\"", STRING_COLOR);
                case NUMBER:  return new GlyphIcon("#", NUMBER_COLOR);
                case BOOLEAN: return new GlyphIcon("?", BOOLEAN_COLOR);
                case NULL:
                default:      return new GlyphIcon("ø", NULL_COLOR);
            }
        }

        private String renderHtml(JsonNode n) {
            String key = "<span style='color:" + hex(KEY_COLOR) + ";font-weight:600'>"
                    + escape(n.name) + "</span>";
            if (n.value == null) return "<html>" + key + "</html>";
            JBColor c;
            switch (n.kind) {
                case STRING:    c = STRING_COLOR; break;
                case NUMBER:    c = NUMBER_COLOR; break;
                case BOOLEAN:   c = BOOLEAN_COLOR; break;
                case OBJECT:    c = SIZE_COLOR;   break;
                case ARRAY:     c = SIZE_COLOR;   break;
                default:        c = NULL_COLOR;
            }
            String sep = "<span style='color:" + hex(SEP_COLOR) + "'> : </span>";
            return "<html>" + key + sep + "<span style='color:" + hex(c) + "'>"
                    + escape(n.value) + "</span></html>";
        }

        private String hex(Color c) {
            return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
        }

        private String escape(String s) {
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }

    private static final class GlyphIcon implements Icon {
        private final String label;
        private final JBColor color;
        private final int size = JBUI.scale(16);

        GlyphIcon(String label, JBColor color) {
            this.label = label;
            this.color = color;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setColor(color);
                Font f = UIUtil.getLabelFont().deriveFont(Font.BOLD, JBUI.scale(12f));
                g2.setFont(f);
                FontMetrics fm = g2.getFontMetrics();
                int tw = fm.stringWidth(label);
                int tx = x + (size - tw) / 2;
                int ty = y + (size - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(label, tx, ty);
            } finally {
                g2.dispose();
            }
        }

        @Override
        public int getIconWidth() { return size; }

        @Override
        public int getIconHeight() { return size; }
    }
}
