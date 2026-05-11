package com.collab.ui;

import com.collab.client.Client;
import com.collab.core.EditOperation;

import java.awt.*;
import java.awt.event.*;

public class EditorPanel extends Panel {
    private TextArea textArea;
    private Label statusLabel;
    private Label userLabel;
    private int currentVersion;
    private String lastContent;

    public EditorPanel() {
        setLayout(new BorderLayout());
        
        Panel topPanel = new Panel(new BorderLayout());
        statusLabel = new Label("Version: 0 | Connected");
        userLabel = new Label("Users: None");
        topPanel.add(statusLabel, BorderLayout.WEST);
        topPanel.add(userLabel, BorderLayout.EAST);
        add(topPanel, BorderLayout.NORTH);

        textArea = new TextArea("", 20, 80, TextArea.SCROLLBARS_VERTICAL_ONLY);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        add(textArea, BorderLayout.CENTER);

        Panel bottomPanel = new Panel(new FlowLayout(FlowLayout.LEFT));
        Button saveBtn = new Button("Save");
        Button loadBtn = new Button("Load");
        Button syncBtn = new Button("Sync");
        
        saveBtn.addActionListener(e -> saveFile());
        loadBtn.addActionListener(e -> loadFile());
        syncBtn.addActionListener(e -> Client.requestSync());
        
        bottomPanel.add(saveBtn);
        bottomPanel.add(loadBtn);
        bottomPanel.add(syncBtn);
        
        textArea.addTextListener(e -> {
            String newText = textArea.getText();
            if (lastContent != null && !newText.equals(lastContent)) {
                handleTextChange(lastContent, newText);
            }
            lastContent = newText;
        });
        
        add(bottomPanel, BorderLayout.SOUTH);
        currentVersion = 0;
    }

    private void handleTextChange(String oldText, String newText) {
        int commonPrefix = 0;
        int minLen = Math.min(oldText.length(), newText.length());
        while (commonPrefix < minLen && oldText.charAt(commonPrefix) == newText.charAt(commonPrefix)) {
            commonPrefix++;
        }

        int commonSuffix = 0;
        while (commonSuffix < minLen - commonPrefix && 
               oldText.charAt(oldText.length() - 1 - commonSuffix) == 
               newText.charAt(newText.length() - 1 - commonSuffix)) {
            commonSuffix++;
        }

        String deleted = oldText.substring(commonPrefix, oldText.length() - commonSuffix);
        String inserted = newText.substring(commonPrefix, newText.length() - commonSuffix);

        if (!deleted.isEmpty()) {
            Client.sendEdit(EditOperation.OpType.DELETE, commonPrefix, deleted);
        }
        if (!inserted.isEmpty()) {
            Client.sendEdit(EditOperation.OpType.INSERT, commonPrefix, inserted);
        }
        
        Client.sendActivity("Editing");
    }

    private void saveFile() {
        FileDialog fd = new FileDialog((Frame) getParent(), "Save File", FileDialog.SAVE);
        fd.setVisible(true);
        if (fd.getFile() != null) {
            Client.saveFile(fd.getDirectory() + fd.getFile());
        }
    }

    private void loadFile() {
        FileDialog fd = new FileDialog((Frame) getParent(), "Open File", FileDialog.LOAD);
        fd.setVisible(true);
        if (fd.getFile() != null) {
            Client.loadFile(fd.getDirectory() + fd.getFile());
        }
    }

    public void updateContent(String content) {
        textArea.setText(content);
        lastContent = content;
    }

    public void updateVersion(int version) {
        this.currentVersion = version;
        statusLabel.setText("Version: " + version + " | " + (Client.isConnected() ? "Connected" : "Disconnected"));
    }

    public void updateUserList(String users) {
        userLabel.setText("Users: " + users);
    }

    public void updateActivity(String user, String activity) {
        System.out.println(user + ": " + activity);
    }

    public void showMessage(String message) {
        statusLabel.setText(message);
    }

    public int getVersion() {
        return currentVersion;
    }

    public String getContent() {
        return textArea.getText();
    }
}