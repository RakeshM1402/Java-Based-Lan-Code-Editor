package com.collab.session;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {
    private Map<String, UserActivity> activeSessions;
    private Map<String, PrintWriter> clientOutputs;
    private int maxSessions;

    public SessionManager(int maxSessions) {
        this.activeSessions = new ConcurrentHashMap<>();
        this.clientOutputs = new ConcurrentHashMap<>();
        this.maxSessions = maxSessions;
    }

    public synchronized boolean addSession(String userId, String username, PrintWriter out) {
        if (activeSessions.size() >= maxSessions) {
            return false;
        }
        if (activeSessions.containsKey(userId)) {
            return false;
        }
        UserActivity activity = new UserActivity(userId, username);
        activeSessions.put(userId, activity);
        clientOutputs.put(userId, out);
        broadcastUserList();
        return true;
    }

    public synchronized void removeSession(String userId) {
        activeSessions.remove(userId);
        clientOutputs.remove(userId);
        broadcastUserList();
    }

    public synchronized void updateActivity(String userId, String action) {
        UserActivity activity = activeSessions.get(userId);
        if (activity != null) {
            activity.recordActivity(action);
            broadcastActivity(userId, action);
        }
    }

    public synchronized List<UserActivity> getActiveSessions() {
        return new ArrayList<>(activeSessions.values());
    }

    public synchronized int getActiveCount() {
        return activeSessions.size();
    }

    public synchronized Set<String> getUserIds() {
        return new HashSet<>(activeSessions.keySet());
    }

    public PrintWriter getClientOutput(String userId) {
        return clientOutputs.get(userId);
    }

    private void broadcastUserList() {
        String message = "USERS:" + getUserListJson();
        broadcast(message);
    }

    private void broadcastActivity(String userId, String action) {
        UserActivity activity = activeSessions.get(userId);
        if (activity != null) {
            String message = "ACTIVITY:" + userId + ":" + activity.getUsername() + ":" + action;
            broadcast(message);
        }
    }

    private void broadcast(String message) {
        for (PrintWriter out : clientOutputs.values()) {
            if (out != null) {
                out.println(message);
                out.flush();
            }
        }
    }

    private String getUserListJson() {
        StringBuilder sb = new StringBuilder();
        for (UserActivity activity : activeSessions.values()) {
            if (sb.length() > 0) sb.append(",");
            sb.append("{\"id\":\"").append(activity.getUserId())
              .append("\",\"name\":\"").append(activity.getUsername()).append("\"}");
        }
        return sb.toString();
    }

    public String getSessionSummary() {
        StringBuilder sb = new StringBuilder("=== Session Summary ===\n");
        sb.append("Active Users: ").append(activeSessions.size()).append("\n");
        for (UserActivity activity : activeSessions.values()) {
            sb.append("- ").append(activity.getUsername())
              .append(" (").append(activity.getCurrentAction()).append(")\n");
        }
        return sb.toString();
    }
}