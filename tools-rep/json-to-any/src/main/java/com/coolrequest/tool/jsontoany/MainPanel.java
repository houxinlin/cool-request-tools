package com.coolrequest.tool.jsontoany;

import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.json.JsonFileType;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.JBSplitter;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

public class MainPanel extends JPanel {

    private final EditorTextField inputField;
    private final JPanel outputWrapper;          // holds the current EditorTextField
    private EditorTextField outputField;         // recreated on format change

    private final JComboBox<JsonConverter.Format> formatBox;
    private final JLabel statusLabel;

    // SQL-specific controls
    private final JPanel sqlPanel;
    private final JRadioButton insertBtn;
    private final JRadioButton updateBtn;
    private final JRadioButton deleteBtn;
    private final JPanel wherePanel;
    private final JPanel whereCheckBoxPanel;
    private List<String> currentJsonKeys = Collections.emptyList();

    private final JsonConverter converter = new JsonConverter();

    public MainPanel() {
        setLayout(new BorderLayout(0, 0));
        setBorder(new EmptyBorder(8, 8, 8, 8));

        // ── Input: IDEA EditorTextField with JSON highlighting ───────────
        Document inputDoc = EditorFactory.getInstance().createDocument("");
        inputField = new EditorTextField(
                inputDoc,
                ProjectManager.getInstance().getDefaultProject(),
                JsonFileType.INSTANCE,
                false,  // editable
                false   // not one-line
        );
        inputField.addSettingsProvider(editor -> {
            EditorSettings settings = editor.getSettings();
            settings.setLineNumbersShown(true);
            settings.setFoldingOutlineShown(true);
            settings.setAutoCodeFoldingEnabled(true);
            settings.setLineMarkerAreaShown(true);
            editor.setHorizontalScrollbarVisible(true);
            editor.setVerticalScrollbarVisible(true);
        });

        // ── Initial output EditorTextField ───────────────────────────────
        outputWrapper = new JPanel(new BorderLayout());
        outputWrapper.setBorder(null);
        outputField = createEditorField(PlainTextFileType.INSTANCE);
        outputWrapper.add(outputField, BorderLayout.CENTER);

        // ── SQL panel ────────────────────────────────────────────────────
        insertBtn = new JRadioButton("INSERT", true);
        updateBtn = new JRadioButton("UPDATE");
        deleteBtn = new JRadioButton("DELETE");
        ButtonGroup sqlModeGroup = new ButtonGroup();
        sqlModeGroup.add(insertBtn);
        sqlModeGroup.add(updateBtn);
        sqlModeGroup.add(deleteBtn);

        whereCheckBoxPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        wherePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        wherePanel.add(new JLabel("WHERE:"));
        wherePanel.add(whereCheckBoxPanel);
        wherePanel.setVisible(false);

        sqlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        sqlPanel.add(new JLabel("Mode:"));
        sqlPanel.add(insertBtn);
        sqlPanel.add(updateBtn);
        sqlPanel.add(deleteBtn);
        sqlPanel.add(wherePanel);
        sqlPanel.setVisible(false);

        insertBtn.addActionListener(e -> wherePanel.setVisible(false));
        updateBtn.addActionListener(e -> wherePanel.setVisible(true));
        deleteBtn.addActionListener(e -> wherePanel.setVisible(true));

        // ── Status label ──────────────────────────────────────────────────
        statusLabel = new JLabel(" ");
        statusLabel.setForeground(Color.GRAY);

        // ── Toolbar ───────────────────────────────────────────────────────
        JPanel toolbar = new JPanel();
        toolbar.setLayout(new BoxLayout(toolbar, BoxLayout.Y_AXIS));

        JPanel mainRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        mainRow.add(new JLabel("Format:"));
        formatBox = new JComboBox<>(JsonConverter.Format.values());
        formatBox.setPreferredSize(new Dimension(130, 28));
        formatBox.addActionListener(e -> onFormatChanged());
        mainRow.add(formatBox);

        JButton convertBtn = new JButton("Convert");
        convertBtn.addActionListener(this::onConvert);
        mainRow.add(convertBtn);

        JButton copyBtn = new JButton("Copy");
        copyBtn.addActionListener(e -> copyToClipboard());
        mainRow.add(copyBtn);

        JButton clearBtn = new JButton("Clear");
        clearBtn.addActionListener(e -> {
            inputField.setText("");
            replaceOutputField(PlainTextFileType.INSTANCE, "");
            currentJsonKeys = Collections.emptyList();
            rebuildWhereCheckboxes();
            setStatus(" ", Color.GRAY);
        });
        mainRow.add(clearBtn);
        mainRow.add(statusLabel);

        toolbar.add(mainRow);
        toolbar.add(sqlPanel);

        add(toolbar, BorderLayout.NORTH);

        // ── JBSplitter: vertical (top=input, bottom=output) ───────────────
        JBSplitter splitter = new JBSplitter(true, 0.45f);
        splitter.setFirstComponent(inputField);
        splitter.setSecondComponent(outputWrapper);
        splitter.setShowDividerControls(true);

        add(splitter, BorderLayout.CENTER);

        // Sync initial state (default format is SQL)
        onFormatChanged();
    }

    // ── EditorTextField factory ───────────────────────────────────────────

