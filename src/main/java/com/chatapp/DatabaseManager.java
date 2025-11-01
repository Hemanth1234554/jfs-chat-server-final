package com.chatapp;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties; // New Import

import org.mindrot.jbcrypt.BCrypt;

public class DatabaseManager {

    // --- Database Connection Details (FIXED) ---
    // We parse the details from your Render string to avoid errors
    
    private static final String DB_HOST = "dpg-d42enk8dl3ps7398pp8g-a.singapore-postgres.render.com";
    private static final String DB_NAME = "jfs_chat_database";
    private static final String DB_USER = "jfs_chat_database_user";
    private static final String DB_PASSWORD = "jYM9lrOqHX72FtzXLVLKGbr1tb33N1vI"; // From your log
    
    // Build the JDBC URL *without* user/pass
    private static final String DB_URL = "jdbc:postgresql://" + DB_HOST + "/" + DB_NAME;


    /**
     * Attempts to establish a connection to the PostgreSQL database.
     */
    private Connection connect() throws SQLException {
        try {
            // This line "registers" the PostgreSQL driver we added
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("PostgreSQL JDBC Driver not found!");
            e.printStackTrace();
            return null;
        }
        
        // This is the new, safer way to connect
        // We pass user, pass, and sslmode as properties
        Properties props = new Properties();
        props.setProperty("user", DB_USER);
        props.setProperty("password", DB_PASSWORD);
        props.setProperty("sslmode", "require");

        return DriverManager.getConnection(DB_URL, props);
    }
    
    /**
     * Initializes the database.
     * Call this once when the server starts to ensure tables exist.
     */
    public void initializeDatabase() {
        // SQL for 'users' table (PostgreSQL syntax)
        String createUserTableSQL = "CREATE TABLE IF NOT EXISTS users ("
                + "user_id SERIAL PRIMARY KEY," // SERIAL is PostgreSQL's AUTO_INCREMENT
                + "username VARCHAR(50) NOT NULL UNIQUE,"
                + "password_hash VARCHAR(255) NOT NULL,"
                + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                + ");";
        
        // SQL for 'private_messages' table (PostgreSQL syntax)
        String createMessagesTableSQL = "CREATE TABLE IF NOT EXISTS private_messages ("
                + "message_id SERIAL PRIMARY KEY,"
                + "sender_id INT NOT NULL REFERENCES users(user_id)," // Simpler FOREIGN KEY
                + "receiver_id INT NOT NULL REFERENCES users(user_id),"
                + "message_text TEXT NOT NULL,"
                + "sent_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                + ");";
                
        // SQL for 'friendships' table (PostgreSQL syntax)
        String createFriendshipsTableSQL = "CREATE TABLE IF NOT EXISTS friendships ("
                + "friendship_id SERIAL PRIMARY KEY,"
                + "user_one_id INT NOT NULL REFERENCES users(user_id),"
                + "user_two_id INT NOT NULL REFERENCES users(user_id),"
                + "status INT NOT NULL DEFAULT 0," // 0=Pending, 1=Accepted, 2=Rejected
                + "action_user_id INT NOT NULL REFERENCES users(user_id),"
                + "CONSTRAINT unique_friendship UNIQUE (user_one_id, user_two_id)" // Unique constraint
                + ");";
        
