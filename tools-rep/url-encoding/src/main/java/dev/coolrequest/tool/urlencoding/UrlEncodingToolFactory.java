package dev.coolrequest.tool.urlencoding;

import dev.coolrequest.tool.CoolToolPanel;
import dev.coolrequest.tool.ToolPanelFactory;

import javax.swing.*;

public class UrlEncodingToolFactory implements ToolPanelFactory {

    @Override
    public CoolToolPanel createToolPanel() {
        return new CoolToolPanel() {
            private com.intellij.openapi.project.Project project;

            @Override
            public JPanel createPanel() {
                return new UrlEncodingPanel();
            }

            @Override
            public void showTool() {
            }

            @Override
            public void closeTool() {
            }
        };
    }
}
