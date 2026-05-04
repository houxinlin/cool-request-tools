package dev.coolrequest.tool.json;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import dev.coolrequest.tool.CoolToolPanel;
import dev.coolrequest.tool.ToolPanelFactory;

import javax.swing.*;

public class JsonToolFactory implements ToolPanelFactory {

    @Override
    public CoolToolPanel createToolPanel() {
        return new CoolToolPanel() {
            private Project project;
            private JsonMainPanel panel;

            @Override
            public JPanel createPanel() {
                JsonMainPanel[] holder = new JsonMainPanel[1];
                Runnable build = () -> holder[0] = new JsonMainPanel(project);
                if (ApplicationManager.getApplication().isDispatchThread()) {
                    build.run();
                } else {
                    ApplicationManager.getApplication().invokeAndWait(build);
                }
                panel = holder[0];
                return panel;
            }

            @Override
            public void showTool() {
            }

            @Override
            public void closeTool() {
                if (panel != null) {
                    panel.dispose();
                }
            }
        };
    }
}
