package javachatsystem;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.net.Socket;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ChatClient extends JFrame {
    private DefaultListModel<String> contactListModel;
    private JList<String> contactList;
    private JLabel chatHeader;
    private JPanel chatPanel;
    private JTextField inputField, searchField;
    private JButton sendButton, addContactBtn, emojiButton;
    private JPopupMenu emojiMenu;

    private String currentUser;
    private String selectedUser = "";
    private PrintWriter out;
    private BufferedReader in;

    private Map<String, java.util.List<MessageModel>> chatHistories = new HashMap<>();
    private Map<String, String> userStatusMap = new HashMap<>(); 
    private Map<String, Integer> unreadCountMap = new HashMap<>();
    private Map<String, JLabel> messageLabelMap = new HashMap<>(); 

    enum TickStatus { SENT, DELIVERED, READ }

    static class MessageModel {
        String id;
        String sender;
        String text;
        String time;
        LocalDate date;
        TickStatus status;

        MessageModel(String id, String sender, String text, String time, LocalDate date, TickStatus status) {
            this.id = id;
            this.sender = sender;
            this.text = text;
            this.time = time;
            this.date = date;
            this.status = status;
        }

        public String getMsgId() { return id; }
        public String getSender() { return sender; }
        public TickStatus getStatus() { return status; }
        public void setStatus(TickStatus status) { this.status = status; }
    }

    public ChatClient(String username, Socket socket, BufferedReader in, PrintWriter out) {
        this.currentUser = username;
        this.in = in;
        this.out = out;

        setTitle("WhatsApp - " + currentUser);
        setSize(920, 650);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // --- LEFT PANEL ---
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setPreferredSize(new Dimension(300, 0));

        // Profile-ka Isticmaalaha oo leh Circular Letter Avatar
        String myInitial = currentUser.isEmpty() ? "?" : currentUser.substring(0, 1).toUpperCase();
        JLabel profile = new JLabel("<html><body style='padding: 5px;'>" +
                "<table cellpadding='0' cellspacing='0'><tr>" +
                "<td><div style='background-color:#128C7E; color:white; border-radius:50%; width:32px; height:32px; text-align:center; font-weight:bold; font-size:16px; line-height:32px;'>" + myInitial + "</div></td>" +
                "<td style='padding-left:10px;'><b style='color:white; font-size:14px;'>" + currentUser + "</b></td>" +
                "</tr></table></body></html>", JLabel.LEFT);
        
        profile.setPreferredSize(new Dimension(0, 60));
        profile.setOpaque(true);
        profile.setBackground(new Color(7, 94, 84));

        addContactBtn = new JButton("Add Contact");
        addContactBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        addContactBtn.setBackground(new Color(18, 140, 126));
        addContactBtn.setForeground(Color.WHITE);

        contactListModel = new DefaultListModel<>();
        contactList = new JList<>(contactListModel);
        contactList.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        contactList.setFixedCellHeight(60);

        contactList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int index = contactList.locationToIndex(e.getPoint());
                if (index >= 0) {
                    String rawValue = contactListModel.getElementAt(index);
                    String cleanName = extractUsername(rawValue);

                    if (cleanName != null && !cleanName.equals(currentUser)) {
                        selectedUser = cleanName;
                        unreadCountMap.put(selectedUser, 0); 
                        refreshContactListUI();

                        String status = userStatusMap.getOrDefault(selectedUser, "OFFLINE");
                        updateHeaderStatus(selectedUser, status);
                        loadConversation(selectedUser);
                    }
                }
            }
        });

        leftPanel.add(profile, BorderLayout.NORTH);
        leftPanel.add(addContactBtn, BorderLayout.SOUTH);
        leftPanel.add(new JScrollPane(contactList), BorderLayout.CENTER);

        // --- RIGHT PANEL ---
        JPanel rightPanel = new JPanel(new BorderLayout());

        JPanel headerPanel = new JPanel(new BorderLayout());
        chatHeader = new JLabel("   Dooro qof aad fariin u dirto");
        chatHeader.setFont(new Font("Segoe UI", Font.BOLD, 15));
        chatHeader.setPreferredSize(new Dimension(0, 60));
        chatHeader.setOpaque(true);
        chatHeader.setBackground(new Color(230, 233, 238));

        searchField = new JTextField();
        searchField.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        searchField.setToolTipText("Raadi fariin...");
        searchField.setPreferredSize(new Dimension(180, 28));

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { filterMessages(); }
            public void removeUpdate(DocumentEvent e) { filterMessages(); }
            public void changedUpdate(DocumentEvent e) { filterMessages(); }
        });

        JPanel searchContainer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 15));
        searchContainer.setOpaque(false);
        searchContainer.add(new JLabel("🔍"));
        searchContainer.add(searchField);

        headerPanel.setBackground(new Color(230, 233, 238));
        headerPanel.add(chatHeader, BorderLayout.CENTER);
        headerPanel.add(searchContainer, BorderLayout.EAST);

        chatPanel = new JPanel();
        chatPanel.setLayout(new BoxLayout(chatPanel, BoxLayout.Y_AXIS));
        chatPanel.setBackground(new Color(229, 221, 213));
        chatPanel.setBorder(new EmptyBorder(10, 15, 10, 15));

        JScrollPane scrollPane = new JScrollPane(chatPanel);
        scrollPane.getVerticalScrollBar().setUnitIncrement(12);

        inputField = new JTextField();
        inputField.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        emojiButton = new JButton("😊");
        emojiButton.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        emojiButton.setFocusPainted(false);
        emojiButton.setMargin(new Insets(2, 6, 2, 6));

        setupEmojiPicker();

        sendButton = new JButton("Send");
        sendButton.setFont(new Font("Segoe UI", Font.BOLD, 13));
        sendButton.setBackground(new Color(7, 94, 84));
        sendButton.setForeground(Color.WHITE);

        JPanel inputArea = new JPanel(new BorderLayout(5, 0));
        inputArea.add(emojiButton, BorderLayout.WEST);
        inputArea.add(inputField, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(8, 8));
        bottom.setBorder(new EmptyBorder(8, 8, 8, 8));
        bottom.add(inputArea, BorderLayout.CENTER);
        bottom.add(sendButton, BorderLayout.EAST);

        rightPanel.add(headerPanel, BorderLayout.NORTH);
        rightPanel.add(scrollPane, BorderLayout.CENTER);
        rightPanel.add(bottom, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        split.setDividerLocation(300);
        add(split);

        addContactBtn.addActionListener(e -> {
            String newContact = JOptionPane.showInputDialog(this, "Geli username-ka aad ku darsaneyso:");
            if (newContact != null && !newContact.trim().isEmpty()) {
                String cleanContact = newContact.trim();
                if (cleanContact.equals(currentUser)) {
                    JOptionPane.showMessageDialog(this, "Isku ma darsan kartid magacaaga!");
                } else {
                    out.println("ADD_CONTACT:" + cleanContact);
                }
            }
        });

        sendButton.addActionListener(e -> dispatchMessage());
        inputField.addActionListener(e -> dispatchMessage());

        out.println("GET_CONTACTS");

        listenIncoming();
        setVisible(true);
    }

    private void setupEmojiPicker() {
        emojiMenu = new JPopupMenu();
        JPanel emojiPanel = new JPanel(new GridLayout(3, 5, 5, 5));
        emojiPanel.setBorder(new EmptyBorder(5, 5, 5, 5));

        String[] emojis = {"😊", "😂", "❤️", "👍", "🔥", "🙏", "😍", "🎉", "😢", "😎", "🤝", "👏", "😮", "🤔", "✨"};
        for (String emo : emojis) {
            JButton btn = new JButton(emo);
            btn.setFont(new Font("Segoe UI", Font.PLAIN, 16));
            btn.setFocusPainted(false);
            btn.addActionListener(e -> {
                inputField.setText(inputField.getText() + emo);
                emojiMenu.setVisible(false);
                inputField.requestFocusInWindow();
            });
            emojiPanel.add(btn);
        }

        emojiMenu.add(emojiPanel);
        emojiButton.addActionListener(e -> emojiMenu.show(emojiButton, 0, -emojiMenu.getPreferredSize().height));
    }

    private void filterMessages() {
        if (selectedUser == null || selectedUser.isEmpty()) return;
        String query = searchField.getText().trim().toLowerCase();

        chatPanel.removeAll();
        messageLabelMap.clear();

        java.util.List<MessageModel> list = chatHistories.get(selectedUser);
        if (list != null) {
            LocalDate lastDate = null;
            for (MessageModel m : list) {
                if (query.isEmpty() || m.text.toLowerCase().contains(query)) {
                    if (lastDate == null || !lastDate.equals(m.date)) {
                        appendDateDivider(m.date);
                        lastDate = m.date;
                    }
                    boolean isSelf = m.sender.equals(currentUser);
                    appendBubble(m, isSelf);
                }
            }
        }
        chatPanel.revalidate();
        chatPanel.repaint();
    }

    private void appendDateDivider(LocalDate date) {
        String labelText;
        LocalDate today = LocalDate.now();

        if (date.equals(today)) {
            labelText = "Today";
        } else if (date.equals(today.minusDays(1))) {
            labelText = "Yesterday";
        } else {
            labelText = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        }

        JPanel dividerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        dividerPanel.setBackground(new Color(229, 221, 213));
        dividerPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

        JLabel dateLabel = new JLabel("  " + labelText + "  ");
        dateLabel.setFont(new Font("Segoe UI", Font.BOLD, 10));
        dateLabel.setForeground(new Color(100, 100, 100));
        dateLabel.setOpaque(true);
        dateLabel.setBackground(new Color(240, 240, 240));
        dateLabel.setBorder(BorderFactory.createLineBorder(new Color(210, 210, 210), 1, true));

        dividerPanel.add(dateLabel);
        chatPanel.add(dividerPanel);
        chatPanel.add(Box.createVerticalStrut(4));
    }

    private String extractUsername(String rawHtml) {
        if (rawHtml.contains("<!--NAME:")) {
            int start = rawHtml.indexOf("<!--NAME:") + 9;
            int end = rawHtml.indexOf("-->", start);
            if (start < end) {
                return rawHtml.substring(start, end).trim();
            }
        }
        return rawHtml.replaceAll("<[^>]*>", "").trim().split(" ")[0];
    }

    private void updateHeaderStatus(String user, String status) {
        String initial = user.isEmpty() ? "?" : user.substring(0, 1).toUpperCase();
        String indicator = status.equals("ONLINE") ? "(Online)" : "(Offline)";
        
        chatHeader.setText("<html><body style='padding-left: 10px;'>" +
                "<table cellpadding='0' cellspacing='0'><tr>" +
                "<td><div style='background-color:#075E54; color:white; border-radius:50%; width:30px; height:30px; text-align:center; font-weight:bold; font-size:14px; line-height:30px;'>" + initial + "</div></td>" +
                "<td style='padding-left:10px;'><b style='font-size:14px;'>" + user + "</b> <span style='font-size:11px; color:#555555;'>" + indicator + "</span></td>" +
                "</tr></table></body></html>");
    }

    private void dispatchMessage() {
        String msg = inputField.getText().trim();
        if (!msg.isEmpty() && selectedUser != null && !selectedUser.isEmpty()) {
            String msgId = UUID.randomUUID().toString().substring(0, 8);
            String timeNow = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
            LocalDate dateNow = LocalDate.now();

            out.println("SEND_MSG:" + msgId + ":" + selectedUser + ":" + timeNow + ":" + msg);

            MessageModel model = new MessageModel(msgId, currentUser, msg, timeNow, dateNow, TickStatus.SENT);

            java.util.List<MessageModel> history = chatHistories.computeIfAbsent(selectedUser, k -> new ArrayList<>());

            if (history.isEmpty() || !history.get(history.size() - 1).date.equals(dateNow)) {
                appendDateDivider(dateNow);
            }

            history.add(model);
            appendBubble(model, true);
            inputField.setText("");
        }
    }

    private void listenIncoming() {
        new Thread(() -> {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    final String res = line;
                    SwingUtilities.invokeLater(() -> processProtocol(res));
                }
            } catch (IOException e) {
                System.out.println("Connection Lost!");
            }
        }).start();
    }

    private void processProtocol(String msg) {
        if (msg == null || msg.trim().isEmpty()) return;

        if (msg.startsWith("CONTACT_LIST:")) {
            String data = msg.substring(13);
            if (!data.isEmpty()) {
                String[] items = data.split(";");
                for (String item : items) {
                    if (!item.isEmpty() && item.contains(",")) {
                        String[] parts = item.split(",");
                        if (parts.length >= 2 && !parts[0].equals(currentUser)) {
                            userStatusMap.put(parts[0], parts[1]);
                            unreadCountMap.putIfAbsent(parts[0], 0);
                        }
                    }
                }
                refreshContactListUI();
            }
        } 
        else if (msg.startsWith("CONTACT_ADDED:")) {
            String[] p = msg.split(":");
            if (p.length >= 3) {
                String newContact = p[1];
                if (!newContact.equals(currentUser)) {
                    userStatusMap.put(newContact, p[2]);
                    unreadCountMap.putIfAbsent(newContact, 0);
                    chatHistories.putIfAbsent(newContact, new ArrayList<>());
                    refreshContactListUI();
                }
            }
        } 
        else if (msg.startsWith("USER_STATUS:")) {
            String[] p = msg.split(":");
            if (p.length >= 3) {
                String name = p[1];
                String status = p[2];
                if (!name.equals(currentUser)) {
                    userStatusMap.put(name, status);
                    refreshContactListUI();
                    if (name.equals(selectedUser)) {
                        updateHeaderStatus(name, status);
                    }
                }
            }
        } 
        else if (msg.startsWith("MSG:")) {
            String[] p = msg.split(":", 5);
            if (p.length >= 5) {
                String msgId = p[1];
                String sender = p[2];
                String timeStr = p[3];
                String text = p[4];
                LocalDate dateNow = LocalDate.now();

                if (!sender.equals(currentUser)) {
                    if (!userStatusMap.containsKey(sender)) {
                        userStatusMap.put(sender, "ONLINE");
                        unreadCountMap.put(sender, 0);
                    }

                    MessageModel model = new MessageModel(msgId, sender, text, timeStr, dateNow, TickStatus.READ);
                    java.util.List<MessageModel> history = chatHistories.computeIfAbsent(sender, k -> new ArrayList<>());

                    if (sender.equals(selectedUser)) {
                        if (history.isEmpty() || !history.get(history.size() - 1).date.equals(dateNow)) {
                            appendDateDivider(dateNow);
                        }
                        appendBubble(model, false);
                        out.println("MARK_READ:" + msgId + ":" + sender);
                    } else {
                        unreadCountMap.put(sender, unreadCountMap.getOrDefault(sender, 0) + 1);
                        refreshContactListUI();
                        Toolkit.getDefaultToolkit().beep();
                    }
                    history.add(model);
                }
            }
        } 
        else if (msg.startsWith("MSG_DELIVERED:")) {
            String[] p = msg.split(":");
            if (p.length >= 2) {
                String msgId = p[1];
                updateTickUI(msgId, "<font color='gray'>✓✓</font>");
            }
        } 
        else if (msg.startsWith("MSG_READ:")) {
            String[] p = msg.split(":");
            if (p.length >= 2) {
                String msgId = p[1];
                updateTickUI(msgId, "<font color='#34B7F1'>✓✓</font>");
            }
        }
    }

    private void updateTickUI(String msgId, String tickHtml) {
        JLabel label = messageLabelMap.get(msgId);
        if (label != null) {
            String currentText = label.getText();
            currentText = currentText.replaceAll("<font color=.*?>.*?</font>", tickHtml);
            label.setText(currentText);
        }
    }

    // Contact List UI oo leh Goobo (Letter Avatar) ku dhex jira
    private void refreshContactListUI() {
        contactListModel.clear();
        for (String name : userStatusMap.keySet()) {
            if (!name.equals(currentUser)) {
                String status = userStatusMap.get(name);
                int unread = unreadCountMap.getOrDefault(name, 0);

                String statusColor = status.equals("ONLINE") ? "#075E54" : "#888888";
                String statusText = status.toLowerCase();
                String initial = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();

                String badgeHtml = "";
                if (unread > 0) {
                    badgeHtml = "<span style='background-color:#25D366; color:white; font-weight:bold; " +
                                "padding:1px 6px; border-radius:10px; font-size:10px;'>" + unread + "</span>";
                }

                String htmlItem = "<html><body style='width: 200px; padding: 2px;'>" +
                        "<!--NAME:" + name + "-->" +
                        "<table width='100%'><tr>" +
                        "<td width='35'><div style='background-color:#128C7E; color:white; border-radius:50%; width:28px; height:28px; text-align:center; font-weight:bold; font-size:13px; line-height:28px;'>" + initial + "</div></td>" +
                        "<td><b>" + name + "</b><br><span style='color:" + statusColor + "; font-size:10px;'>" + statusText + "</span></td>" +
                        "<td align='right'>" + badgeHtml + "</td>" +
                        "</tr></table></body></html>";

                contactListModel.addElement(htmlItem);
            }
        }
    }

    private void loadConversation(String user) {
        chatPanel.removeAll();
        messageLabelMap.clear();

        java.util.List<MessageModel> list = chatHistories.get(user);
        if (list != null) {
            LocalDate lastDate = null;
            for (MessageModel m : list) {
                if (lastDate == null || !lastDate.equals(m.date)) {
                    appendDateDivider(m.date);
                    lastDate = m.date;
                }
                boolean isSelf = m.sender.equals(currentUser);
                appendBubble(m, isSelf);

                if (!isSelf) {
                    out.println("MARK_READ:" + m.id + ":" + m.sender);
                }
            }
        }
        chatPanel.revalidate();
        chatPanel.repaint();
    }

    private void appendBubble(MessageModel m, boolean isSelf) {
        JPanel bubble = new JPanel(new FlowLayout(isSelf ? FlowLayout.RIGHT : FlowLayout.LEFT, 0, 0));
        bubble.setBackground(new Color(229, 221, 213));
        bubble.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        String tickStr = "";
        if (isSelf) {
            if (m.status == TickStatus.SENT) tickStr = " <font color='gray'>✓</font>";
            else if (m.status == TickStatus.DELIVERED) tickStr = " <font color='gray'>✓✓</font>";
            else if (m.status == TickStatus.READ) tickStr = " <font color='#34B7F1'>✓✓</font>";
        }

        String htmlText = "<html><body style='font-family: Segoe UI, sans-serif; font-size: 11px; padding: 4px 8px;'>" +
                m.text + " &nbsp;&nbsp;<sub style='font-size:8px; color:#888888;'>" + m.time + tickStr + "</sub></body></html>";

        JLabel label = new JLabel(htmlText);
        label.setOpaque(true);
        label.setBackground(isSelf ? new Color(220, 248, 198) : Color.WHITE);
        label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(210, 210, 210), 1, true),
                BorderFactory.createEmptyBorder(2, 4, 2, 4)
        ));

        bubble.add(label);

        if (isSelf) {
            messageLabelMap.put(m.id, label);
        }

        chatPanel.add(bubble);
        chatPanel.add(Box.createVerticalStrut(6));
        chatPanel.revalidate();
        chatPanel.repaint();
    }
}