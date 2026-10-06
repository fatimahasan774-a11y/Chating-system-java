package javachatsystem;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class ClientHandler implements Runnable {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String username;

    private static ConcurrentHashMap<String, List<String>> userContacts = new ConcurrentHashMap<>();
    private static ConcurrentHashMap<String, List<String>> chatHistory = new ConcurrentHashMap<>();

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            String request;
            
            // 1. AUTHENTICATION LOOP
            while ((request = in.readLine()) != null) {
                if (request.startsWith("REGISTER:")) {
                    String[] parts = request.split(":", 3);
                    if (parts.length == 3) {
                        String u = parts[1].trim();
                        String p = parts[2].trim();

                        if (ChatServer.registerUser(u, p)) {
                            out.println("REGISTER_SUCCESS");
                        } else {
                            out.println("AUTH_ERROR:Username already taken!");
                        }
                    } else {
                        out.println("AUTH_ERROR:Incomplete registration data");
                    }
                } 
                else if (request.startsWith("LOGIN:")) {
                    String[] parts = request.split(":", 3);
                    if (parts.length == 3) {
                        String u = parts[1].trim();
                        String p = parts[2].trim();

                        if (ChatServer.validateUser(u, p)) {
                            if (ChatServer.isUserOnline(u)) {
                                out.println("AUTH_ERROR:User is already online!");
                            } else {
                                this.username = u;
                                ChatServer.registerActiveUser(username, this);
                                out.println("LOGIN_SUCCESS:" + username);
                                break;
                            }
                        } else {
                            out.println("AUTH_ERROR:Invalid username or password!");
                        }
                    } else {
                        out.println("AUTH_ERROR:Incomplete login data");
                    }
                }
            }

            // 2. CHAT MESSAGES & CONTACTS LOOP
            if (username != null) {
                sendUserContacts();

                while ((request = in.readLine()) != null) {
                    
                    if (request.startsWith("MSG_ALL:")) {
                        String msg = request.substring(8);
                        
                        // Send message to all online users
                        for (String user : ChatServer.getActiveUsernames()) {
                            if (!user.equalsIgnoreCase(username)) {
                                ClientHandler ch = ChatServer.getHandlerForUser(user);
                                if (ch != null) {
                                    ch.sendMessage("MSG_ALL_FROM:" + username + ":" + msg);
                                }
                            }
                        }
                    } 
                    else if (request.startsWith("MSG_PRIVATE:")) {
                        String[] parts = request.split(":", 3);
                        if (parts.length == 3) {
                            String targetUser = parts[1].trim();
                            String msg = parts[2].trim();

                            saveInMemoryHistory(username, targetUser, username + ": " + msg);

                            ClientHandler ch = ChatServer.getHandlerForUser(targetUser);
                            if (ch != null) {
                                ch.sendMessage("MSG_FROM:" + username + ":" + msg);
                            } else {
                                sendMessage("SYSTEM_MSG:" + targetUser + " is offline.");
                            }
                        }
                    } 
                    else if (request.startsWith("ADD_CONTACT:")) {
                        String[] parts = request.split(":", 3);
                        String targetU = "";
                        
                        if (parts.length == 3) {
                            targetU = parts[2].trim();
                        } else if (parts.length == 2) {
                            targetU = parts[1].trim();
                        }

                        if (targetU.isEmpty()) {
                            out.println("ADD_CONTACT_RESP:FAILED:Fadlan geli magaca qofka!");
                        } else if (targetU.equalsIgnoreCase(username)) {
                            out.println("ADD_CONTACT_RESP:FAILED:Isku ma dari kartid magacaaga!");
                        } else if (!ChatServer.isUserRegistered(targetU)) {
                            // Returns this exact error if the user does not exist in the database
                            out.println("ADD_CONTACT_RESP:FAILED:User-kan ma leh account system-ka dhexdiisa!");
                        } else {
                            addInMemoryContact(username, targetU);
                            boolean isOnline = ChatServer.isUserOnline(targetU);
                            out.println("ADD_CONTACT_RESP:SUCCESS:" + targetU + ":" + (isOnline ? "Online" : "Offline"));
                        }
                    }
                    else if (request.equals("GET_CONTACTS")) {
                        sendUserContacts();
                    }
                    else if (request.startsWith("GET_HISTORY:")) {
                        String[] parts = request.split(":", 2);
                        if (parts.length == 2) {
                            String targetUser = parts[1].trim();
                            List<String> history = getInMemoryHistory(username, targetUser);
                            for (String chatMsg : history) {
                                out.println("CHAT_HISTORY_ITEM:" + targetUser + ":" + chatMsg);
                            }
                        }
                    }
                    else if (request.equals("LOGOUT")) {
                        break;
                    }
                }
            }

        } catch (IOException e) {
            System.out.println("Client Disconnected (" + username + "): " + e.getMessage());
        } finally {
            if (username != null) {
                ChatServer.removeActiveUser(username);
            }
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }

    private void sendUserContacts() {
        List<String> contacts = userContacts.getOrDefault(username.toLowerCase(), new ArrayList<>());
        for (String contact : contacts) {
            boolean isOnline = ChatServer.isUserOnline(contact);
            out.println("CONTACT_ITEM:" + contact + ":" + (isOnline ? "Online" : "Offline"));
        }
    }

    private synchronized void addInMemoryContact(String u1, String u2) {
        userContacts.computeIfAbsent(u1.toLowerCase(), k -> new ArrayList<>());
        if (!userContacts.get(u1.toLowerCase()).contains(u2.toLowerCase())) {
            userContacts.get(u1.toLowerCase()).add(u2.toLowerCase());
        }
    }

    private synchronized void saveInMemoryHistory(String sender, String receiver, String msg) {
        String key = getHistoryKey(sender, receiver);
        chatHistory.computeIfAbsent(key, k -> new ArrayList<>()).add(msg);
    }

    private List<String> getInMemoryHistory(String user1, String user2) {
        String key = getHistoryKey(user1, user2);
        return chatHistory.getOrDefault(key, new ArrayList<>());
    }

    private String getHistoryKey(String u1, String u2) {
        return u1.compareToIgnoreCase(u2) < 0 
            ? u1.toLowerCase() + "_" + u2.toLowerCase() 
            : u2.toLowerCase() + "_" + u1.toLowerCase();
    }

    public void sendMessage(String msg) {
        if (out != null) {
            out.println(msg);
        }
    }
}