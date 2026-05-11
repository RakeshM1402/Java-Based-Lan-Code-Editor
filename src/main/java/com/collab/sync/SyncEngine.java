package com.collab.sync;

import com.collab.core.Document;
import com.collab.core.EditOperation;
import com.collab.core.TextBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class SyncEngine {
    private Document document;
    private ConflictResolver conflictResolver;
    private Map<String, Integer> userVersions;
    private List<EditOperation> pendingOperations;
    private int globalVersion;
    private volatile boolean isLocked;

    public SyncEngine(Document document) {
        this.document = document;
        this.conflictResolver = new ConflictResolver();
        this.userVersions = new ConcurrentHashMap<>();
        this.pendingOperations = new CopyOnWriteArrayList<>();
        this.globalVersion = 0;
        this.isLocked = false;
    }

    public synchronized String processOperation(EditOperation operation) {
        if (isLocked) {
            pendingOperations.add(operation);
            return document.getContent();
        }

        int expectedVersion = userVersions.getOrDefault(operation.getUserId(), 0);
        
        if (operation.getVersion() < globalVersion) {
            List<EditOperation> ops = document.getOperationsSince(operation.getVersion());
            for (EditOperation existing : ops) {
                operation = operation.transform(existing);
            }
        }

        String result = document.applyEdit(operation);
        globalVersion = document.getVersion();
        userVersions.put(operation.getUserId(), globalVersion);
        
        return result;
    }

    public synchronized List<EditOperation> getOperationsForUser(String userId, int fromVersion) {
        return document.getOperationsSince(fromVersion);
    }

    public synchronized void lockEditing() {
        isLocked = true;
    }

    public synchronized void unlockEditing() {
        isLocked = false;
        for (EditOperation op : pendingOperations) {
            processOperation(op);
        }
        pendingOperations.clear();
    }

    public boolean isLocked() {
        return isLocked;
    }

    public int getGlobalVersion() {
        return globalVersion;
    }

    public int getUserVersion(String userId) {
        return userVersions.getOrDefault(userId, 0);
    }

    public List<EditOperation> getPendingOperations() {
        return new ArrayList<>(pendingOperations);
    }
}