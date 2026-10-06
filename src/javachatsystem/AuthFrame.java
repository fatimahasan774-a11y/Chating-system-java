package javachatsystem;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class AuthFrame extends JFrame {
    private JTextField userField;
    private JPasswordField passField;
    private JButton loginBtn, registerBtn;

    public AuthFrame() {
        setTitle("Java Chat System - Login / Register");
        setSize(380, 240);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(false);

        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Username Label & Field
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.3;
        mainPanel.add(new JLabel("Username:"), gbc);

        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 0.7;
        userField = new JTextField();
        mainPanel.add(userField, gbc);

        // Password Label & Field
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.3;
        mainPanel.add(new JLabel("Password:"), gbc);

        gbc.gridx = 1; gbc.gridy = 1; gbc.weightx = 0.7;
        passField = new JPasswordField();
        mainPanel.add(passField, gbc);

        // Buttons
        JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        loginBtn = new JButton("Login");
        registerBtn = new JButton("Register");
        buttonPanel.add(loginBtn);
        buttonPanel.add(registerBtn);

        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2; gbc.insets = new Insets(15, 6, 6, 6);
        mainPanel.add(buttonPanel, gbc);

        add(mainPanel);

        loginBtn.addActionListener(e -> authenticate("LOGIN"));
        registerBtn.addActionListener(e -> authenticate("REGISTER"));
    }

    private void authenticate(String type) {
        String u = userField.getText().trim();
        String p = new String(passField.getPassword()).trim();

        if (u.isEmpty() || p.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter your username and password.fafath!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        loginBtn.setEnabled(false);
        registerBtn.setEnabled(false);

        new Thread(() -> {
            try {
                Socket socket = new Socket("localhost", 12345);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

                // Soo dir fariinta
                out.println(type + ":" + u + ":" + p);
                String resp = in.readLine();

                SwingUtilities.invokeLater(() -> {
                    if (resp != null && (resp.startsWith("LOGIN_SUCCESS") || resp.startsWith("REGISTER_SUCCESS"))) {
                        if (type.equals("REGISTER")) {
                            JOptionPane.showMessageDialog(this, "Your account has been created! Now log in..", "Success", JOptionPane.INFORMATION_MESSAGE);
                            try { socket.close(); } catch (IOException ignored) {}
                        } else {
                            // LOGIN SUCCESS
                            dispose();
                            ChatClient client = new ChatClient(u, socket, in, out);
                            client.setVisible(true);
                        }
                    } else {
                        // AUTH FAILED
                        String errorMsg = "The information you entered is incorrect.";
                        if (resp != null && resp.contains(":")) {
                            errorMsg = resp.split(":", 2)[1];
                        }
                        JOptionPane.showMessageDialog(this, errorMsg, "Auth Error", JOptionPane.ERROR_MESSAGE);
                        try { socket.close(); } catch (IOException ignored) {}
                    }
                    resetButtons();
                });
            } catch (IOException ex) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, "I can’t connect to the server! Please make sure the ChatServer is running..", "Connection Error", JOptionPane.ERROR_MESSAGE);
                    resetButtons();
                });
            }
        }).start();
    }

    private void resetButtons() {
        loginBtn.setEnabled(true);
        registerBtn.setEnabled(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new AuthFrame().setVisible(true));
    }
}