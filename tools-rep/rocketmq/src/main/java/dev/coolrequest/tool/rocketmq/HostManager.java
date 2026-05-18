package dev.coolrequest.tool.rocketmq;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class HostManager {

    private static final String STORAGE_KEY = "coolrequest.rocketmq.hosts";
    private static final String SELECTED_HOST_KEY = "coolrequest.rocketmq.selectedHost";
    private static final Gson GSON = new Gson();

    private final Project project;

    public HostManager(Project project) {
        this.project = project;
    }

    public List<String> getHosts() {
        String json = PropertiesComponent.getInstance(project).getValue(STORAGE_KEY, "[]");
        List<String> hosts = GSON.fromJson(json, new TypeToken<List<String>>() {}.getType());
        if (hosts == null || hosts.isEmpty()) {
            hosts = new ArrayList<>();
            hosts.add("127.0.0.1:9876");
            saveHosts(hosts);
        }
        return hosts;
    }

    public String getSelectedHost() {
        String selected = PropertiesComponent.getInstance(project).getValue(SELECTED_HOST_KEY);
        if (selected == null || selected.isEmpty()) {
            List<String> hosts = getHosts();
            selected = hosts.get(0);
            setSelectedHost(selected);
        }
        return selected;
    }

    public void setSelectedHost(String host) {
        PropertiesComponent.getInstance(project).setValue(SELECTED_HOST_KEY, host);
    }

    private void saveHosts(List<String> hosts) {
        PropertiesComponent.getInstance(project).setValue(STORAGE_KEY, GSON.toJson(hosts));
    }

    public void addHost(String host) {
        List<String> hosts = new ArrayList<>(getHosts());
        if (!hosts.contains(host)) {
            hosts.add(host);
            saveHosts(hosts);
        }
    }

    public void removeHost(String host) {
        List<String> hosts = new ArrayList<>(getHosts());
        hosts.remove(host);
        if (hosts.isEmpty()) {
            hosts.add("127.0.0.1:9876");
        }
        saveHosts(hosts);
    }

    public void showHostPopup(JComponent component, Consumer<String> onSelected) {
        List<String> hosts = new ArrayList<>(getHosts());
        hosts.add("+ Add Host...");

        JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<String>("Select Host", hosts) {
            @Override
            public PopupStep<?> onChosen(String selectedValue, boolean finalChoice) {
                if ("+ Add Host...".equals(selectedValue)) {
                    SwingUtilities.invokeLater(() -> {
                        String newHost = Messages.showInputDialog(
                                project,
                                "Enter NameServer address (e.g. 192.168.1.100:9876):",
                                "Add Host",
                                null);
                        if (newHost != null && !newHost.trim().isEmpty()) {
                            addHost(newHost.trim());
                            setSelectedHost(newHost.trim());
                            onSelected.accept(newHost.trim());
                        }
                    });
                } else {
                    setSelectedHost(selectedValue);
                    onSelected.accept(selectedValue);
                }
                return FINAL_CHOICE;
            }
        }).showUnderneathOf(component);
    }
}