    private EditorTextField createEditorField(FileType fileType) {
        Document doc = EditorFactory.getInstance().createDocument("");
        EditorTextField field = new EditorTextField(
                doc,
                ProjectManager.getInstance().getDefaultProject(),
                fileType,
                false,  // editable
                false   // not one-line
        );
        field.addSettingsProvider(editor -> {
            EditorSettings settings = editor.getSettings();
            settings.setLineNumbersShown(true);
            settings.setFoldingOutlineShown(true);
            settings.setAutoCodeFoldingEnabled(true);
            settings.setLineMarkerAreaShown(true);
            editor.setHorizontalScrollbarVisible(true);
            editor.setVerticalScrollbarVisible(true);
        });
        return field;
    }

    /**
     * Tear down the old editor, build a fresh one, slot it into outputWrapper.
     */
    private void replaceOutputField(FileType fileType, String text) {
        outputWrapper.remove(outputField);

        Document doc = EditorFactory.getInstance().createDocument(text);
        outputField = new EditorTextField(
                doc,
                ProjectManager.getInstance().getDefaultProject(),
                fileType,
                false,  // editable
                false
        );
        outputField.addSettingsProvider(editor -> {
            EditorSettings settings = editor.getSettings();
            settings.setLineNumbersShown(true);
            settings.setFoldingOutlineShown(true);
            settings.setAutoCodeFoldingEnabled(true);
            settings.setLineMarkerAreaShown(true);
            editor.setHorizontalScrollbarVisible(true);
            editor.setVerticalScrollbarVisible(true);
        });

        outputWrapper.add(outputField, BorderLayout.CENTER);
        outputWrapper.revalidate();
        outputWrapper.repaint();
    }

    // ── Format change ────────────────────────────────────────────────────

    private void onFormatChanged() {
        JsonConverter.Format fmt = (JsonConverter.Format) formatBox.getSelectedItem();
        sqlPanel.setVisible(fmt == JsonConverter.Format.SQL);
        // Recreate editor with new language, then auto-convert if input is not empty
        replaceOutputField(getFileTypeFor(fmt), "");
        if (!inputField.getText().trim().isEmpty()) {
            onConvert(null);
        }
    }

    private FileType getFileTypeFor(JsonConverter.Format fmt) {
        if (fmt == null) return PlainTextFileType.INSTANCE;
        switch (fmt) {
            case HTML:
                return HtmlFileType.INSTANCE;
            case XML:
                return XmlFileType.INSTANCE;
            default:
                return PlainTextFileType.INSTANCE;
        }
    }

    // ── Convert ──────────────────────────────────────────────────────────

    private void onConvert(ActionEvent e) {
        String json = inputField.getText().trim();
        if (json.isEmpty()) {
            setStatus("Input is empty.", Color.ORANGE.darker());
            return;
        }
        JsonConverter.Format fmt = (JsonConverter.Format) formatBox.getSelectedItem();

        currentJsonKeys = parseKeys(json);
        rebuildWhereCheckboxes();

        JsonConverter.SqlMode sqlMode = updateBtn.isSelected() ? JsonConverter.SqlMode.UPDATE
                : deleteBtn.isSelected() ? JsonConverter.SqlMode.DELETE
                : JsonConverter.SqlMode.INSERT;
        List<String> whereFields = getSelectedWhereFields();

        try {
            String result = converter.convert(json, fmt, sqlMode, whereFields);
            replaceOutputField(getFileTypeFor(fmt), result);
            setStatus("OK", new Color(0, 150, 0));
        } catch (Exception ex) {
            replaceOutputField(PlainTextFileType.INSTANCE, ex.getMessage());
            setStatus("Error", Color.RED);
        }
    }

    // ── WHERE helpers ────────────────────────────────────────────────────

    private void rebuildWhereCheckboxes() {
        whereCheckBoxPanel.removeAll();
        for (String key : currentJsonKeys) {
            whereCheckBoxPanel.add(new JCheckBox(key));
        }
        whereCheckBoxPanel.revalidate();
        whereCheckBoxPanel.repaint();
    }

    private List<String> getSelectedWhereFields() {
        List<String> selected = new ArrayList<>();
        for (Component c : whereCheckBoxPanel.getComponents()) {
            if (c instanceof JCheckBox && ((JCheckBox) c).isSelected()) {
                selected.add(((JCheckBox) c).getText());
            }
        }
        return selected;
    }

    private List<String> parseKeys(String jsonText) {
        try {
            org.json.JSONArray arr;
            if (jsonText.startsWith("[")) {
                arr = new org.json.JSONArray(jsonText);
            } else {
                arr = new org.json.JSONArray();
                arr.put(new org.json.JSONObject(jsonText));
            }
            LinkedHashSet<String> keys = new LinkedHashSet<>();
            for (int i = 0; i < arr.length(); i++) {
                try {
                    keys.addAll(arr.getJSONObject(i).keySet());
                } catch (Exception ignored) {
                }
            }
            return new ArrayList<>(keys);
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    // ── Utilities ────────────────────────────────────────────────────────

    private void copyToClipboard() {
        String text = outputField.getText();
        if (!text.isEmpty()) {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(text), null);
            setStatus("Copied!", new Color(0, 100, 200));
        }
    }

    private void setStatus(String msg, Color color) {
        statusLabel.setText(msg);
        statusLabel.setForeground(color);
    }
}
