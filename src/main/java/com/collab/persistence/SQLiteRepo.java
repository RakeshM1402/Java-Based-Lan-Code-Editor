package com.collab.persistence;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SQLiteRepo {
    private Connection connection;
    private String dbPath;

    public SQLiteRepo(String dbPath) throws SQLException {
        this.dbPath = dbPath;
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        initializeTables();
    }

    private void initializeTables() throws SQLException {
        String createVersionsTable = "CREATE TABLE IF NOT EXISTS versions (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "document_name TEXT NOT NULL," +
                "version INTEGER NOT NULL," +
                "content TEXT NOT NULL," +
                "user_id TEXT," +
                "timestamp INTEGER NOT NULL" +
                ")";

        String createSessionsTable = "CREATE TABLE IF NOT EXISTS sessions (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "user_id TEXT NOT NULL," +
                "username TEXT NOT NULL," +
                "join_time INTEGER NOT NULL," +
                "last_activity INTEGER NOT NULL" +
                ")";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createVersionsTable);
            stmt.execute(createSessionsTable);
        }
    }

    public void saveVersion(String docName, int version, String content, String userId) throws SQLException {
        String sql = "INSERT INTO versions (document_name, version, content, user_id, timestamp) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, docName);
            pstmt.setInt(2, version);
            pstmt.setString(3, content);
            pstmt.setString(4, userId);
            pstmt.setLong(5, System.currentTimeMillis());
            pstmt.executeUpdate();
        }
    }

    public List<String[]> getVersions(String docName) throws SQLException {
        List<String[]> versions = new ArrayList<>();
        String sql = "SELECT version, content, user_id, timestamp FROM versions WHERE document_name = ? ORDER BY version";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, docName);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                versions.add(new String[]{
                    String.valueOf(rs.getInt("version")),
                    rs.getString("content"),
                    rs.getString("user_id"),
                    String.valueOf(rs.getLong("timestamp"))
                });
            }
        }
        return versions;
    }

    public String getVersion(String docName, int version) throws SQLException {
        String sql = "SELECT content FROM versions WHERE document_name = ? AND version = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, docName);
            pstmt.setInt(2, version);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("content");
            }
        }
        return null;
    }

    public void saveSession(String userId, String username, long joinTime) throws SQLException {
        String sql = "INSERT OR REPLACE INTO sessions (user_id, username, join_time, last_activity) VALUES (?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            pstmt.setString(2, username);
            pstmt.setLong(3, joinTime);
            pstmt.setLong(4, System.currentTimeMillis());
            pstmt.executeUpdate();
        }
    }

    public void close() throws SQLException {
        if (connection != null) {
            connection.close();
        }
    }
}