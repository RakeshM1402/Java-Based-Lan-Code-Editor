package com.collab.client;

import com.collab.core.EditOperation;
import com.collab.ui.EditorFrame;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Client {
    private static Socket socket;
    private static PrintWriter out;
    private static BufferedReader in;
    private static String userId;
    private static String username;
    private static String serverIP;
    private static int serverPort;
    private static EditorFrame editorFrame;
    private static ExecutorService listenerExecutor = Executors.newSingleThreadExecutor();
    private static volatile boolean connected = false;
    private static ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> heartbeatTask;

    public static void start(String serverIP, int port, String username) throws Exception {
        Client.username = username;
        Client.serverIP = serverIP;
        Client.serverPort = port;
        userId = UUID.randomUUID().toString();

        connect();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                stop();
            } catch (Exception e) {}
        }));
    }

    private static void connect() throws Exception {
        socket = new Socket(serverIP, serverPort);
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        System.out.println("Connected to server at " + serverIP + ":" + serverPort);
        
        if (editorFrame == null) {
            java.awt.EventQueue.invokeLater(() -> {
                editorFrame = new EditorFrame(Client.class, "Collaborative Editor - " + username);
            });
            Thread.sleep(500);
        }

        joinServer();

        connected = true;
        startListener();
        startHeartbeat();
    }

    private static void startHeartbeat() {
        heartbeatTask = heartbeatExecutor.scheduleAtFixedRate(() -> {
            if (connected && out != null) {
                out.println("PING");
            }
        }, 10, 10, TimeUnit.SECONDS);
    }

    private static void joinServer() {
        out.println("JOIN:" + userId + ":" + username);
    }

    private static void startListener() {
        listenerExecutor.submit(() -> {
            try {
                String message;
                while (connected && (message = in.readLine()) != null) {
                    handleMessage(message);
                }
            } catch (IOException e) {
                System.err.println("Disconnected from server");
                connected = false;
                attemptReconnect();
            }
        });
    }

    private static void attemptReconnect() {
        if (heartbeatTask != null) heartbeatTask.cancel(false);
        System.out.println("Attempting to reconnect in 5 seconds...");
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                for (int i = 0; i < 3; i++) {
                    try {
                        connect();
                        System.out.println("Reconnected successfully!");
                        return;
                    } catch (Exception e) {
                        System.out.println("Reconnect attempt " + (i + 1) + " failed...");
                        Thread.sleep(3000);
                    }
                }
                System.err.println("Failed to reconnect after 3 attempts");
            } catch (InterruptedException e) {}
        }).start();
    }

    private static void handleMessage(String message) {
        if (message.startsWith("PONG")) {
            // Heartbeat response received
        } else if (message.startsWith("JOIN_OK:")) {
            String[] parts = message.substring(8).split(":", 2);
            if (parts.length >= 2) {
                String content = parts[0];
                int version = Integer.parseInt(parts[1]);
                editorFrame.updateContent(content);
                editorFrame.updateVersion(version);
                System.out.println("Joined successfully. Current version: " + version);
            }
        } else if (message.startsWith("EDIT_OK:")) {
            String[] parts = message.substring(8).split(":", 4);
            if (parts.length >= 4) {
                String content = parts[0];
                int version = Integer.parseInt(parts[1]);
                String editingUser = parts[2];
                if (!editingUser.equals(userId)) {
                    editorFrame.updateContent(content);
                    editorFrame.updateVersion(version);
                }
            }
        } else if (message.startsWith("SYNC_OK:")) {
            String[] parts = message.substring(8).split(":", 3);
            if (parts.length >= 3) {
                editorFrame.updateContent(parts[0]);
                editorFrame.updateVersion(Integer.parseInt(parts[1]));
            }
        } else if (message.startsWith("USERS:")) {
            String userList = message.substring(6);
            System.out.println("Active users: " + userList);
            editorFrame.updateUserList(userList);
        } else if (message.startsWith("ACTIVITY:")) {
            String[] parts = message.split(":", 3);
            if (parts.length >= 3) {
                editorFrame.updateActivity(parts[1], parts[2]);
            }
        } else if (message.startsWith("SAVE_OK:")) {
            editorFrame.showMessage("File saved: " + message.substring(8));
        } else if (message.startsWith("LOAD_OK:")) {
            editorFrame.updateContent(message.substring(9));
            editorFrame.showMessage("File loaded successfully");
        } else if (message.startsWith("ERROR:")) {
            editorFrame.showMessage("Error: " + message.substring(6));
        }
    }

    public static void sendEdit(EditOperation.OpType type, int position, String text) {
        if (!connected) return;
        int version = editorFrame.getVersion();
        out.println("EDIT:" + userId + ":" + version + ":" + type + ":" + position + ":" + text);
    }

    public static void sendActivity(String activity) {
        if (!connected) return;
        out.println("ACTIVITY:" + activity);
    }

    public static void requestSync() {
        if (!connected) return;
        out.println("SYNC");
    }

    public static void requestUserList() {
        if (!connected) return;
        out.println("USERS");
    }

    public static void saveFile(String fileName) {
        if (!connected) return;
        out.println("SAVE:" + fileName);
    }

    public static void loadFile(String fileName) {
        if (!connected) return;
        out.println("LOAD:" + fileName);
    }

    public static void stop() throws Exception {
        connected = false;
        if (heartbeatTask != null) heartbeatTask.cancel(false);
        if (out != null) out.println("LEAVE:" + userId);
        if (socket != null) socket.close();
        listenerExecutor.shutdown();
        heartbeatExecutor.shutdown();
        System.out.println("Client disconnected.");
    }

    public static boolean isConnected() {
        return connected;
    }

    public static String getUserId() {
        return userId;
    }

    public static String getUsername() {
        return username;
    }
}
