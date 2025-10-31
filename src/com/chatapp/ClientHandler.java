package com.chatapp;

import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {

    private final Socket clientSocket;
    private final ChatServer server; // Reference to the main server
    private PrintWriter out;
    private BufferedReader in;
    private String username;

    public ClientHandler(Socket socket, ChatServer server) {
        this.clientSocket = socket;
        this.server = server;
        
        try {
            // Setup the communication streams
            this.out = new PrintWriter(clientSocket.getOutputStream(), true);
            this.in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            
            // This is the line that uses the new getClients() method
            this.username = "User" + (server.getClients().size()); 
            System.out.println("Assigned temporary username: " + username);

        } catch (IOException e) {
            System.err.println("Error setting up streams for client: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        String message;
        try {
            // 1. Announce the client's connection to everyone
            server.broadcastMessage(username + " has joined the chat.", this);

            // 2. Continuous loop to read messages from this client
            while ((message = in.readLine()) != null) {
                // Prepend the username before broadcasting
                String fullMessage = "[" + username + "]: " + message; 
                System.out.println("Broadcasting: " + fullMessage);
                server.broadcastMessage(fullMessage, this); 
            }
        } catch (IOException e) {
            System.out.println(username + " disconnected.");
        } finally {
            // Clean up resources and notify the server to remove this handler
            closeResources();
            server.removeClient(this); 
        }
    }
    
    public void sendMessage(String message) {
        out.println(message);
    }
    
    private void closeResources() {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (clientSocket != null) clientSocket.close();
        } catch (IOException e) {
            // Ignore
        }
    }
}