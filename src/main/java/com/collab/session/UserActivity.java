package com.collab.session;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class UserActivity implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String userId;
    private String username;
    private long joinTime;
    private long lastActivityTime;
    private String currentAction;
    private List<String> recentActions;

    public UserActivity(String userId, String username) {
        this.userId = userId;
        this.username = username;
        this.joinTime = System.currentTimeMillis();
        this.lastActivityTime = joinTime;
        this.currentAction = "Idle";
        this.recentActions = new ArrayList<>();
    }

    public synchronized void recordActivity(String action) {
        this.currentAction = action;
        this.lastActivityTime = System.currentTimeMillis();
        recentActions.add(action + " at " + lastActivityTime);
        if (recentActions.size() > 20) {
            recentActions.remove(0);
        }
    }

    public String getUserId() { return userId; }
    public String getUsername() { return username; }
    public long getJoinTime() { return joinTime; }
    public long getLastActivityTime() { return lastActivityTime; }
    public String getCurrentAction() { return currentAction; }
    public List<String> getRecentActions() { return new ArrayList<>(recentActions); }

    public long getActiveDuration() {
        return lastActivityTime - joinTime;
    }
}