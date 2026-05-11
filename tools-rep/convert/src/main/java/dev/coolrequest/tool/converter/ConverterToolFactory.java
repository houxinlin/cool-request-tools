package dev.coolrequest.tool.converter;

import com.intellij.openapi.project.Project;
import dev.coolrequest.tool.CoolToolPanel;
import dev.coolrequest.tool.ToolPanelFactory;
import dev.coolrequest.tool.converter.ui.ConverterMainPanel;

import javax.swing.JPanel;

public class ConverterToolFactory implements ToolPanelFactory {

    @Override
    public CoolToolPanel createToolPanel() {
        return new CoolToolPanel() {
            private Project project;

            @Override
            public JPanel createPanel() {
                return new ConverterMainPanel();
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