        try (Connection conn = connect();
             Statement stmt = conn.createStatement()) {
            
            stmt.execute(createUserTableSQL);
            stmt.execute(createMessagesTableSQL);
            stmt.execute(createFriendshipsTableSQL); 
            System.out.println("Database tables are ready.");
            
        } catch (SQLException e) {
            System.err.println("Error initializing database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Registers a new user in the database.
     */
    public boolean registerUser(String username, String password) {
        String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt(12));
        String sql = "INSERT INTO users(username, password_hash) VALUES(?, ?)";

        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, username);
            pstmt.setString(2, hashedPassword);
            pstmt.executeUpdate();
            
            System.out.println("New user registered: " + username);
            return true;
            
        } catch (SQLException e) {
            // PostgreSQL unique violation code
            if (e.getSQLState().equals("23505")) { 
                System.out.println("Registration failed: Username already exists.");
            } else {
                System.err.println("Error registering user: " + e.getMessage());
            }
            return false;
        }
    }

    /**
     * Logs in a user.
     * @return The user's user_id if successful, or -1 if login fails.
     */
    public int loginUser(String username, String password) {
        String sql = "SELECT user_id, password_hash FROM users WHERE username = ?";

        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, username);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String storedHash = rs.getString("password_hash");
                    
                    if (BCrypt.checkpw(password, storedHash)) {
                        int userId = rs.getInt("user_id");
                        System.out.println("User login successful: " + username + " (ID: " + userId + ")");
                        return userId;
                    } else {
                        System.out.println("User login failed: Invalid password.");
                        return -1; 
                    }
                } else {
                    System.out.println("User login failed: User not found.");
                    return -1;
                }
            }
        } catch (SQLException e) {
            System.err.println("Error logging in user: " + e.getMessage());
            return -1;
        }
    }
    
    /**
     * Gets all registered usernames from the database.
     */
    public List<String> getAllUsernames() {
        List<String> usernames = new ArrayList<>();
        String sql = "SELECT username FROM users ORDER BY username ASC";
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            
            while (rs.next()) {
                usernames.add(rs.getString("username"));
            }
            
        } catch (SQLException e) {
            System.err.println("Error getting all usernames: " + e.getMessage());
        }
        return usernames;
    }
    
    /**
     * Finds a user's ID from their username.
     */
    public int getUserId(String username) {
        String sql = "SELECT user_id FROM users WHERE username = ?";
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, username);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("user_id");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting user ID: " + e.getMessage());
        }
        return -1; // User not found
    }
    
    /**
     * Saves a new private message to the database.
     * @return The new Message object with its timestamp, or null if failed.
     */
    public Message savePrivateMessage(int senderId, int receiverId, String message) {
        String sql = "INSERT INTO private_messages (sender_id, receiver_id, message_text) VALUES (?, ?, ?)";
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            pstmt.setInt(1, senderId);
            pstmt.setInt(2, receiverId);
            pstmt.setString(3, message);
            int affectedRows = pstmt.executeUpdate();

            if (affectedRows > 0) {
                try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        int messageId = generatedKeys.getInt(1); 
                        return getMessageById(messageId);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error saving private message: " + e.getMessage());
        }
        return null; // Failed to save
    }
    
    
    /**
     * Helper method to get a single message's details by its ID.
     */
    private Message getMessageById(int messageId) throws SQLException {
        String sql = "SELECT m.message_text, u.username AS sender_username, m.sent_at "
                   + "FROM private_messages m "
                   + "JOIN users u ON m.sender_id = u.user_id "
                   + "WHERE m.message_id = ?";
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, messageId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String sender = rs.getString("sender_username");
                    String text = rs.getString("message_text");
                    Timestamp timestamp = rs.getTimestamp("sent_at");
                    return new Message(sender, text, timestamp);
                }
            }
        }
        return null;
    }
    
    
    /**
     * Retrieves the chat history between two users.
     */
    public List<Message> getMessageHistory(int userId1, int userId2) {
        List<Message> history = new ArrayList<>();
        String sql = "SELECT m.message_text, u.username AS sender_username, m.sent_at "
                   + "FROM private_messages m "
                   + "JOIN users u ON m.sender_id = u.user_id "
                   + "WHERE (m.sender_id = ? AND m.receiver_id = ?) " 
                   + "OR (m.sender_id = ? AND m.receiver_id = ?) "
                   + "ORDER BY m.sent_at ASC";

        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, userId1);
            pstmt.setInt(2, userId2);
            pstmt.setInt(3, userId2);
            pstmt.setInt(4, userId1);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String sender = rs.getString("sender_username");
                    String text = rs.getString("message_text");
                    Timestamp timestamp = rs.getTimestamp("sent_at");
                    history.add(new Message(sender, text, timestamp));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error loading message history: " + e.getMessage());
        }
        return history;
    }
    
    
    // --- FRIEND SYSTEM METHODS (PostgreSQL) ---

    /**
     * Sends a friend request from one user to another.
     */
    public String sendFriendRequest(int senderId, String receiverUsername) {
        int receiverId = getUserId(receiverUsername);
        
        if (receiverId == -1) return "User not found.";
        if (senderId == receiverId) return "You cannot add yourself as a friend.";
        
        int userOneId = Math.min(senderId, receiverId);
        int userTwoId = Math.max(senderId, receiverId);
        
        String checkSql = "SELECT * FROM friendships WHERE user_one_id = ? AND user_two_id = ?";
        String insertSql = "INSERT INTO friendships (user_one_id, user_two_id, status, action_user_id) "
                         + "VALUES (?, ?, 0, ?) ON CONFLICT (user_one_id, user_two_id) DO NOTHING"; 

        try (Connection conn = connect()) {
            // Check if a friendship (or request) already exists
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setInt(1, userOneId);
                checkStmt.setInt(2, userTwoId);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next()) {
                        int status = rs.getInt("status");
                        if (status == 1) return "You are already friends with this user.";
                        if (status == 0) return "A friend request is already pending.";
                        if (status == 2) return "This user has rejected your request.";
                    }
                }
            }
            
            // If no record exists, create a new pending request
            try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                insertStmt.setInt(1, userOneId);
                insertStmt.setInt(2, userTwoId);
                insertStmt.setInt(3, senderId); // The sender is the action user
                int affectedRows = insertStmt.executeUpdate();
                
                if (affectedRows > 0) {
                    return "Friend request sent.";
                } else {
                    return "A friend request is already pending.";
                }
            }
            
        } catch (SQLException e) {
            System.err.println("Error sending friend request: " + e.getMessage());
            return "An error occurred.";
        }
    }
    
    /**
     * Accepts or rejects a friend request.
     */
    public boolean actionFriendRequest(int currentUserId, int senderId, int status) {
        int userOneId = Math.min(currentUserId, senderId);
        int userTwoId = Math.max(currentUserId, senderId);
        
        String sql = "UPDATE friendships SET status = ?, action_user_id = ? "
                   + "WHERE user_one_id = ? AND user_two_id = ? AND status = 0"; 
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, status);
            pstmt.setInt(2, currentUserId); 
            pstmt.setInt(3, userOneId);
            pstmt.setInt(4, userTwoId);
            
            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0; 
            
        } catch (SQLException e) {
            System.err.println("Error acting on friend request: " + e.getMessage());
            return false;
        }
    }

    /**
     * Gets a list of a user's friends (status = 1).
     */
    public List<String> getFriendList(int userId) {
        List<String> friends = new ArrayList<>();
        String sql = "SELECT u.username FROM users u "
                   + "JOIN friendships f ON (u.user_id = f.user_one_id OR u.user_id = f.user_two_id) "
                   + "WHERE (f.user_one_id = ? OR f.user_two_id = ?) "
                   + "AND f.status = 1 AND u.user_id != ?"; 
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, userId);
            pstmt.setInt(2, userId);
            pstmt.setInt(3, userId); 
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    friends.add(rs.getString("username"));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting friend list: " + e.getMessage());
        }
        return friends;
    }
    
    /**
     * Gets a list of pending friend requests for a user (status = 0).
     */
    public List<String> getPendingRequests(int userId) {
        List<String> requests = new ArrayList<>();
        String sql = "SELECT u.username FROM users u "
                   + "JOIN friendships f ON u.user_id = f.action_user_id "
                   + "WHERE (f.user_one_id = ? OR f.user_two_id = ?) "
                   + "AND f.status = 0 AND f.action_user_id != ?"; 
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, userId);
            pstmt.setInt(2, userId);
            pstmt.setInt(3, userId); 
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    requests.add(rs.getString("username"));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting pending requests: " + e.getMessage());
        }
        return requests;
    }

    
    /**
     * A simple helper class to store a message.
     */
    public static class Message {
        public final String sender;
        public final String text;
        public final Timestamp timestamp; 
        
        public Message(String sender, String text, Timestamp timestamp) {
            this.sender = sender;
            this.text = text;
            this.timestamp = timestamp;
        }
    }
    
} // This is the FINAL closing brace for the DatabaseManager class

