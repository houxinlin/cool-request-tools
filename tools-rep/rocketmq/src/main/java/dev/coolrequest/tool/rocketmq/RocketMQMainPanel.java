package dev.coolrequest.tool.rocketmq;

import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class RocketMQMainPanel extends JPanel {

    private static final String CARD_HOME = "Home";
    private static final String CARD_PRODUCER = "Producer";
    private static final String CARD_CONSUMER = "Consumer";

    private final Project project;
    private final HostManager hostManager;
    private final CardLayout cardLayout;
    private final JPanel contentPanel;
    private final JBLabel hostLabel;
    private final JBLabel modeLabel;
    private String currentMode = CARD_HOME;

    public RocketMQMainPanel(Project project) {
        this.project = project;
        this.hostManager = new HostManager(project);
        setLayout(new BorderLayout());

        // Top bar - host selector
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        topBar.setBorder(JBUI.Borders.customLine(UIManager.getColor("Separator.separatorColor"), 0, 0, 1, 0));
        JBLabel hostTitle = new JBLabel("Host:");
        topBar.add(hostTitle);

        hostLabel = new JBLabel(hostManager.getSelectedHost());
        hostLabel.setForeground(UIManager.getColor("Link.activeForeground"));
        hostLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        hostLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                hostManager.showHostPopup(hostLabel, host -> hostLabel.setText(host));
            }
        });
        topBar.add(hostLabel);
        add(topBar, BorderLayout.NORTH);

        // Content area
        cardLayout = new CardLayout();
        contentPanel = new JPanel(cardLayout);
        contentPanel.add(createHomePanel(), CARD_HOME);
        contentPanel.add(new ProducerPanel(project, hostManager), CARD_PRODUCER);
        contentPanel.add(new ConsumerPanel(project, hostManager), CARD_CONSUMER);
        add(contentPanel, BorderLayout.CENTER);

        // Bottom status bar
        JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        statusBar.setBorder(JBUI.Borders.customLine(UIManager.getColor("Separator.separatorColor"), 1, 0, 0, 0));
        JBLabel modeTitle = new JBLabel("Mode:");
        statusBar.add(modeTitle);

        modeLabel = new JBLabel(currentMode);
        modeLabel.setForeground(UIManager.getColor("Link.activeForeground"));
        modeLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        modeLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                switchMode();
            }
        });
        statusBar.add(modeLabel);
        add(statusBar, BorderLayout.SOUTH);

        cardLayout.show(contentPanel, CARD_HOME);
    }

    private JPanel createHomePanel() {
        JPanel homePanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = JBUI.insets(20);
        gbc.gridy = 0;

        JPanel producerCard = createCard("Producer", "发送消息到 RocketMQ");
        JPanel consumerCard = createCard("Consumer", "从 RocketMQ 订阅消息");

        producerCard.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showMode(CARD_PRODUCER);
            }
        });

        consumerCard.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showMode(CARD_CONSUMER);
            }
        });

        gbc.gridx = 0;
        homePanel.add(producerCard, gbc);

        gbc.gridx = 1;
        homePanel.add(consumerCard, gbc);

        return homePanel;
    }

    private JPanel createCard(String title, String description) {
        JPanel card = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.dispose();
            }
        };
        card.setPreferredSize(new Dimension(180, 160));
        card.setBorder(JBUI.Borders.empty(20));
        card.setOpaque(false);
        card.setBackground(UIManager.getColor("Button.background"));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.CENTER;

        JBLabel titleLabel = new JBLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        card.add(titleLabel, gbc);

        gbc.gridy = 1;
        gbc.insets = JBUI.insets(8, 0, 0, 0);
        JBLabel descLabel = new JBLabel(description);
        descLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        card.add(descLabel, gbc);

        card.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                card.setBackground(UIManager.getColor("Button.hoverBackground") != null
                        ? UIManager.getColor("Button.hoverBackground")
                        : UIManager.getColor("Button.background").brighter());
                card.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                card.setBackground(UIManager.getColor("Button.background"));
                card.repaint();
            }
        });

        return card;
    }

    private void showMode(String mode) {
        currentMode = mode;
        modeLabel.setText(mode);
        cardLayout.show(contentPanel, mode);
    }

    private void switchMode() {
        if (CARD_PRODUCER.equals(currentMode)) {
            showMode(CARD_CONSUMER);
        } else if (CARD_CONSUMER.equals(currentMode)) {
            showMode(CARD_PRODUCER);
        } else {
            showMode(CARD_PRODUCER);
        }
    }
}
