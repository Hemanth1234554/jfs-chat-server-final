package com.chatapp;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import com.chatapp.DatabaseManager.Message; 

import java.net.InetSocketAddress;
import java.text.SimpleDateFormat; 
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WebSocketChatServer extends WebSocketServer {

    private static DatabaseManager dbManager;
    
    // Maps a user_id (Integer) to their active WebSocket connection.
    private Map<Integer, WebSocket> onlineUsers = new ConcurrentHashMap<>();

    /**
     * Constructor: Sets up the server on a specific port.
     */
    public WebSocketChatServer(int port) {
        super(new InetSocketAddress(port));
        System.out.println("Attempting to start WebSocket server on port " + port);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        System.out.println("🔗 New client connected: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        System.out.println("👋 Client disconnected: " + conn.getRemoteSocketAddress());
        
        Integer userId = conn.getAttachment();
        
        if (userId != null) {
            onlineUsers.remove(userId);
            // Tell everyone the user list has changed (they are offline)
            broadcastUserListUpdate(); 
            
            // Tell anyone chatting with them that they stopped typing
            broadcastTyping(userId, -1, false); // -1 for "all"
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        try {
            JSONObject json = new JSONObject(message);
            String type = json.getString("type");
            
            Integer userId = conn.getAttachment(); // User ID if logged in

            // Auth actions can be done before being fully logged in
            if (type.equals("login")) {
                handleLogin(conn, json);
                return;
            }
            if (type.equals("register")) {
                handleRegistration(conn, json);
                return;
            }

            // All actions below require a valid, logged-in user
            if (userId == null) {
                // Check for session reconnect
                if (type.equals("login") && json.getString("password").equals("SESSION_RECONNECT")) {
                    handleLogin(conn, json); // Allow reconnect
                } else {
                    sendJsonError(conn, "Authentication required. Please log in again.");
                }
                return;
            }

            switch (type) {
                case "get_contact_list": // Renamed from "get_user_list"
                    handleGetContactList(conn, userId);
                    break;
                case "get_message_history":
                    handleGetMessageHistory(conn, userId, json);
                    break;
                case "private_message":
                    handlePrivateMessage(conn, userId, json);
                    break;
                case "start_typing":
                    handleTyping(userId, json, true);
                    break;
                case "stop_typing":
                    handleTyping(userId, json, false);
                    break;
                    
                // --- NEW FRIEND SYSTEM LOGIC ---
                case "search_users":
                    handleUserSearch(conn, userId, json);
                    break;
                case "send_friend_request":
                    handleSendFriendRequest(conn, userId, json);
                    break;
                case "action_friend_request":
                    handleActionFriendRequest(conn, userId, json);
                    break;
                    
                default:
                    sendJsonError(conn, "Unknown message type.");
            }
            
        } catch (JSONException e) {
            System.err.println("Failed to parse JSON or missing 'type': " + message);
            sendJsonError(conn, "Invalid JSON format.");
        }
    }
    
    // --- Message Handling Methods ---
    
    private void handleRegistration(WebSocket conn, JSONObject json) {
        String user = json.getString("username");
        String pass = json.getString("password");
        
        if (dbManager.registerUser(user, pass)) {
            sendJsonMessage(conn, createJsonMessage("register_success", "Registration successful. Please log in."));
        } else {
            sendJsonError(conn, "Registration failed. Username may already exist.");
        }
    }
    
    private void handleLogin(WebSocket conn, JSONObject json) {
        String user = json.getString("username");
        String pass = json.getString("password");
        
        // Handle session reconnect
        if (pass.equals("SESSION_RECONNECT")) {
            Integer userId = dbManager.getUserId(user); // Get ID from username
            if (userId != -1) {
                System.out.println("User reconnected: " + user);
                conn.setAttachment(userId); // Re-attach the ID
                onlineUsers.put(userId, conn);
                broadcastUserListUpdate();
                return;
            } else {
                 sendJsonError(conn, "Session reconnect failed. User not found.");
                 return;
            }
        }

        int userId = dbManager.loginUser(user, pass);
        
        if (userId != -1) { // Login successful
            conn.setAttachment(userId); 
            onlineUsers.put(userId, conn);
            
            JSONObject loginData = new JSONObject();
            loginData.put("username", user);
            loginData.put("userId", userId);
            sendJsonMessage(conn, createJsonMessage("login_success", loginData));
            
            // Tell *everyone* that this user is now online
            broadcastUserListUpdate();
            
        } else { // Login failed
            sendJsonError(conn, "Login failed. Invalid username or password.");
        }
    }
    
    private void handleGetMessageHistory(WebSocket conn, int senderId, JSONObject json) {
        String targetUsername = json.getString("withUser");
        int targetId = dbManager.getUserId(targetUsername);
        
        if (targetId == -1) { sendJsonError(conn, "User not found."); return; }
        
        List<Message> history = dbManager.getMessageHistory(senderId, targetId);
        
        JSONArray historyJson = new JSONArray();
        for (Message msg : history) {
            historyJson.put(createJsonMessageFromObject(msg));
        }
        
        JSONObject response = new JSONObject();
        response.put("type", "message_history");
        response.put("withUser", targetUsername);
        response.put("history", historyJson);
        sendJsonMessage(conn, response.toString());
    }

    private void handlePrivateMessage(WebSocket conn, int senderId, JSONObject json) {
        String receiverUsername = json.getString("receiverUsername");
        String messageText = json.getString("message");
        
        int receiverId = dbManager.getUserId(receiverUsername);
        
        if (receiverId == -1) { sendJsonError(conn, "Error: User '" + receiverUsername + "' does not exist."); return; }
        
        Message savedMessage = dbManager.savePrivateMessage(senderId, receiverId, messageText);
        
        if (savedMessage == null) { sendJsonError(conn, "Error: Could not save message."); return; }
        
        String pmJsonString = createJsonMessage("private_message_incoming", createJsonMessageFromObject(savedMessage));

        // Send to receiver (if online)
        WebSocket receiverConn = onlineUsers.get(receiverId);
        if (receiverConn != null) {
            sendJsonMessage(receiverConn, pmJsonString);
        }
        
        // Send copy back to sender
        sendJsonMessage(conn, pmJsonString);
        
        broadcastTyping(senderId, receiverId, false);
    }
    
    private void handleTyping(int senderId, JSONObject json, boolean isTyping) {
        String receiverUsername = json.getString("toUser");
        int receiverId = dbManager.getUserId(receiverUsername);
        
        if (receiverId != -1) {
            broadcastTyping(senderId, receiverId, isTyping);
        }
    }

    // --- NEW FRIEND SYSTEM HANDLERS ---
    
    /**
     * Searches for users who are not already friends.
     */
    private void handleUserSearch(WebSocket conn, int currentUserId, JSONObject json) {
        String query = json.getString("query").toLowerCase();
        List<String> allUsers = dbManager.getAllUsernames();
        List<String> friends = dbManager.getFriendList(currentUserId);
        
        JSONArray searchResults = new JSONArray();
        for (String user : allUsers) {
            // Include user if name matches, they aren't our friend, and they aren't us
            if (user.toLowerCase().contains(query) && 
                !friends.contains(user) && 
                dbManager.getUserId(user) != currentUserId) {
                searchResults.put(user);
            }
        }
        
        JSONObject response = new JSONObject();
        response.put("type", "search_results");
        response.put("users", searchResults);
        sendJsonMessage(conn, response.toString());
    }
    
    /**
     * Sends a friend request.
     */
    private void handleSendFriendRequest(WebSocket conn, int senderId, JSONObject json) {
        String receiverUsername = json.getString("username");
        String statusMessage = dbManager.sendFriendRequest(senderId, receiverUsername);
        
        // Send a status update back to the sender
        if (statusMessage.equals("Friend request sent.")) {
            // If success, also notify the receiver (if they are online)
            int receiverId = dbManager.getUserId(receiverUsername);
            WebSocket receiverConn = onlineUsers.get(receiverId);
            if (receiverConn != null) {
                // Send them their new pending request list
                handleGetContactList(receiverConn, receiverId); 
            }
            sendJsonMessage(conn, createJsonMessage("request_sent", statusMessage));
        } else {
            // Send an error if it failed (e.g., "Already friends")
            sendJsonError(conn, statusMessage);
        }
    }
    
    /**
     * Accepts or Rejects a friend request.
     */
    private void handleActionFriendRequest(WebSocket conn, int currentUserId, JSONObject json) {
        String senderUsername = json.getString("username");
        boolean didAccept = json.getBoolean("accept");
        
        int senderId = dbManager.getUserId(senderUsername);
        if (senderId == -1) {
            sendJsonError(conn, "User not found.");
            return;
        }
        
        int status = didAccept ? 1 : 2; // 1 = Accepted, 2 = Rejected
        boolean success = dbManager.actionFriendRequest(currentUserId, senderId, status);
        
        if (success) {
            // Refresh this user's contact list
            handleGetContactList(conn, currentUserId);
            
            // Also refresh the *other* user's contact list (if they are online)
            WebSocket senderConn = onlineUsers.get(senderId);
            if (senderConn != null) {
                handleGetContactList(senderConn, senderId);
            }
        } else {
            sendJsonError(conn, "Failed to action friend request.");
        }
    }
    
    
    // --- Broadcasting and List Methods (UPDATED) ---

    /**
     * Sends the user's main "Contact List" (Friends + Pending)
     */
    private void handleGetContactList(WebSocket conn, int userId) {
        List<String> friends = dbManager.getFriendList(userId);
        List<String> pending = dbManager.getPendingRequests(userId);
        
        JSONObject contactListJson = new JSONObject();
        JSONArray friendsArray = new JSONArray();
        JSONArray pendingArray = new JSONArray();
        
        // Build friends list
        for (String username : friends) {
            JSONObject userObj = new JSONObject();
            userObj.put("username", username);
            int uid = dbManager.getUserId(username);
            userObj.put("online", onlineUsers.containsKey(uid));
            friendsArray.put(userObj);
        }
        
        // Build pending list
        for (String username : pending) {
            pendingArray.put(username);
        }
        
        contactListJson.put("type", "contact_list");
        contactListJson.put("friends", friendsArray);
        contactListJson.put("pending", pendingArray);
        
        conn.send(contactListJson.toString());
    }
    
    /**
     * Sends the updated friend/online list to ALL online users.
     * This is called when anyone logs in or out.
     */
    private void broadcastUserListUpdate() {
        // This is a bit inefficient, but simple.
        // A better way would be to send a small "user_online" or "user_offline" message.
        // For now, we just refresh everyone's full contact list.
        for (WebSocket conn : onlineUsers.values()) {
            if(conn.getAttachment() != null) {
                int userId = conn.getAttachment();
                handleGetContactList(conn, userId);
            }
        }
    }
    
    private void broadcastTyping(int senderId, int receiverId, boolean isTyping) {
        String senderUsername = dbManager.getAllUsernames().stream()
                                  .filter(u -> dbManager.getUserId(u) == senderId)
                                  .findFirst().orElse(null);
                                  
        if (senderUsername == null) return; 

        JSONObject typingJson = new JSONObject();
        typingJson.put("type", isTyping ? "user_typing" : "user_stopped_typing");
        typingJson.put("username", senderUsername);
        
        String jsonString = typingJson.toString();

        if (receiverId == -1) {
            for (WebSocket conn : onlineUsers.values()) {
                conn.send(jsonString);
            }
        } else {
            WebSocket receiverConn = onlineUsers.get(receiverId);
            if (receiverConn != null) {
                sendJsonMessage(receiverConn, jsonString);
            }
        }
    }
    
    
    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.err.println("An error occurred for connection " + (conn != null ? conn.getRemoteSocketAddress() : "UNKNOWN") + ": " + ex.getMessage());
        ex.printStackTrace();
    }

    @Override
    public void onStart() {
        System.out.println("✅ WebSocket Server started successfully.");
        System.out.println("Listening on port " + getPort());
        setConnectionLostTimeout(0);
    }
    
    
    // --- JSON Helper Methods ---
    
    private void sendJsonMessage(WebSocket conn, String jsonString) {
        conn.send(jsonString);
    }
    
    private void sendJsonError(WebSocket conn, String errorMessage) {
        JSONObject errorJson = new JSONObject();
        errorJson.put("type", "error");
        errorJson.put("message", errorMessage);
        conn.send(errorJson.toString());
    }
    
    private String createJsonMessage(String type, String message) {
        JSONObject json = new JSONObject();
        json.put("type", type);
        json.put("message", message);
        return json.toString();
    }
    
    private String createJsonMessage(String type, JSONObject data) {
        JSONObject json = new JSONObject();
        json.put("type", type);
        json.put("data", data);
        return json.toString();
    }
    
    private JSONObject createJsonMessageFromObject(Message msg) {
        JSONObject msgJson = new JSONObject();
        msgJson.put("sender", msg.sender);
        msgJson.put("message", msg.text);
        
        String time = new SimpleDateFormat("h:mm a").format(msg.timestamp);
        msgJson.put("timestamp", time);
        
        return msgJson;
    }


    /**
     * The main method to run the server.
     */
    public static void main(String[] args) {
        dbManager = new DatabaseManager();
        dbManager.initializeDatabase(); 
        
        int port = 8080; 
        WebSocketChatServer server = new WebSocketChatServer(port);
        server.start();
    }
}