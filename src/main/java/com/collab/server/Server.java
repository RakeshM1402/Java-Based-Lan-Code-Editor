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
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Base64;

import com.sun.net.httpserver.*;

public class Server {
    private static ServerSocket serverSocket;
    private static HttpServer httpServer;
    private static SessionManager sessionManager;
    private static Document document;
    private static SyncEngine syncEngine;
    private static VersionController versionController;
    private static boolean running = true;
    private static final ExecutorService clientHandlers = Executors.newCachedThreadPool();
    private static int tcpPort;
    private static int httpPort;

    private static Map<String, HttpExchange> wsClients = new ConcurrentHashMap<>();
    private static Map<String, PrintWriter> wsOutputs = new ConcurrentHashMap<>();

    private static void displayLANAddresses() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isUp() && !ni.isLoopback()) {
                    Enumeration<InetAddress> addresses = ni.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        InetAddress addr = addresses.nextElement();
                        if (addr instanceof Inet4Address) {
                            System.out.println("  " + addr.getHostAddress() + " (" + ni.getDisplayName() + ")");
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("  Could not detect LAN addresses");
        }
    }

    private static String createWebSocketResponse(String key) throws Exception {
        String guid = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
        String combined = key + guid;
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] hash = md.digest(combined.getBytes("ISO-8859-1"));
        return Base64.getEncoder().encodeToString(hash);
    }

    private static void startHttpServer(int port) throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(port), 0);
        
        httpServer.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/") || path.isEmpty()) {
                path = "/index.html";
            }
            
            String resourcePath = "/web" + path;
            InputStream is = Server.class.getResourceAsStream(resourcePath);
            
            if (is != null) {
                byte[] content = is.readAllBytes();
                String contentType = getContentType(path);
                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.sendResponseHeaders(200, content.length);
                exchange.getResponseBody().write(content);
            } else {
                String response = "404 Not Found";
                exchange.sendResponseHeaders(404, response.length());
                exchange.getResponseBody().write(response.getBytes());
            }
            is.close();
        });

        httpServer.createContext("/ws", exchange -> {
            String connHeader = exchange.getRequestHeaders().getFirst("Connection");
            String upgradeHeader = exchange.getRequestHeaders().getFirst("Upgrade");
            String keyHeader = exchange.getRequestHeaders().getFirst("Sec-WebSocket-Key");
            String wsKey = exchange.getRequestHeaders().getFirst("Sec-WebSocket-Key");
            
            if (connHeader != null && connHeader.toLowerCase().contains("upgrade") && 
                "websocket".equalsIgnoreCase(upgradeHeader) && wsKey != null) {
                try {
                    String responseKey = createWebSocketResponse(wsKey);
                    exchange.getResponseHeaders().set("Upgrade", "websocket");
                    exchange.getResponseHeaders().set("Connection", "Upgrade");
                    exchange.getResponseHeaders().set("Sec-WebSocket-Accept", responseKey);
                    exchange.sendResponseHeaders(101, -1);
                    
                    String userId = "ws_" + System.currentTimeMillis();
                    wsClients.put(userId, exchange);
                    wsOutputs.put(userId, new PrintWriter(new OutputStreamWriter(exchange.getResponseBody())));
                    
                    readWebSocketMessages(exchange, userId);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                exchange.sendResponseHeaders(400, -1);
            }
        });

        httpServer.createContext("/api/server/start", exchange -> sendJson(exchange, "{\"status\":\"running\",\"port\":" + tcpPort + "}"));
        httpServer.createContext("/api/server/stop", exchange -> sendJson(exchange, "{\"status\":\"stopped\"}"));
        httpServer.createContext("/api/server/status", exchange -> {
            StringBuilder ips = new StringBuilder();
            try {
                Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                while (interfaces.hasMoreElements()) {
                    NetworkInterface ni = interfaces.nextElement();
                    if (ni.isUp() && !ni.isLoopback()) {
                        Enumeration<InetAddress> addresses = ni.getInetAddresses();
                        while (addresses.hasMoreElements()) {
                            InetAddress addr = addresses.nextElement();
                            if (addr instanceof Inet4Address) {
                                if (ips.length() > 0) ips.append(",");
                                ips.append("\"").append(addr.getHostAddress()).append("\"");
                            }
                        }
                    }
                }
            } catch (Exception e) {}
            sendJson(exchange, "{\"status\":\"running\",\"port\":" + tcpPort + ",\"httpPort\":" + httpPort + ",\"ips\":[" + ips + "]}");
        });
        httpServer.createContext("/api/users", exchange -> {
            List<String> users = new ArrayList<>(sessionManager.getUserIds());
            users.addAll(wsClients.keySet());
            sendJson(exchange, "{\"status\":\"ok\",\"users\":[\"" + String.join("\",\"", users) + "\"]}");
        });

        httpServer.setExecutor(null);
        httpServer.start();
        System.out.println("HTTP Server (web UI) started on port " + port);
    }

    private static void readWebSocketMessages(HttpExchange exchange, String userId) {
        try {
            InputStream is = exchange.getRequestBody();
            byte[] buffer = new byte[1024];
            StringBuilder message = new StringBuilder();
            
            while (running) {
                int bytesRead = is.read(buffer);
                if (bytesRead == -1) break;
                
                int opcode = buffer[0] & 0xFF;
                if (opcode == 0x8) break;
                
                if (opcode == 0x81) {
                    int length = buffer[1] & 0x7F;
                    int maskOffset = 2;
                    boolean masked = (buffer[1] & 0x80) != 0;
                    
                    if (length == 126) {
                        length = ((buffer[2] & 0xFF) << 8) | (buffer[3] & 0xFF);
                        maskOffset = 4;
                    } else if (length == 127) {
                        length = (int)((((long)buffer[2] & 0xFF) << 56) | (((long)buffer[3] & 0xFF) << 48) |
                                 (((long)buffer[4] & 0xFF) << 40) | (((long)buffer[5] & 0xFF) << 32) |
                                 (((long)buffer[6] & 0xFF) << 24) | (((long)buffer[7] & 0xFF) << 16) |
                                 (((long)buffer[8] & 0xFF) << 8) | ((long)buffer[9] & 0xFF));
                        maskOffset = 10;
                    }
                    
                    if (masked) {
                        byte[] mask = new byte[4];
                        System.arraycopy(buffer, maskOffset, mask, 0, 4);
                        int dataStart = maskOffset + 4;
                        
                        for (int i = 0; i < length && (dataStart + i) < bytesRead; i++) {
                            buffer[dataStart + i] ^= mask[i % 4];
                        }
                    }
                    
                    int dataStart = maskOffset + (masked ? 4 : 0);
                    message.append(new String(buffer, dataStart, Math.min(length, bytesRead - dataStart), "UTF-8"));
                    
                    String msgStr = message.toString();
                    message.setLength(0);
                    handleWsMessage(userId, msgStr);
                }
            }
        } catch (Exception e) {
        } finally {
            wsClients.remove(userId);
            wsOutputs.remove(userId);
            try { exchange.close(); } catch (Exception e) {}
        }
    }

    private static void handleWsMessage(String userId, String message) {
        if (message.startsWith("JOIN:")) {
            String[] parts = message.split(":", 3);
            if (parts.length >= 3) {
                PrintWriter out = wsOutputs.get(userId);
                if (out != null) {
                    sessionManager.addSession(userId, parts[2], out);
                    document.addUser(userId);
                    out.println("JOIN_OK:" + document.getContent() + ":" + document.getVersion());
                    out.flush();
                    broadcastWs("JOIN_OK:" + document.getContent() + ":" + document.getVersion() + ":" + userId);
                }
            }
        } else if (message.startsWith("EDIT:")) {
            String[] parts = message.split(":", 5);
            if (parts.length >= 5) {
                String uid = parts[1];
                int version = Integer.parseInt(parts[2]);
                String type = parts[3];
                int position = Integer.parseInt(parts[4]);
                String text = parts.length > 5 ? parts[5] : "";
                
                try {
                    EditOperation.OpType opType = EditOperation.OpType.valueOf(type);
                    EditOperation operation = new EditOperation(uid, opType, position, text, version);
                    String result = syncEngine.processOperation(operation);
                    broadcastWs("EDIT_OK:" + result + ":" + document.getVersion() + ":" + uid);
                } catch (Exception e) {}
            }
        } else if (message.equals("PING")) {
            PrintWriter out = wsOutputs.get(userId);
            if (out != null) {
                out.print("\u0000PONG\u0000");
                out.flush();
            }
        }
    }

    private static void broadcastWs(String message) {
        for (Map.Entry<String, PrintWriter> entry : wsOutputs.entrySet()) {
            try {
                PrintWriter out = entry.getValue();
                ByteBuffer buffer = encodeWebSocketFrame(message);
                out.write(new String(buffer.array(), "ISO-8859-1"));
                out.flush();
            } catch (Exception e) {}
        }
    }

    private static ByteBuffer encodeWebSocketFrame(String message) {
        byte[] data = message.getBytes();
        ByteBuffer buffer;
        
        if (data.length < 126) {
            buffer = ByteBuffer.allocate(2 + data.length);
            buffer.put((byte) 0x81);
            buffer.put((byte) data.length);
        } else if (data.length < 65536) {
            buffer = ByteBuffer.allocate(4 + data.length);
            buffer.put((byte) 0x81);
            buffer.put((byte) 126);
            buffer.put((byte) ((data.length >> 8) & 0xFF));
            buffer.put((byte) (data.length & 0xFF));
        } else {
            buffer = ByteBuffer.allocate(10 + data.length);
            buffer.put((byte) 0x81);
            buffer.put((byte) 127);
            for (int i = 7; i >= 0; i--) {
                buffer.put((byte) ((data.length >> (8 * i)) & 0xFF));
            }
        }
        buffer.put(data);
        buffer.flip();
        return buffer;
    }

    private static String getContentType(String path) {
        if (path.endsWith(".html")) return "text/html";
        if (path.endsWith(".css")) return "text/css";
        if (path.endsWith(".js")) return "application/javascript";
        if (path.endsWith(".json")) return "application/json";
        return "text/plain";
    }

    private static void sendJson(HttpExchange exchange, String json) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        byte[] bytes = json.getBytes();
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    public static void start(int port) throws Exception {
        start(port, port + 100);
    }

    public static void start(int tcpPort, int httpPort) throws Exception {
        Server.tcpPort = tcpPort;
        Server.httpPort = httpPort;
        
        document = new Document("collaborative_doc.txt");
        syncEngine = new SyncEngine(document);
        sessionManager = new SessionManager(20);
        
        try {
            versionController = new VersionController("collab_data.db", "collaborative_doc.txt");
        } catch (Exception e) {
            System.out.println("Warning: Could not initialize database, continuing without persistence");
        }

        startHttpServer(httpPort);

        serverSocket = new ServerSocket(tcpPort);
        System.out.println("=== TCP Server started on port " + tcpPort + " ===");
        System.out.println("Document: " + document.getName());
        System.out.println("Available LAN addresses:");
        displayLANAddresses();
        
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
        if (httpServer != null) httpServer.stop(1);
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
            } else if (message.startsWith("LEAVE:")) {
                handleLeave(message);
            } else if (message.equals("PING")) {
                out.println("PONG");
            }
        }

        private void handleLeave(String message) {
            String[] parts = message.split(":", 2);
            if (parts.length >= 2) {
                String leaveUserId = parts[1];
                System.out.println("User left: " + leaveUserId);
                cleanupForUser(leaveUserId);
            }
        }

        private void cleanupForUser(String uid) {
            if (sessionManager.getUserIds().contains(uid)) {
                document.removeUser(uid);
                sessionManager.removeSession(uid);
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
