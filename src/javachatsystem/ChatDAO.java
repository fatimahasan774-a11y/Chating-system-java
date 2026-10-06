package javachatsystem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ChatDAO {

    public static boolean registerUser(String username, String password) {
        String u = username.trim();
        String p = password.trim();

        if (userExists(u)) {
            return false;
        }

        String sql = "INSERT INTO users (username, password, status) VALUES (?, ?, 'OFFLINE')";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, u);
            stmt.setString(2, p);
            
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean validateUser(String username, String password) {
        String sql = "SELECT * FROM users WHERE TRIM(username) = ? AND TRIM(password) = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, username.trim());
            stmt.setString(2, password.trim());
            
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void updateUserStatus(String username, String status) {
        String sql = "UPDATE users SET status = ? WHERE LOWER(TRIM(username)) = LOWER(TRIM(?))";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, status);
            stmt.setString(2, username.trim());
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static boolean userExists(String username) {
        String sql = "SELECT COUNT(*) FROM users WHERE LOWER(TRIM(username)) = LOWER(TRIM(?))";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, username.trim());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static boolean addContact(String currentUser, String contactUser) {
        String currentU = currentUser.trim();
        String targetU = contactUser.trim();

        if (!userExists(targetU) || currentU.equalsIgnoreCase(targetU)) {
            return false; 
        }

        String sql = "INSERT IGNORE INTO contacts (user_name, contact_name) VALUES (?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, currentU);
            stmt.setString(2, targetU);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false; 
        }
    }

    public static List<String> getContacts(String username) {
        List<String> contacts = new ArrayList<>();
        String sql = "SELECT contact_name FROM contacts WHERE LOWER(TRIM(user_name)) = LOWER(TRIM(?))";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, username.trim());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    contacts.add(rs.getString("contact_name"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return contacts;
    }

    public static boolean saveMessage(String sender, String receiver, String message) {
        String sql = "INSERT INTO messages (sender_username, receiver_username, message, time_sent) VALUES (?, ?, ?, CURTIME())";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, sender.trim());
            stmt.setString(2, receiver.trim());
            stmt.setString(3, message.trim());
            
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static List<String> getChatHistory(String user1, String user2) {
        List<String> history = new ArrayList<>();
        String sql = "SELECT sender_username, message FROM messages " +
                     "WHERE (LOWER(TRIM(sender_username)) = LOWER(TRIM(?)) AND LOWER(TRIM(receiver_username)) = LOWER(TRIM(?))) " +
                     "OR (LOWER(TRIM(sender_username)) = LOWER(TRIM(?)) AND LOWER(TRIM(receiver_username)) = LOWER(TRIM(?))) " +
                     "ORDER BY id ASC";
                     
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, user1);
            stmt.setString(2, user2);
            stmt.setString(3, user2);
            stmt.setString(4, user1);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String sender = rs.getString("sender_username");
                    String msg = rs.getString("message");
                    
                    if (sender.equalsIgnoreCase(user1)) {
                        history.add("Me -> " + user2 + ": " + msg);
                    } else {
                        history.add(sender + ": " + msg);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return history;
    }
}