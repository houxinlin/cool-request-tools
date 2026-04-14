package com.coolrequest.tool.jsontoany;

import dev.coolrequest.tool.CoolToolPanel;
import dev.coolrequest.tool.ToolPanelFactory;

import javax.swing.*;

public class JsonToAnyToolFactory implements ToolPanelFactory {

    @Override
    public CoolToolPanel createToolPanel() {
        return new CoolToolPanel() {
            private MainPanel panel;

            @Override
            public JPanel createPanel() {
                panel = new MainPanel();
                return panel;
            }

            @Override
            public void showTool() {}

            @Override
            public void closeTool() {}
        };
    }
}
