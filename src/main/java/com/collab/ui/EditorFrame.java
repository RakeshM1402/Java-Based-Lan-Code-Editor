package com.collab.ui;

import com.collab.client.Client;

import java.awt.*;
import java.awt.event.*;

public class EditorFrame extends Frame {
    private EditorPanel editorPanel;
    private Class<?> clientClass;

    public EditorFrame(Class<?> clientClass, String title) {
        super(title);
        this.clientClass = clientClass;
        
        setLayout(new BorderLayout());
        setSize(800, 600);
        setLocation(100, 100);

        editorPanel = new EditorPanel();
        add(editorPanel, BorderLayout.CENTER);

        MenuBar menuBar = new MenuBar();
        Menu fileMenu = new Menu("File");
        MenuItem saveItem = new MenuItem("Save", new MenuShortcut(KeyEvent.VK_S));
        MenuItem loadItem = new MenuItem("Open", new MenuShortcut(KeyEvent.VK_O));
        MenuItem exitItem = new MenuItem("Exit", new MenuShortcut(KeyEvent.VK_Q));
        
        saveItem.addActionListener(e -> saveFile());
        loadItem.addActionListener(e -> loadFile());
        exitItem.addActionListener(e -> System.exit(0));
        
        fileMenu.add(saveItem);
        fileMenu.add(loadItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);
        menuBar.add(fileMenu);

        Menu editMenu = new Menu("Edit");
        MenuItem syncItem = new MenuItem("Sync Now", new MenuShortcut(KeyEvent.VK_R));
        syncItem.addActionListener(e -> Client.requestSync());
        editMenu.add(syncItem);
        menuBar.add(editMenu);

        setMenuBar(menuBar);

        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                try {
                    if (clientClass != null) {
                        java.lang.reflect.Method stopMethod = clientClass.getMethod("stop");
                        stopMethod.invoke(null);
                    }
                } catch (Exception ex) {}
                System.exit(0);
            }
        });

        setVisible(true);
    }

    public void updateContent(String content) {
        editorPanel.updateContent(content);
    }

    public void updateVersion(int version) {
        editorPanel.updateVersion(version);
    }

    public void updateUserList(String users) {
        editorPanel.updateUserList(users);
    }

    public void updateActivity(String user, String activity) {
        editorPanel.updateActivity(user, activity);
    }

    public void showMessage(String message) {
        editorPanel.showMessage(message);
    }

    public int getVersion() {
        return editorPanel.getVersion();
    }

    private void saveFile() {
        FileDialog fd = new FileDialog(this, "Save File", FileDialog.SAVE);
        fd.setVisible(true);
        if (fd.getFile() != null) {
            String path = fd.getDirectory() + fd.getFile();
            if (clientClass != null) {
                Client.saveFile(path);
            } else {
                try {
                    java.io.FileWriter fw = new java.io.FileWriter(path);
                    fw.write(editorPanel.getContent());
                    fw.close();
                    showMessage("File saved: " + fd.getFile());
                } catch (Exception ex) {
                    showMessage("Save error: " + ex.getMessage());
                }
            }
        }
    }

    private void loadFile() {
        FileDialog fd = new FileDialog(this, "Open File", FileDialog.LOAD);
        fd.setVisible(true);
        if (fd.getFile() != null) {
            String path = fd.getDirectory() + fd.getFile();
            if (clientClass != null) {
                Client.loadFile(path);
            } else {
                try {
                    java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(path));
                    StringBuilder content = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        content.append(line).append("\n");
                    }
                    br.close();
                    editorPanel.updateContent(content.toString());
                    showMessage("File loaded: " + fd.getFile());
                } catch (Exception ex) {
                    showMessage("Load error: " + ex.getMessage());
                }
            }
        }
    }
}