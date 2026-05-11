package com.collab.core;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class Document {
    private String name;
    private TextBuffer textBuffer;
    private List<String> activeUsers;
    private long lastModified;

    public Document(String name) {
        this.name = name;
        this.textBuffer = new TextBuffer();
        this.activeUsers = new ArrayList<>();
        this.lastModified = System.currentTimeMillis();
    }

    public Document(String name, String content) {
        this.name = name;
        this.textBuffer = new TextBuffer(content);
        this.activeUsers = new ArrayList<>();
        this.lastModified = System.currentTimeMillis();
    }

    public synchronized String applyEdit(EditOperation operation) {
        String result = textBuffer.applyOperation(operation);
        lastModified = System.currentTimeMillis();
        return result;
    }

    public synchronized String getContent() {
        return textBuffer.getContent();
    }

    public synchronized int getVersion() {
        return textBuffer.getVersion();
    }

    public synchronized List<EditOperation> getOperationsSince(int version) {
        return textBuffer.getOperationsSince(version);
    }

    public synchronized void addUser(String userId) {
        if (!activeUsers.contains(userId)) {
            activeUsers.add(userId);
        }
    }

    public synchronized void removeUser(String userId) {
        activeUsers.remove(userId);
    }

    public synchronized List<String> getActiveUsers() {
        return new ArrayList<>(activeUsers);
    }

    public String getName() { return name; }
    public long getLastModified() { return lastModified; }

    public void saveToFile(File file) throws IOException {
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(textBuffer.getContent());
        }
    }

    public void loadFromFile(File file) throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        textBuffer.setContent(content.toString());
        lastModified = System.currentTimeMillis();
    }
}