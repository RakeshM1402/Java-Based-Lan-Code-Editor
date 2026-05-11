package com.collab.core;

import java.io.Serializable;
import java.util.UUID;

public class EditOperation implements Serializable {
    private static final long serialVersionUID = 1L;
    
    public enum OpType { INSERT, DELETE, REPLACE }
    
    private String id;
    private String userId;
    private OpType type;
    private int position;
    private String text;
    private long timestamp;
    private int version;

    public EditOperation(String userId, OpType type, int position, String text, int version) {
        this.id = UUID.randomUUID().toString();
        this.userId = userId;
        this.type = type;
        this.position = position;
        this.text = text;
        this.timestamp = System.currentTimeMillis();
        this.version = version;
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public OpType getType() { return type; }
    public int getPosition() { return position; }
    public String getText() { return text; }
    public long getTimestamp() { return timestamp; }
    public int getVersion() { return version; }

    public EditOperation transform(EditOperation other) {
        if (this.timestamp >= other.timestamp) return this;
        
        EditOperation transformed = new EditOperation(this.userId, this.type, this.position, this.text, this.version);
        
        if (other.type == OpType.INSERT) {
            if (other.position <= this.position) {
                transformed.position += other.text.length();
            }
        } else if (other.type == OpType.DELETE) {
            if (other.position < this.position) {
                transformed.position = Math.max(other.position, this.position - other.text.length());
            }
        }
        return transformed;
    }

    @Override
    public String toString() {
        return "EditOperation{" +
                "userId='" + userId + '\'' +
                ", type=" + type +
                ", position=" + position +
                ", text='" + text + '\'' +
                ", version=" + version +
                '}';
    }
}