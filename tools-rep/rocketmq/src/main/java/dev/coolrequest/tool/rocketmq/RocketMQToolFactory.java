package dev.coolrequest.tool.rocketmq;

import com.intellij.openapi.project.Project;
import dev.coolrequest.tool.CoolToolPanel;
import dev.coolrequest.tool.ToolPanelFactory;

import javax.swing.*;

public class RocketMQToolFactory implements ToolPanelFactory {

    @Override
    public CoolToolPanel createToolPanel() {
        return new CoolToolPanel() {
            private Project project;

            @Override
            public JPanel createPanel() {
                return new RocketMQMainPanel(project);
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
