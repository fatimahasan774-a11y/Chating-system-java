package javachatsystem;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServer extends JFrame {
    private static final int PORT = 12345;
    
    // MySQL Database Configuration (Bedel password-ka haddii aad leedahay)
    private static final String DB_URL = "jdbc:mysql://localhost:3306/chating?useSSL=false&serverTimezone=UTC";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "";

    // Active Online Connections (username -> ClientHandler)
    private static final Map<String, ClientHandler> activeClients = new ConcurrentHashMap<>();

    private static JTextArea logArea;
    private static DefaultListModel<String> onlineListModel;
    private static JList<String> onlineUserList;
    private ServerSocket serverSocket;

    public ChatServer() {
        setTitle("Java Chat System - Server Monitor (MySQL DB)");
        setSize(700, 450);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        Color darkBg = new Color(15, 23, 42);
        Color panelBg = new Color(30, 41, 59);
        Color textColor = new Color(241, 245, 249);
        Color greenStatus = new Color(34, 197, 94);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        mainPanel.setBackground(darkBg);

        JLabel headerLabel = new JLabel("SERVER STATUS: RUNNING (PORT " + PORT + ") - MYSQL CONNECTED", JLabel.CENTER);
        headerLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        headerLabel.setForeground(greenStatus);
        mainPanel.add(headerLabel, BorderLayout.NORTH);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setBackground(panelBg);
        logArea.setForeground(textColor);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        logArea.setBorder(new EmptyBorder(10, 10, 10, 10));

        JScrollPane logScrollPane = new JScrollPane(logArea);
        logScrollPane.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(51, 65, 85)),
                " Server Logs ",
                0, 0, new Font("Segoe UI", Font.BOLD, 12), Color.WHITE));
        mainPanel.add(logScrollPane, BorderLayout.CENTER);

        onlineListModel = new DefaultListModel<>();
        onlineUserList = new JList<>(onlineListModel);
        onlineUserList.setBackground(panelBg);
        onlineUserList.setForeground(greenStatus);
        onlineUserList.setFont(new Font("Segoe UI", Font.BOLD, 13));
        onlineUserList.setFixedCellHeight(30);

        JScrollPane userScrollPane = new JScrollPane(onlineUserList);
        userScrollPane.setPreferredSize(new Dimension(200, 0));
        userScrollPane.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(51, 65, 85)),
                " Online Users ",
                0, 0, new Font("Segoe UI", Font.BOLD, 12), Color.WHITE));
        mainPanel.add(userScrollPane, BorderLayout.EAST);

        add(mainPanel);
        setVisible(true);

        startServer();
    }

    private void startServer() {
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket();
                serverSocket.setReuseAddress(true);
                serverSocket.bind(new InetSocketAddress(PORT));
                
                log("The Chat Server is running on port.: " + PORT + "...");

                while (!serverSocket.isClosed()) {
                    Socket socket = serverSocket.accept();
                    log("[NEW CONNECTION]: Client cusub ayaa soo xidhiidhay: " + socket.getInetAddress());
                    new Thread(new ClientHandler(socket)).start();
                }
            } catch (IOException e) {
                log("[Error]: Server Exception: " + e.getMessage());
            }
        }).start();
    }

    public static void log(String message) {
        SwingUtilities.invokeLater(() -> {
            if (logArea != null) {
                logArea.append(message + "\n");
                logArea.setCaretPosition(logArea.getDocument().getLength());
            } else {
                System.out.println(message);
            }
        });
    }

    // --- MYSQL DATABASE AUTHENTICATION METHODS ---

    // Xaqiijinta User-ka Database-ka (Login)
    public static boolean validateUser(String u, String p) {
        String query = "SELECT * FROM users WHERE LOWER(username) = LOWER(?) AND password = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, u.trim());
            pstmt.setString(2, p.trim());
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next(); // True haddii uu user-ku jiro oo password-ku sax yahay
            }
        } catch (SQLException e) {
            log("[DB Error - Login]: " + e.getMessage());
            return false;
        }
    }

    // Diiwaangelinta User cusub ee Database-ka (Register)
    public static synchronized boolean registerUser(String u, String p) {
        if (u == null || p == null || u.trim().isEmpty() || p.trim().isEmpty()) {
            return false;
        }
        
        String checkQuery = "SELECT * FROM users WHERE LOWER(username) = LOWER(?)";
        String insertQuery = "INSERT INTO users (username, password, status) VALUES (?, ?, 'OFFLINE')";
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement checkStmt = conn.prepareStatement(checkQuery)) {
            
            checkStmt.setString(1, u.trim());
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next()) {
                    return false; // User-ku mar hore ayuu jiraa
                }
            }
            
            try (PreparedStatement insertStmt = conn.prepareStatement(insertQuery)) {
                insertStmt.setString(1, u.trim());
                insertStmt.setString(2, p.trim());
                insertStmt.executeUpdate();
                log("[REGISTER]: Account cusub waxaa lagu daray Database-ka: " + u);
                return true;
            }
            
        } catch (SQLException e) {
            log("[DB Error - Register]: " + e.getMessage());
            return false;
        }
    }

    // Hubinta in user-ku ka diiwaangishan yahay Database-ka (Add Contact loogu talagalay)
    public static boolean isUserRegistered(String username) {
        if (username == null || username.trim().isEmpty()) return false;
        String query = "SELECT * FROM users WHERE LOWER(username) = LOWER(?)";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, username.trim());
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    public static synchronized boolean registerActiveUser(String username, ClientHandler handler) {
        String key = username.toLowerCase();
        if (activeClients.containsKey(key)) {
            return false;
        }
        activeClients.put(key, handler);

        SwingUtilities.invokeLater(() -> onlineListModel.addElement(username));
        log("[CONNECT]: " + username + " wuu soo galay online.");

        broadcastStatusUpdate(username, "ONLINE");
        return true;
    }

    public static synchronized void removeActiveUser(String username) {
        if (username != null) {
            String key = username.toLowerCase();
            if (activeClients.containsKey(key)) {
                activeClients.remove(key);

                SwingUtilities.invokeLater(() -> onlineListModel.removeElement(username));
                log("[DISCONNECT]: " + username + " wuu ka baxay.");

                broadcastStatusUpdate(username, "OFFLINE");
            }
        }
    }

    public static void broadcastStatusUpdate(String username, String status) {
        for (Map.Entry<String, ClientHandler> entry : activeClients.entrySet()) {
            if (!entry.getKey().equalsIgnoreCase(username)) {
                entry.getValue().sendMessage("STATUS_UPDATE:" + username + ":" + status);
            }
        }
    }

    public static boolean isUserOnline(String username) {
        return activeClients.containsKey(username.toLowerCase());
    }

    public static ClientHandler getHandlerForUser(String username) {
        return activeClients.get(username.toLowerCase());
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ChatServer::new);
    }
}