package javachatsystem;

import java.io.*;
import java.net.*;
import java.util.*;

public class ChatServer {
    private static final int PORT = 5050;

    private static final Map<String, String> users = new HashMap<>(); 
    private static final Map<String, Set<String>> userContacts = new HashMap<>(); 
    private static final Map<String, List<String>> pendingMessages = new HashMap<>(); 
    private static final Map<String, ClientHandler> activeClients = new HashMap<>(); 

    public static void main(String[] args) {
        System.out.println("==========================================");
        System.out.println("🚀 WhatsApp Memory Server Running on Port " + PORT);
        System.out.println("==========================================");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket socket = serverSocket.accept();
                new ClientHandler(socket).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static synchronized boolean registerUser(String username, String password) {
        if (users.containsKey(username)) return false;
        users.put(username, password);
        userContacts.put(username, new HashSet<>());
        return true;
    }

    public static synchronized boolean validateLogin(String username, String password) {
        return users.containsKey(username) && users.get(username).equals(password);
    }

    public static synchronized boolean userExists(String username) {
        return users.containsKey(username);
    }

    public static synchronized boolean addContact(String owner, String contactName) {
        if (!users.containsKey(contactName)) return false;
        userContacts.computeIfAbsent(owner, k -> new HashSet<>()).add(contactName);
        return true;
    }

    public static synchronized Set<String> getContacts(String owner) {
        return userContacts.getOrDefault(owner, new HashSet<>());
    }

    // --- ONLINE / OFFLINE STATUS LOGIC ---
    public static synchronized boolean addOnlineUser(String username, ClientHandler handler) {
        if (activeClients.containsKey(username)) return false;
        activeClients.put(username, handler);
        broadcastStatusChange(username, "ONLINE");
        return true;
    }

    public static synchronized void removeOnlineUser(String username) {
        if (username != null && activeClients.containsKey(username)) {
            activeClients.remove(username);
            broadcastStatusChange(username, "OFFLINE");
        }
    }

    public static synchronized boolean isUserOnline(String username) {
        return activeClients.containsKey(username);
    }

    private static synchronized void broadcastStatusChange(String username, String status) {
        for (Map.Entry<String, ClientHandler> entry : activeClients.entrySet()) {
            if (!entry.getKey().equals(username)) {
                entry.getValue().sendMessage("USER_STATUS:" + username + ":" + status);
            }
        }
    }

    // --- MESSAGE ROUTING & TICKS LOGIC ---
    public static synchronized void routePrivateMessage(String msgId, String sender, String recipient, String timestamp, String message) {
        // U dar kontagga labada dhinac
        addContact(recipient, sender);
        addContact(sender, recipient);
        
        ClientHandler recipientHandler = activeClients.get(recipient);
        if (recipientHandler != null) {
            // 1. Ogeysii recipient-ka in kontag cusub loo soo kordhiyay iyadoo xaaladiisa lagu darayo
            String senderStatus = isUserOnline(sender) ? "ONLINE" : "OFFLINE";
            recipientHandler.sendMessage("CONTACT_ADDED:" + sender + ":" + senderStatus);

            // 2. Fariinta gaadhsii recipient-ka
            recipientHandler.sendMessage("MSG:" + msgId + ":" + sender + ":" + timestamp + ":" + message);
            
            // 3. Ogeysii sender-ka in fariintii la gaadhsiiyay (Delivered - Gray Ticks)
            ClientHandler senderHandler = activeClients.get(sender);
            if (senderHandler != null) {
                senderHandler.sendMessage("MSG_DELIVERED:" + msgId);
            }
        } else {
            String formattedMsg = msgId + ":" + sender + ":" + timestamp + ":" + message;
            pendingMessages.computeIfAbsent(recipient, k -> new ArrayList<>()).add(formattedMsg);
        }
    }

    public static synchronized void notifyReadReceipt(String msgId, String sender, String reader) {
        ClientHandler senderHandler = activeClients.get(sender);
        if (senderHandler != null) {
            // Ogeysii sender-ka in fariinta la akhriyay (Read - Blue Ticks)
            senderHandler.sendMessage("MSG_READ:" + msgId + ":" + reader);
        }
    }

    public static synchronized List<String> getAndClearPendingMessages(String recipient) {
        List<String> msgs = pendingMessages.getOrDefault(recipient, new ArrayList<>());
        pendingMessages.remove(recipient);
        return msgs;
    }
}