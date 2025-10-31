package com.chatapp;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.Socket;
import java.util.ArrayList;

@SuppressWarnings({ "serial", "unused" })
public class ChatClientGUI extends JFrame implements Runnable {

    // GUI Components
    private JTextArea chatArea; 
    private JTextField messageField; 
    private JButton sendButton;
    private JList<String> userList; // New component for list of users
    private DefaultListModel<String> listModel; // Model to update the user list
    
    // Networking Components
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private final String serverAddress = "localhost";
    private final int serverPort = 8080; 

    public ChatClientGUI() {
        super("Java Chat Client");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        
        // --- 1. Setup Chat Panel (Main Communication Area) ---
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setBackground(new Color(240, 240, 255)); // Light background for chat
        JScrollPane chatScrollPane = new JScrollPane(chatArea);

        messageField = new JTextField();
        sendButton = new JButton("Send");
        
        // Input panel (text field and button)
        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.add(messageField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

        // Main Chat panel (chat area and input panel)
        JPanel mainChatPanel = new JPanel(new BorderLayout());
        mainChatPanel.add(chatScrollPane, BorderLayout.CENTER);
        mainChatPanel.add(inputPanel, BorderLayout.SOUTH);
        
        // --- 2. Setup User List Panel ---
        listModel = new DefaultListModel<>();
        userList = new JList<>(listModel);
        userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane userScrollPane = new JScrollPane(userList);
        userScrollPane.setPreferredSize(new Dimension(100, 0)); // Give it a fixed width
        
        // --- 3. Setup Split Pane Layout (The "Nice" UI) ---
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, mainChatPanel, userScrollPane);
        splitPane.setResizeWeight(0.7); // 70% for chat, 30% for user list
        
        add(splitPane, BorderLayout.CENTER);

        // --- 4. Final Window Settings ---
        setSize(700, 500); // Larger window size
        setLocationRelativeTo(null); // Center window
        setVisible(true);

        // --- 5. Add Action Listeners ---
        sendButton.addActionListener(e -> sendMessage());
        messageField.addActionListener(e -> sendMessage());
    }
    
    // ... (connect, run, sendMessage, closeConnection methods remain the same for now)
    // ... (We will update the 'run' method in the next step to process the user list)
    
    // Keep the main methods outside of the constructor
    public void connect() {
        try {
            socket = new Socket(serverAddress, serverPort);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            chatArea.append("Connected to server: " + serverAddress + ":" + serverPort + "\n");
        } catch (IOException e) {
            chatArea.append("Error: Could not connect to server.\n");
            dispose(); 
        }
    }
    
    @Override
    public void run() {
        try {
            String serverResponse;
            while ((serverResponse = in.readLine()) != null) {
                chatArea.append(serverResponse + "\n");
            }
        } catch (IOException e) {
            chatArea.append("Connection lost to server.\n");
        } finally {
            closeConnection();
        }
    }

    private void sendMessage() {
        String message = messageField.getText();
        if (out != null && !message.trim().isEmpty()) {
            out.println(message);
            messageField.setText("");
        }
    }
    
    private void closeConnection() {
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null) socket.close();
        } catch (IOException e) {}
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ChatClientGUI client = new ChatClientGUI();
            client.connect();
            new Thread(client).start();
        });
    }
}