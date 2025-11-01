package com.chatapp;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChatServer {

    private final int port;
    private ServerSocket serverSocket;
    
    // Use a synchronized list to safely handle client threads accessing it concurrently
    private final List<ClientHandler> clients = Collections.synchronizedList(new ArrayList<>()); 

    public ChatServer(int port) {
        this.port = port;
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("✅ Server started. Listening on port " + port);

            while (true) {
                Socket clientSocket = serverSocket.accept(); 
                System.out.println("🔗 New client connected: " + clientSocket);

                // Create and add the handler
                ClientHandler clientHandler = new ClientHandler(clientSocket, this);
                clients.add(clientHandler); 
                
                // Start the handler thread
                new Thread(clientHandler).start();
                
                System.out.println("Current active clients: " + clients.size());
            }

        } catch (IOException e) {
            System.err.println("Server exception: " + e.getMessage());
        }
    }

    // ⭐ FIX: Added the public getter method for the clients list
    public List<ClientHandler> getClients() {
        return clients;
    }
    
    /**
     * Sends a message to all connected clients.
     */
    public void broadcastMessage(String message, ClientHandler sender) {
        // Iterate over a copy of the list to prevent ConcurrentModificationException
        for (ClientHandler client : clients) {
            client.sendMessage(message);
        }
    }
    
    /**
     * Called by ClientHandler when a client disconnects.
     */
    public void removeClient(ClientHandler client) {
        clients.remove(client);
        System.out.println("Client removed. Current active clients: " + clients.size());
        broadcastMessage("A client has disconnected.", null);
    }


    public static void main(String[] args) {
        ChatServer server = new ChatServer(8080);
        server.start();
    }
}