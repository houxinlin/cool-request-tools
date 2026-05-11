package dev.coolrequest.tool.converter.ui;

import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import dev.coolrequest.tool.converter.model.Format;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class FormatChooser extends JPanel {
    private final String title;
    private final Map<String, List<Format>> grouped;
    private final List<String> categories;
    private Format current;
    private final Consumer<Format> onChange;
    private final JButton button;

    public FormatChooser(String title, List<Format> formats, Format initial, Consumer<Format> onChange) {
        this.title = title;
        this.current = initial;
        this.onChange = onChange;

        this.grouped = new LinkedHashMap<>();
        for (Format f : formats) {
            grouped.computeIfAbsent(f.getCategory(), k -> new ArrayList<>()).add(f);
        }
        this.categories = new ArrayList<>(grouped.keySet());

        setLayout(new BorderLayout());

        JBLabel label = new JBLabel(title);
        label.setBorder(JBUI.Borders.emptyRight(8));
        label.setForeground(UIUtil.getLabelForeground());
        add(label, BorderLayout.WEST);

        button = new JButton(formatLabel(current));
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setPreferredSize(new Dimension(260, 28));
        button.setMargin(JBUI.insets(2, 12));
        button.addActionListener(e -> showPopup());
        add(button, BorderLayout.CENTER);
    }

    public void setSelected(Format f) {
        this.current = f;
        button.setText(formatLabel(f));
    }

    public Format getSelected() {
        return current;
    }

    private String formatLabel(Format f) {
        if (f == null) return "<选择格式>";
        return f.getCategory() + " ▸ " + f.getName();
    }

    private void showPopup() {
        BaseListPopupStep<String> categoryStep = new BaseListPopupStep<String>(title, categories) {
            @Override
            public boolean hasSubstep(String selectedValue) {
                return true;
            }

            @Override
            public PopupStep<?> onChosen(String selectedValue, boolean finalChoice) {
                List<Format> list = grouped.get(selectedValue);
                return new BaseListPopupStep<Format>(selectedValue, list) {
                    @Override
                    public String getTextFor(Format value) {
                        return value.getName() + "  —  " + value.getDescription();
                    }

                    @Override
                    public PopupStep<?> onChosen(Format selectedValue, boolean finalChoice) {
                        if (selectedValue != null && !selectedValue.equals(current)) {
                            current = selectedValue;
                            button.setText(formatLabel(selectedValue));
                            if (onChange != null) onChange.accept(selectedValue);
                        }
                        return FINAL_CHOICE;
                    }
                };
            }
        };

        ListPopup popup = JBPopupFactory.getInstance().createListPopup(categoryStep);
        popup.showUnderneathOf(button);
    }
}
