package com.collab.server;

import com.collab.core.Document;
import com.collab.core.EditOperation;
import com.collab.session.SessionManager;
import com.collab.sync.SyncEngine;
import com.collab.persistence.VersionController;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Server {
    private static ServerSocket serverSocket;
    private static SessionManager sessionManager;
    private static Document document;
    private static SyncEngine syncEngine;
    private static VersionController versionController;
    private static boolean running = true;
    private static final ExecutorService clientHandlers = Executors.newCachedThreadPool();

    public static void start(int port) throws Exception {
        document = new Document("collaborative_doc.txt");
        syncEngine = new SyncEngine(document);
        sessionManager = new SessionManager(20);
        
        try {
            versionController = new VersionController("collab_data.db", "collaborative_doc.txt");
        } catch (Exception e) {
            System.out.println("Warning: Could not initialize database, continuing without persistence");
        }

        serverSocket = new ServerSocket(port);
        System.out.println("=== Server started on port " + port + " ===");
        System.out.println("Document: " + document.getName());
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                stop();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }));

        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New connection from: " + clientSocket.getInetAddress());
                clientHandlers.submit(new ClientHandler(clientSocket));
            } catch (IOException e) {
                if (running) {
                    System.err.println("Error accepting connection: " + e.getMessage());
                }
            }
        }
    }

    public static void stop() throws Exception {
        running = false;
        if (serverSocket != null) serverSocket.close();
        if (versionController != null) versionController.close();
        clientHandlers.shutdown();
        System.out.println("Server stopped.");
    }

    static class ClientHandler implements Runnable {
        private Socket socket;
        private String userId;
        private PrintWriter out;
        private BufferedReader in;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                String line;
                while ((line = in.readLine()) != null) {
                    handleMessage(line);
                }
            } catch (IOException e) {
                System.out.println("Client disconnected: " + userId);
            } finally {
                cleanup();
            }
        }

        private void handleMessage(String message) {
            if (message.startsWith("JOIN:")) {
                handleJoin(message);
            } else if (message.startsWith("EDIT:")) {
                handleEdit(message);
            } else if (message.equals("SYNC")) {
                handleSync();
            } else if (message.startsWith("ACTIVITY:")) {
                handleActivity(message);
            } else if (message.equals("USERS")) {
                handleUserList();
            } else if (message.startsWith("SAVE:")) {
                handleSave(message);
            } else if (message.startsWith("LOAD:")) {
                handleLoad(message);
            }
        }

        private void handleJoin(String message) {
            String[] parts = message.split(":", 3);
            if (parts.length >= 3) {
                userId = parts[1];
                String username = parts[2];
                if (sessionManager.addSession(userId, username, out)) {
                    document.addUser(userId);
                    sessionManager.updateActivity(userId, "Joined");
                    out.println("JOIN_OK:" + document.getContent() + ":" + document.getVersion());
                    System.out.println("User joined: " + username + " (" + userId + ")");
                } else {
                    out.println("JOIN_FAIL:Session limit reached");
                }
            }
        }

        private void handleEdit(String message) {
            String[] parts = message.split(":", 5);
            if (parts.length >= 5) {
                String userId = parts[1];
                int version = Integer.parseInt(parts[2]);
                String type = parts[3];
                int position = Integer.parseInt(parts[4]);
                String text = parts.length > 5 ? parts[5] : "";

                EditOperation.OpType opType = EditOperation.OpType.valueOf(type);
                EditOperation operation = new EditOperation(userId, opType, position, text, version);
                
                String result = syncEngine.processOperation(operation);
                sessionManager.updateActivity(userId, "Editing");
                
                try {
                    if (versionController != null) {
                        versionController.saveVersion(document, userId);
                    }
                } catch (Exception e) {
                    System.err.println("Version save error: " + e.getMessage());
                }

                broadcast("EDIT_OK:" + result + ":" + document.getVersion() + ":" + userId);
            }
        }

        private void handleSync() {
            int userVersion = userId != null ? syncEngine.getUserVersion(userId) : 0;
            out.println("SYNC_OK:" + document.getContent() + ":" + document.getVersion() + ":" + userVersion);
        }

        private void handleActivity(String message) {
            String[] parts = message.split(":", 2);
            if (parts.length >= 2 && userId != null) {
                sessionManager.updateActivity(userId, parts[1]);
            }
        }

        private void handleUserList() {
            List<String> users = new ArrayList<>(sessionManager.getUserIds());
            out.println("USERS_OK:" + String.join(",", users));
        }

        private void handleSave(String message) {
            String fileName = message.substring(5);
            try {
                document.saveToFile(new File(fileName));
                broadcast("SAVE_OK:" + fileName);
            } catch (IOException e) {
                out.println("SAVE_FAIL:" + e.getMessage());
            }
        }

        private void handleLoad(String message) {
            String fileName = message.substring(5);
            try {
                document.loadFromFile(new File(fileName));
                syncEngine = new SyncEngine(document);
                broadcast("LOAD_OK:" + document.getContent());
            } catch (IOException e) {
                out.println("LOAD_FAIL:" + e.getMessage());
            }
        }

        private void broadcast(String message) {
            for (String uid : sessionManager.getUserIds()) {
                PrintWriter pw = sessionManager.getClientOutput(uid);
                if (pw != null) {
                    pw.println(message);
                }
            }
        }

        private void cleanup() {
            if (userId != null) {
                document.removeUser(userId);
                sessionManager.removeSession(userId);
            }
            try {
                socket.close();
            } catch (IOException e) {}
        }
    }
}