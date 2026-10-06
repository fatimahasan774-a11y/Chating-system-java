package javachatsystem;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatClient extends JFrame {
    private String username;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    private DefaultListModel<UserStatus> userListModel;
    private JList<UserStatus> userList;
    private List<UserStatus> allUsersList = new ArrayList<>();
    private Map<String, UserStatus> userMap = new HashMap<>();

    private JTextPane chatArea;
    private JTextField messageField;
    private JTextField searchField;
    private JButton sendButton;
    private JButton addContactBtn;
    private JButton logoutBtn; 
    private JLabel currentChatLabel;

    private String selectedUser = "GLOBAL";

    public static class UserStatus {
        private String name;
        private boolean isOnline;
        private int unreadCount;

        public UserStatus(String name, boolean isOnline) {
            this.name = name;
            this.isOnline = isOnline;
            this.unreadCount = 0;
        }

        public String getName() { return name; }
        public boolean isOnline() { return isOnline; }
        public void setOnline(boolean online) { this.isOnline = online; }
        
        public int getUnreadCount() { return unreadCount; }
        public void incrementUnread() { this.unreadCount++; }
        public void resetUnread() { this.unreadCount = 0; }

        @Override
        public String toString() { return name; }
    }

    public ChatClient(String username, Socket socket, BufferedReader in, PrintWriter out) {
        this.username = username;
        this.socket = socket;
        this.in = in;
        this.out = out;

        setTitle("Java Chat System - " + username);
        setSize(900, 620);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        Color darkBg = new Color(15, 23, 42);
        Color sidebarBg = new Color(30, 41, 59);
        Color chatBg = new Color(248, 250, 252);
        Color accentBlue = new Color(37, 99, 235);
        Color greenStatus = new Color(34, 197, 94);
        Color logoutRed = new Color(239, 68, 68);

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(chatBg);

        // TOP HEADER
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(darkBg);
        topPanel.setPreferredSize(new Dimension(900, 55));
        topPanel.setBorder(new EmptyBorder(10, 20, 10, 20));

        JLabel headerLabel = new JLabel("Logged in as: " + username, JLabel.LEFT);
        headerLabel.setForeground(Color.WHITE);
        headerLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));

        JPanel topEastPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        topEastPanel.setOpaque(false);

        currentChatLabel = new JLabel("Chatting with: Everyone (GLOBAL)");
        currentChatLabel.setForeground(greenStatus);
        currentChatLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));

        logoutBtn = new JButton("Logout");
        logoutBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        logoutBtn.setBackground(logoutRed);
        logoutBtn.setForeground(Color.WHITE);
        logoutBtn.setFocusPainted(false);
        logoutBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        logoutBtn.setPreferredSize(new Dimension(85, 30));

        topEastPanel.add(currentChatLabel);
        topEastPanel.add(logoutBtn);

        topPanel.add(headerLabel, BorderLayout.WEST);
        topPanel.add(topEastPanel, BorderLayout.EAST);
        mainPanel.add(topPanel, BorderLayout.NORTH);

        // SIDEBAR
        JPanel sidebarPanel = new JPanel(new BorderLayout());
        sidebarPanel.setPreferredSize(new Dimension(260, 0));
        sidebarPanel.setBackground(sidebarBg);
        sidebarPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(51, 65, 85)));

        JPanel controlsPanel = new JPanel(new BorderLayout(5, 5));
        controlsPanel.setBackground(sidebarBg);
        controlsPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        searchField = new JTextField();
        searchField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        searchField.setBackground(new Color(15, 23, 42));
        searchField.setForeground(Color.WHITE);
        searchField.setCaretColor(Color.WHITE);
        searchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(51, 65, 85)),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)
        ));

        addContactBtn = new JButton("+ Add");
        addContactBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        addContactBtn.setBackground(greenStatus);
        addContactBtn.setForeground(Color.WHITE);
        addContactBtn.setFocusPainted(false);
        addContactBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        controlsPanel.add(searchField, BorderLayout.CENTER);
        controlsPanel.add(addContactBtn, BorderLayout.EAST);
        sidebarPanel.add(controlsPanel, BorderLayout.NORTH);

        // USER LIST
        userListModel = new DefaultListModel<>();
        UserStatus globalStatus = new UserStatus("GLOBAL", true);
        userListModel.addElement(globalStatus);
        allUsersList.add(globalStatus);
        userMap.put("GLOBAL", globalStatus);

        userList = new JList<>(userListModel);
        userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        userList.setSelectedIndex(0);
        userList.setBackground(sidebarBg);
        userList.setFixedCellHeight(42);
        userList.setCellRenderer(new UserStatusCellRenderer());

        userList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                UserStatus selected = userList.getSelectedValue();
                if (selected != null) {
                    selectedUser = selected.getName();
                    selected.resetUnread();
                    userList.repaint();
                    String statusText = selected.isOnline() ? "● Online" : "○ Offline";
                    currentChatLabel.setText("Chatting with: " + selectedUser + " (" + statusText + ")");
                    
                    chatArea.setText("");
                    
                    if (!selectedUser.equals("GLOBAL")) {
                        out.println("GET_HISTORY:" + selectedUser);
                    }
                }
            }
        });

        JScrollPane userScrollPane = new JScrollPane(userList);
        userScrollPane.setBorder(null);
        sidebarPanel.add(userScrollPane, BorderLayout.CENTER);
        mainPanel.add(sidebarPanel, BorderLayout.WEST);

        // CHAT PANEL
        JPanel chatPanel = new JPanel(new BorderLayout());
        chatPanel.setBackground(chatBg);

        chatArea = new JTextPane();
        chatArea.setEditable(false);
        chatArea.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        chatArea.setBackground(chatBg);
        chatArea.setBorder(new EmptyBorder(15, 15, 15, 15));

        JScrollPane chatScrollPane = new JScrollPane(chatArea);
        chatScrollPane.setBorder(null);
        chatPanel.add(chatScrollPane, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout(10, 0));
        bottomPanel.setBackground(Color.WHITE);
        bottomPanel.setBorder(new EmptyBorder(12, 15, 12, 15));

        messageField = new JTextField();
        messageField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        messageField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(203, 213, 225)),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));

        sendButton = new JButton("Send");
        sendButton.setFont(new Font("Segoe UI", Font.BOLD, 14));
        sendButton.setBackground(accentBlue);
        sendButton.setForeground(Color.WHITE);
        sendButton.setFocusPainted(false);
        sendButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        sendButton.setPreferredSize(new Dimension(95, 38));

        bottomPanel.add(messageField, BorderLayout.CENTER);
        bottomPanel.add(sendButton, BorderLayout.EAST);
        chatPanel.add(bottomPanel, BorderLayout.SOUTH);

        mainPanel.add(chatPanel, BorderLayout.CENTER);
        add(mainPanel);

        // ACTIONS
        sendButton.addActionListener(e -> sendMessage());
        messageField.addActionListener(e -> sendMessage());

        // LOGOUT ACTION SAX AH
        logoutBtn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(
                ChatClient.this, 
             "Are you sure you want to log out?",
            "Confirmation",

                JOptionPane.YES_NO_OPTION
            );
            if (confirm == JOptionPane.YES_OPTION) {
                performLogout();
            }
        });

        addContactBtn.addActionListener(e -> {
            String newPerson = JOptionPane.showInputDialog(this, "Enter the username of the person you want to add.:", "Add Contact", JOptionPane.QUESTION_MESSAGE);
            if (newPerson != null && !newPerson.trim().isEmpty()) {
                String name = newPerson.trim();
                if (name.equalsIgnoreCase(username)) {
                    JOptionPane.showMessageDialog(this, "Isku ma dari kartid magacaaga!", "Digniin", JOptionPane.WARNING_MESSAGE);
                } else if (userMap.containsKey(name)) {
                    JOptionPane.showMessageDialog(this, "User-ka " + name + " mar hore ayuu ku jiraa liiskaaga.", "Digniin", JOptionPane.WARNING_MESSAGE);
                } else {
                    out.println("ADD_CONTACT:" + name);
                }
            }
        });

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { filterContacts(searchField.getText().trim()); }
            public void removeUpdate(DocumentEvent e) { filterContacts(searchField.getText().trim()); }
            public void changedUpdate(DocumentEvent e) { filterContacts(searchField.getText().trim()); }
        });

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                performLogout();
            }
        });

        new Thread(new IncomingReader()).start();
        setVisible(true);
    }

    private void performLogout() {
        if (out != null) {
            out.println("LOGOUT");
            out.flush();
        }
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        dispose(); 
    }

    private void filterContacts(String query) {
        userListModel.clear();
        for (UserStatus u : allUsersList) {
            if (u.getName().toLowerCase().contains(query.toLowerCase())) {
                userListModel.addElement(u);
            }
        }
    }

    private void addContactToList(String name, boolean isOnline) {
        SwingUtilities.invokeLater(() -> {
            if (!userMap.containsKey(name)) {
                UserStatus newStatus = new UserStatus(name, isOnline);
                allUsersList.add(newStatus);
                userMap.put(name, newStatus);
                filterContacts(searchField.getText().trim());
            } else {
                userMap.get(name).setOnline(isOnline);
                userList.repaint();
            }
        });
    }

    private void sendMessage() {
        String msg = messageField.getText().trim();
        if (!msg.isEmpty()) {
            if (selectedUser.equals("GLOBAL")) {
                out.println("MSG_ALL:" + msg);
                appendMessage("Me (Global): " + msg);
            } else {
                out.println("MSG_PRIVATE:" + selectedUser + ":" + msg);
                appendMessage("Me: " + msg);
            }
            messageField.setText("");
        }
    }

    private void appendMessage(String msg) {
        SwingUtilities.invokeLater(() -> {
            chatArea.setText(chatArea.getText() + msg + "\n");
        });
    }

    private class UserStatusCellRenderer extends JPanel implements ListCellRenderer<UserStatus> {
        private JLabel nameLabel = new JLabel();
        private JLabel badgeLabel = new JLabel();
        private JLabel statusLabel = new JLabel();

        public UserStatusCellRenderer() {
            setLayout(new BorderLayout(5, 0));
            setBorder(new EmptyBorder(8, 12, 8, 12));

            JPanel rightContainer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            rightContainer.setOpaque(false);
            rightContainer.add(badgeLabel);
            rightContainer.add(statusLabel);

            add(nameLabel, BorderLayout.CENTER);
            add(rightContainer, BorderLayout.EAST);
            setOpaque(true);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends UserStatus> list, UserStatus value, int index, boolean isSelected, boolean cellHasFocus) {
            nameLabel.setText(value.getName());
            nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));

            if (value.isOnline()) {
                statusLabel.setText("● Online");
                statusLabel.setForeground(new Color(34, 197, 94));
            } else {
                statusLabel.setText("○ Offline");
                statusLabel.setForeground(new Color(148, 163, 184));
            }

            if (value.getUnreadCount() > 0) {
                badgeLabel.setText(" " + value.getUnreadCount() + " ");
                badgeLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
                badgeLabel.setForeground(Color.WHITE);
                badgeLabel.setBackground(new Color(34, 197, 94));
                badgeLabel.setOpaque(true);
                badgeLabel.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
            } else {
                badgeLabel.setText("");
                badgeLabel.setOpaque(false);
            }

            if (isSelected) {
                setBackground(new Color(37, 99, 235));
                nameLabel.setForeground(Color.WHITE);
                if (!value.isOnline()) statusLabel.setForeground(new Color(226, 232, 240));
            } else {
                setBackground(new Color(30, 41, 59));
                nameLabel.setForeground(Color.WHITE);
            }
            return this;
        }
    }

    private class IncomingReader implements Runnable {
        @Override
        public void run() {
            String message;
            try {
                while ((message = in.readLine()) != null) {
                    final String msg = message;

                    if (msg.startsWith("STATUS_UPDATE:")) {
                        String[] parts = msg.split(":");
                        if (parts.length >= 3) {
                            String uName = parts[1].trim();
                            boolean isOnline = parts[2].equalsIgnoreCase("ONLINE");

                            SwingUtilities.invokeLater(() -> {
                                UserStatus us = userMap.get(uName);
                                if (us != null) {
                                    us.setOnline(isOnline);
                                    userList.repaint();
                                }
                                if (selectedUser != null && selectedUser.equalsIgnoreCase(uName)) {
                                    currentChatLabel.setText("Chatting with: " + uName + " (" + (isOnline ? "● Online" : "○ Offline") + ")");
                                }
                            });
                        }
                    } 
                    else if (msg.startsWith("CONTACT_ITEM:")) {
                        String[] parts = msg.split(":", 3);
                        if (parts.length >= 3) {
                            String cName = parts[1].trim();
                            boolean isOnline = parts[2].equalsIgnoreCase("Online");
                            addContactToList(cName, isOnline);
                        }
                    }
                    else if (msg.startsWith("ADD_CONTACT_RESP:")) {
                        String[] parts = msg.split(":", 4);
                        if (parts.length >= 2 && parts[1].equals("SUCCESS")) {
                            if (parts.length >= 4) {
                                String cName = parts[2].trim();
                                boolean isOnline = parts[3].equalsIgnoreCase("Online");
                                addContactToList(cName, isOnline);
                                SwingUtilities.invokeLater(() -> 
                                    JOptionPane.showMessageDialog(ChatClient.this, "User-ka " + cName + " You’ve been added, and you can now chat with them.!", "succesfull", JOptionPane.INFORMATION_MESSAGE)
                                );
                            }
                        } else {
                            String errReason = (parts.length >= 3) ? parts[2] : "Cilad ayaa dhacday";
                            SwingUtilities.invokeLater(() -> 
                                JOptionPane.showMessageDialog(ChatClient.this, errReason, "Digniin", JOptionPane.ERROR_MESSAGE)
                            );
                        }
                    }
                    else if (msg.startsWith("MSG_FROM:")) {
                        String[] parts = msg.split(":", 3);
                        if (parts.length >= 3) {
                            String sender = parts[1].trim();
                            String text = parts[2].trim();
                            
                            if (!userMap.containsKey(sender)) {
                                addContactToList(sender, true);
                            }
                            
                            if (selectedUser.equalsIgnoreCase(sender)) {
                                appendMessage(sender + ": " + text);
                            } else {
                                UserStatus us = userMap.get(sender);
                                if (us != null) {
                                    us.incrementUnread();
                                    userList.repaint();
                                }
                            }
                        }
                    }
                    else if (msg.startsWith("MSG_ALL_FROM:")) {
                        String[] parts = msg.split(":", 3);
                        if (parts.length >= 3) {
                            String sender = parts[1].trim();
                            String text = parts[2].trim();
                            if (selectedUser.equals("GLOBAL")) {
                                appendMessage(sender + " (Global): " + text);
                            }
                        }
                    }
                    else if (msg.startsWith("CHAT_HISTORY_ITEM:")) {
                        String[] parts = msg.split(":", 3);
                        if (parts.length >= 3) {
                            String historyMsg = parts[2].trim();
                            appendMessage(historyMsg);
                        }
                    }
                    else if (msg.startsWith("SYSTEM_MSG:")) {
                        String sysMsg = msg.substring(11);
                        appendMessage("[System]: " + sysMsg);
                    }
                }
            } catch (IOException e) {
                System.out.println("Connection lost to server.");
            }
        }
    }
}