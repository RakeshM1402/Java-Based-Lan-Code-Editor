package com.collab;

import com.collab.server.Server;
import com.collab.client.Client;
import com.collab.ui.EditorFrame;

public class Main {
    public static void main(String[] args) {
        if (args.length == 0) {
            showUsage();
            return;
        }

        String mode = args[0].toLowerCase();

        try {
            switch (mode) {
                case "server":
                    int port = args.length > 1 ? Integer.parseInt(args[1]) : 5000;
                    System.out.println("Starting server on port " + port + "...");
                    Server.start(port);
                    break;
                case "client":
                    String serverIP = args.length > 1 ? args[1] : "localhost";
                    int serverPort = args.length > 2 ? Integer.parseInt(args[2]) : 5000;
                    String username = args.length > 3 ? args[3] : "User" + (int)(Math.random() * 1000);
                    System.out.println("Starting client - connecting to " + serverIP + ":" + serverPort);
                    Client.start(serverIP, serverPort, username);
                    break;
                case "standalone":
                    System.out.println("Starting standalone mode...");
                    new EditorFrame(null, "Standalone Editor");
                    break;
                default:
                    showUsage();
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void showUsage() {
        System.out.println("=== Collaborative Code Editor ===");
        System.out.println("Usage:");
        System.out.println("  java -jar CollaborativeEditor.jar server [port]        - Start server (default: 5000)");
        System.out.println("  java -jar CollaborativeEditor.jar client <ip> [port]   - Connect to server");
        System.out.println("  java -jar CollaborativeEditor.jar standalone           - Run without networking");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  Server: java -jar CollaborativeEditor.jar server");
        System.out.println("  Client: java -jar CollaborativeEditor.jar client 192.168.1.100 5000");
    }
}