package com.collab.server;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.util.*;

public class HttpServer {
    private com.sun.net.httpserver.HttpServer httpServer;
    private int port;
    private ServerSocket serverSocket;

    public HttpServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        httpServer = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(port), 0);
        
        httpServer.createContext("/api/server/start", exchange -> handleStart(exchange));
        httpServer.createContext("/api/server/stop", exchange -> handleStop(exchange));
        httpServer.createContext("/api/server/status", exchange -> handleStatus(exchange));
        httpServer.createContext("/api/client/join", exchange -> handleJoin(exchange));
        httpServer.createContext("/api/client/leave", exchange -> handleLeave(exchange));
        httpServer.createContext("/api/doc/content", exchange -> handleDocContent(exchange));
        httpServer.createContext("/api/doc/edit", exchange -> handleDocEdit(exchange));
        httpServer.createContext("/api/users", exchange -> handleUsers(exchange));
        
        httpServer.setExecutor(null);
        httpServer.start();
        
        System.out.println("HTTP Server started on port " + port);
    }

    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            System.out.println("HTTP Server stopped");
        }
    }

    private void handleStart(HttpExchange exchange) throws IOException {
        String response = "{\"status\":\"starting\",\"port\":" + port + "}";
        sendJsonResponse(exchange, response);
    }

    private void handleStop(HttpExchange exchange) throws IOException {
        stop();
        String response = "{\"status\":\"stopped\"}";
        sendJsonResponse(exchange, response);
    }

    private void handleStatus(HttpExchange exchange) throws IOException {
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
        
        String response = "{\"status\":\"running\",\"port\":" + port + ",\"ips\":[" + ips + "]}";
        sendJsonResponse(exchange, response);
    }

    private void handleJoin(HttpExchange exchange) throws IOException {
        String response = "{\"status\":\"join\",\"message\":\"Use socket connection\"}";
        sendJsonResponse(exchange, response);
    }

    private void handleLeave(HttpExchange exchange) throws IOException {
        String response = "{\"status\":\"leave\",\"message\":\"Use socket connection\"}";
        sendJsonResponse(exchange, response);
    }

    private void handleDocContent(HttpExchange exchange) throws IOException {
        String response = "{\"status\":\"ok\",\"content\":\"\"}";
        sendJsonResponse(exchange, response);
    }

    private void handleDocEdit(HttpExchange exchange) throws IOException {
        String response = "{\"status\":\"ok\"}";
        sendJsonResponse(exchange, response);
    }

    private void handleUsers(HttpExchange exchange) throws IOException {
        String response = "{\"status\":\"ok\",\"users\":[]}";
        sendJsonResponse(exchange, response);
    }

    private void sendJsonResponse(HttpExchange exchange, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.getBytes().length);
        OutputStream os = exchange.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }

    public static void main(String[] args) throws IOException {
        new HttpServer(8080).start();
    }
}
