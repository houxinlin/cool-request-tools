package dev.coolrequest.tool.qrcode;

import com.intellij.openapi.project.Project;
import dev.coolrequest.tool.CoolToolPanel;
import dev.coolrequest.tool.ToolPanelFactory;

import javax.swing.*;

public class QrCodeToolFactory implements ToolPanelFactory {

    @Override
    public CoolToolPanel createToolPanel() {
        return new CoolToolPanel() {
            /** 上层加载器通过反射自动注入当前 Project 实例 */
            public Project project;

            private QrCodePanel panel;

            @Override
            public JPanel createPanel() {
                panel = new QrCodePanel(project);
                return panel;
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
