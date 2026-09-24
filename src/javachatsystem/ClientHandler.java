package javachatsystem;

import java.io.*;
import java.net.*;
import java.util.List;
import java.util.Set;

public class ClientHandler extends Thread {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String username;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            String request;
            while ((request = in.readLine()) != null) {
                if (request.trim().isEmpty()) continue;

                String[] parts = request.split(":", 5);
                String command = parts[0];

                if (command.equals("REGISTER")) {
                    if (parts.length >= 3) {
                        boolean ok = ChatServer.registerUser(parts[1], parts[2]);
                        out.println(ok ? "AUTH_SUCCESS" : "AUTH_FAILED:Username-kan waa la qaatay!");
                    }
                } else if (command.equals("LOGIN")) {
                    if (parts.length >= 3) {
                        boolean ok = ChatServer.validateLogin(parts[1], parts[2]);
                        if (ok) {
                            this.username = parts[1];
                            if (ChatServer.addOnlineUser(username, this)) {
                                out.println("AUTH_SUCCESS");
                                sendPendingMessages();
                                
                                // Ogeysii kuwo kale oo dhan xaalada kan cusub
                                sendInitialContactStatuses();
                            } else {
                                out.println("AUTH_FAILED:User-kan hore buu u online yahay!");
                            }
                        } else {
                            out.println("AUTH_FAILED:Username ama Password ma saxa!");
                        }
                    }
                } else if (command.equals("ADD_CONTACT")) {
                    if (parts.length >= 2) {
                        String contact = parts[1];
                        boolean added = ChatServer.addContact(username, contact);
                        if (added) {
                            // Kontagga kale iyagana u dar
                            ChatServer.addContact(contact, username);
                            
                            String status = ChatServer.isUserOnline(contact) ? "ONLINE" : "OFFLINE";
                            out.println("CONTACT_ADDED:" + contact + ":" + status);
                            
                            // Ogeysii kontagga la darsadayna
                            ClientHandler otherHandler = getHandlerForUser(contact);
                            if (otherHandler != null) {
                                otherHandler.sendMessage("CONTACT_ADDED:" + username + ":ONLINE");
                            }
                        } else {
                            out.println("CONTACT_FAILED:Qofkani ma laha WhatsApp Account!");
                        }
                    }
                } else if (command.equals("GET_CONTACTS")) {
                    Set<String> contacts = ChatServer.getContacts(username);
                    StringBuilder sb = new StringBuilder();
                    for (String c : contacts) {
                        String status = ChatServer.isUserOnline(c) ? "ONLINE" : "OFFLINE";
                        sb.append(c).append(",").append(status).append(";");
                    }
                    out.println("CONTACT_LIST:" + sb.toString());
                } else if (command.equals("SEND_MSG")) {
                    if (parts.length >= 5) {
                        String msgId = parts[1];
                        String recipient = parts[2];
                        String timestamp = parts[3];
                        String msg = parts[4];

                        if (!ChatServer.userExists(recipient)) {
                            out.println("MSG_FAILED:Qofkani ma laha WhatsApp Account!");
                        } else {
                            ChatServer.routePrivateMessage(msgId, username, recipient, timestamp, msg);
                        }
                    }
                } else if (command.equals("MARK_READ")) {
                    if (parts.length >= 3) {
                        String msgId = parts[1];
                        String sender = parts[2];
                        ChatServer.notifyReadReceipt(msgId, sender, username);
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("Disconnected: " + username);
        } finally {
            ChatServer.removeOnlineUser(username);
            try { socket.close(); } catch (IOException e) { e.printStackTrace(); }
        }
    }

    private void sendInitialContactStatuses() {
        Set<String> contacts = ChatServer.getContacts(username);
        for (String c : contacts) {
            String status = ChatServer.isUserOnline(c) ? "ONLINE" : "OFFLINE";
            out.println("USER_STATUS:" + c + ":" + status);
        }
    }

    private ClientHandler getHandlerForUser(String targetUser) {
        // Habka tooska ah ee lagu helayo active handler-ka ku jira ChatServer
        return null; // Server-ka ayaa maareeya routePrivateMessage
    }

    private void sendPendingMessages() {
        List<String> pending = ChatServer.getAndClearPendingMessages(username);
        for (String msgStr : pending) {
            out.println("MSG:" + msgStr);
        }
    }

    public void sendMessage(String msg) {
        if (out != null) out.println(msg);
    }
}