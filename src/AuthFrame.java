package javachatsystem;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.Socket;

public class AuthFrame extends JFrame {
    private JTextField userField;
    private JPasswordField passField;

    public AuthFrame() {
        setTitle("WhatsApp - Login");
        setSize(380, 380);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(false);

        JPanel panel = new JPanel(null);
        panel.setBackground(new Color(240, 242, 245));

        JLabel title = new JLabel("💬 WhatsApp", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(new Color(0, 168, 132));
        title.setBounds(20, 25, 320, 35);

        userField = new JTextField();
        userField.setBounds(40, 90, 280, 38);

        passField = new JPasswordField();
        passField.setBounds(40, 150, 280, 38);

        JButton loginBtn = new JButton("Login");
        loginBtn.setBounds(40, 220, 130, 42);
        loginBtn.setBackground(new Color(0, 168, 132));
        loginBtn.setForeground(Color.WHITE);

        JButton regBtn = new JButton("Register");
        regBtn.setBounds(190, 220, 130, 42);
        regBtn.setBackground(new Color(52, 73, 94));
        regBtn.setForeground(Color.WHITE);

        panel.add(title);
        panel.add(userField);
        panel.add(passField);
        panel.add(loginBtn);
        panel.add(regBtn);
        add(panel);

        loginBtn.addActionListener(e -> executeAuth("LOGIN"));
        regBtn.addActionListener(e -> executeAuth("REGISTER"));
    }

    private void executeAuth(String type) {
        String u = userField.getText().trim();
        String p = new String(passField.getPassword()).trim();
        if (u.isEmpty() || p.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Fadlan geli username iyo password!");
            return;
        }

        try {
            Socket socket = new Socket("localhost", 5050);
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            out.println(type + ":" + u + ":" + p);
            String resp = in.readLine();

            if (resp != null && resp.startsWith("AUTH_SUCCESS")) {
                if (type.equals("REGISTER")) {
                    JOptionPane.showMessageDialog(this, "Akaounka waa la sameeyay! Hadda Login dheh.");
                } else {
                    dispose();
                    new ChatClient(u, socket, in, out);
                }
            } else {
                JOptionPane.showMessageDialog(this, resp.split(":")[1], "Auth Error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Server-ka lama xidhiidhin karo!", "Network Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new AuthFrame().setVisible(true));
    }
}