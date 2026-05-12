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
import java.security.MessageDigest;
import java.util.Base64;

import com.sun.net.httpserver.*;

public class Server {
    private static ServerSocket serverSocket;
    private static ServerSocket wsServerSocket;
    private static HttpServer httpServer;
    private static SessionManager sessionManager;
    private static Document document;
    private static SyncEngine syncEngine;
    private static VersionController versionController;
    private static boolean running = true;
    private static final ExecutorService clientHandlers = Executors.newCachedThreadPool();
    private static int tcpPort;
    private static int httpPort;
    private static int wsPort;

    private static final Set<WebSocketClient> wsClients = Collections.synchronizedSet(new HashSet<>());

    private static class WebSocketClient {
        Socket socket;
        String userId;
        String username;
        DataInputStream in;
        DataOutputStream out;
        
        WebSocketClient(Socket socket) throws IOException {
            this.socket = socket;
            this.in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            this.out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        }
        
        void send(String msg) {
            try {
                String framed = "\u0000" + msg + "\u0000";
                out.write(framed.getBytes("UTF-8"));
                out.flush();
            } catch (Exception e) {
                wsClients.remove(this);
            }
        }
        
        void close() {
            try { socket.close(); } catch (Exception e) {}
        }
    }

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

    private static String createWebSocketKey(String key) throws Exception {
        String guid = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] hash = md.digest((key + guid).getBytes("ISO-8859-1"));
        return Base64.getEncoder().encodeToString(hash);
    }

    private static void startHttpServer(int port) throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(port), 0);
        
        httpServer.createContext("/", exchange -> {
            try {
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
                    OutputStream os = exchange.getResponseBody();
                    os.write(content);
                    os.close();
                } else {
                    String response = "404 Not Found";
                    exchange.sendResponseHeaders(404, response.length());
                    exchange.getResponseBody().write(response.getBytes());
                }
            } catch (Exception e) {
                e.printStackTrace();
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
            sendJson(exchange, "{\"status\":\"running\",\"port\":" + tcpPort + ",\"httpPort\":" + httpPort + ",\"wsPort\":" + wsPort + ",\"ips\":[" + ips + "]}");
        });
        httpServer.createContext("/api/users", exchange -> {
            List<String> users = new ArrayList<>(sessionManager.getUserIds());
            for (WebSocketClient c : wsClients) {
                if (c.username != null) users.add(c.username);
            }
            sendJson(exchange, "{\"status\":\"ok\",\"users\":[\"" + String.join("\",\"", users) + "\"]}");
        });

        httpServer.setExecutor(null);
        httpServer.start();
        System.out.println("HTTP Server (web UI) started on port " + port);
    }

    private static void startWebSocketServer(int port) throws IOException {
        wsServerSocket = new ServerSocket(port);
        System.out.println("WebSocket Server started on port " + port);
        
        clientHandlers.submit(() -> {
            while (running) {
                try {
                    Socket clientSocket = wsServerSocket.accept();
                    clientHandlers.submit(() -> handleWebSocketConnection(clientSocket));
                } catch (IOException e) {
                    if (running) System.err.println("WS accept error: " + e.getMessage());
                }
            }
        });
    }

    private static void handleWebSocketConnection(Socket socket) {
        WebSocketClient client = null;
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            String line = reader.readLine();
            if (line == null) {
                socket.close();
                return;
            }
            
            if (!line.startsWith("GET /ws")) {
                socket.close();
                return;
            }
            
            Map<String, String> headers = new HashMap<>();
            String wsKey = null;
            
            while (!(line = reader.readLine()).isEmpty()) {
                int colon = line.indexOf(':');
                if (colon > 0) {
                    String key = line.substring(0, colon).trim();
                    String value = line.substring(colon + 1).trim();
                    headers.put(key, value);
                    if (key.equalsIgnoreCase("Sec-WebSocket-Key")) {
                        wsKey = value;
                    }
                }
            }
            
            if (wsKey == null) {
                socket.close();
                return;
            }
            
            String acceptKey = createWebSocketKey(wsKey);
            String response = "HTTP/1.1 101 Switching Protocols\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Accept: " + acceptKey + "\r\n" +
                "\r\n";
            
            socket.getOutputStream().write(response.getBytes("ISO-8859-1"));
            socket.getOutputStream().flush();
            
            client = new WebSocketClient(socket);
            wsClients.add(client);
            System.out.println("WebSocket client connected! Total: " + wsClients.size());
            
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] b = new byte[4096];
            boolean inFrame = false;
            
            while (running && !socket.isClosed()) {
                int available = socket.getInputStream().available();
                if (available > 0) {
                    int r = socket.getInputStream().read(b);
                    if (r == -1) break;
                    
                    for (int i = 0; i < r; i++) {
                        int byteVal = b[i] & 0xFF;
                        if (byteVal == 0x00) {
                            inFrame = true;
                        } else if (byteVal == 0xFF) {
                            if (inFrame) {
                                String msg = buffer.toString("UTF-8");
                                if (!msg.isEmpty()) {
                                    handleWsMessage(client, msg);
                                }
                                buffer.reset();
                                inFrame = false;
                            }
                        } else if (inFrame) {
                            buffer.write(byteVal);
                        }
                    }
                } else {
                    Thread.sleep(20);
                }
            }
        } catch (Exception e) {
        } finally {
            if (client != null) {
                wsClients.remove(client);
                System.out.println("WebSocket client removed. Total: " + wsClients.size());
            }
            try { socket.close(); } catch (Exception e) {}
        }
    }

    private static void handleWsMessage(WebSocketClient client, String message) {
        System.out.println("WS: " + message);
        
        if (message.startsWith("JOIN:")) {
            String[] parts = message.split(":", 3);
            if (parts.length >= 3) {
                client.userId = parts[1];
                client.username = parts[2];
                client.send("JOIN_OK:" + document.getContent() + ":" + document.getVersion());
                System.out.println("Web client joined: " + client.username);
                broadcastWs("EDIT_OK:" + document.getContent() + ":" + document.getVersion() + ":" + client.userId);
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
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } else if (message.equals("PING")) {
            client.send("PONG");
        }
    }

    private static void broadcastWs(String message) {
        System.out.println("Broadcast WS to " + wsClients.size() + ": " + message.substring(0, Math.min(50, message.length())));
        for (WebSocketClient c : new HashSet<>(wsClients)) {
            c.send(message);
        }
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
        start(port, port + 100, port + 200);
    }

    public static void start(int tcpPort, int httpPort, int wsPort) throws Exception {
        Server.tcpPort = tcpPort;
        Server.httpPort = httpPort;
        Server.wsPort = wsPort;
        
        document = new Document("collaborative_doc.txt");
        syncEngine = new SyncEngine(document);
        sessionManager = new SessionManager(20);
        
        try {
            versionController = new VersionController("collab_data.db", "collaborative_doc.txt");
        } catch (Exception e) {
            System.out.println("Warning: Could not initialize database");
        }

        startHttpServer(httpPort);
        startWebSocketServer(wsPort);

        serverSocket = new ServerSocket(tcpPort);
        System.out.println("=== TCP Server started on port " + tcpPort + " ===");
        System.out.println("Document: " + document.getName());
        System.out.println("Available LAN addresses:");
        displayLANAddresses();
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { stop(); } catch (Exception e) { e.printStackTrace(); }
        }));

        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New TCP connection from: " + clientSocket.getInetAddress());
                clientHandlers.submit(new ClientHandler(clientSocket));
            } catch (IOException e) {
                if (running) System.err.println("Error: " + e.getMessage());
            }
        }
    }

    public static void stop() throws Exception {
        running = false;
        if (httpServer != null) httpServer.stop(1);
        if (wsServerSocket != null) wsServerSocket.close();
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

        ClientHandler(Socket socket) { this.socket = socket; }

        @Override
        public void run() {
            try {
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String line;
                while ((line = in.readLine()) != null) handleMessage(line);
            } catch (IOException e) {
                System.out.println("Client disconnected: " + userId);
            } finally {
                cleanup();
            }
        }

        private void handleMessage(String message) {
            if (message.startsWith("JOIN:")) handleJoin(message);
            else if (message.startsWith("EDIT:")) handleEdit(message);
            else if (message.equals("SYNC")) handleSync();
            else if (message.startsWith("ACTIVITY:")) handleActivity(message);
            else if (message.equals("USERS")) handleUserList();
            else if (message.startsWith("SAVE:")) handleSave(message);
            else if (message.startsWith("LOAD:")) handleLoad(message);
            else if (message.startsWith("LEAVE:")) handleLeave(message);
            else if (message.equals("PING")) out.println("PONG");
        }

        private void handleLeave(String message) {
            String[] parts = message.split(":", 2);
            if (parts.length >= 2) {
                System.out.println("User left: " + parts[1]);
                cleanupForUser(parts[1]);
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
                    System.out.println("User joined: " + username);
                } else {
                    out.println("JOIN_FAIL:Session limit reached");
                }
            }
        }

        private void handleEdit(String message) {
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
                    if (versionController != null) versionController.saveVersion(document, uid);
                    broadcast("EDIT_OK:" + result + ":" + document.getVersion() + ":" + uid);
                } catch (Exception e) { e.printStackTrace(); }
            }
        }

        private void handleSync() {
            int userVersion = userId != null ? syncEngine.getUserVersion(userId) : 0;
            out.println("SYNC_OK:" + document.getContent() + ":" + document.getVersion() + ":" + userVersion);
        }

        private void handleActivity(String message) {
            String[] parts = message.split(":", 2);
            if (parts.length >= 2 && userId != null) sessionManager.updateActivity(userId, parts[1]);
        }

        private void handleUserList() {
            out.println("USERS_OK:" + String.join(",", sessionManager.getUserIds()));
        }

        private void handleSave(String message) {
            String fileName = message.substring(5);
            try {
                document.saveToFile(new File(fileName));
                broadcast("SAVE_OK:" + fileName);
            } catch (IOException e) { out.println("SAVE_FAIL:" + e.getMessage()); }
        }

        private void handleLoad(String message) {
            String fileName = message.substring(5);
            try {
                document.loadFromFile(new File(fileName));
                syncEngine = new SyncEngine(document);
                broadcast("LOAD_OK:" + document.getContent());
            } catch (IOException e) { out.println("LOAD_FAIL:" + e.getMessage()); }
        }

        private void broadcast(String message) {
            for (String uid : sessionManager.getUserIds()) {
                PrintWriter pw = sessionManager.getClientOutput(uid);
                if (pw != null) pw.println(message);
            }
            broadcastWs(message);
        }

        private void cleanup() {
            if (userId != null) {
                document.removeUser(userId);
                sessionManager.removeSession(userId);
            }
            try { socket.close(); } catch (IOException e) {}
        }
    }
}
