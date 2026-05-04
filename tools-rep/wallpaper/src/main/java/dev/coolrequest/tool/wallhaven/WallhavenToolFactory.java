package dev.coolrequest.tool.wallhaven;

import com.intellij.openapi.project.Project;
import dev.coolrequest.tool.CoolToolPanel;
import dev.coolrequest.tool.ToolPanelFactory;

import javax.swing.*;

public class WallhavenToolFactory implements ToolPanelFactory {

    @Override
    public CoolToolPanel createToolPanel() {
        return new CoolToolPanel() {
            private Project project;
            private WallhavenPanel panel;

            @Override
            public JPanel createPanel() {
                panel = new WallhavenPanel(project);
                return panel;
            }

            @Override
            public void showTool() {
            }

            @Override
            public void closeTool() {
                if (panel != null) panel.dispose();
            }
        };
    }
}
