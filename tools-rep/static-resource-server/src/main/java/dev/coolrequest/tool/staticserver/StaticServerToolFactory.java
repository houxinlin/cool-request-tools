package dev.coolrequest.tool.staticserver;

import com.intellij.openapi.project.Project;
import dev.coolrequest.tool.CoolToolPanel;
import dev.coolrequest.tool.ToolPanelFactory;

import javax.swing.*;

public class StaticServerToolFactory implements ToolPanelFactory {

    @Override
    public CoolToolPanel createToolPanel() {
        return new CoolToolPanel() {
            /** 上层加载器通过反射自动注入当前 Project 实例 */
            public Project project;

            private StaticServerPanel panel;

            @Override
            public JPanel createPanel() {
                panel = new StaticServerPanel(project);
                return panel;
            }

            @Override
            public void showTool() {
            }

            @Override
            public void closeTool() {
                if (panel != null) {
                    panel.stopAllServers();
                }
            }
        };
    }
}
