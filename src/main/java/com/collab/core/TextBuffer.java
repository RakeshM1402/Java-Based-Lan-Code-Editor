package com.collab.core;

import java.util.ArrayList;
import java.util.List;

public class TextBuffer {
    private StringBuilder buffer;
    private int version;
    private List<EditOperation> operations;

    public TextBuffer() {
        this.buffer = new StringBuilder();
        this.version = 0;
        this.operations = new ArrayList<>();
    }

    public TextBuffer(String initialContent) {
        this.buffer = new StringBuilder(initialContent);
        this.version = 0;
        this.operations = new ArrayList<>();
    }

    public synchronized String applyOperation(EditOperation op) {
        switch (op.getType()) {
            case INSERT:
                int insertPos = Math.min(op.getPosition(), buffer.length());
                buffer.insert(insertPos, op.getText());
                break;
            case DELETE:
                int deletePos = Math.min(op.getPosition(), buffer.length());
                int endPos = Math.min(deletePos + op.getText().length(), buffer.length());
                if (deletePos < endPos) {
                    buffer.delete(deletePos, endPos);
                }
                break;
            case REPLACE:
                int replacePos = Math.min(op.getPosition(), buffer.length());
                int replaceEnd = Math.min(replacePos + op.getText().length(), buffer.length());
                buffer.replace(replacePos, replaceEnd, "");
                break;
        }
        version++;
        op = new EditOperation(op.getUserId(), op.getType(), op.getPosition(), op.getText(), version);
        operations.add(op);
        return buffer.toString();
    }

    public synchronized String getContent() {
        return buffer.toString();
    }

    public synchronized int getVersion() {
        return version;
    }

    public synchronized List<EditOperation> getOperationsSince(int fromVersion) {
        if (fromVersion < 0 || fromVersion >= operations.size()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(operations.subList(fromVersion, operations.size()));
    }

    public synchronized void setContent(String content) {
        this.buffer = new StringBuilder(content);
        this.version++;
    }
}