package com.collab.persistence;

import com.collab.core.Document;
import java.util.List;

public class VersionController {
    private SQLiteRepo repo;
    private String currentDocName;
    private int currentVersion;

    public VersionController(String dbPath, String docName) throws Exception {
        this.repo = new SQLiteRepo(dbPath);
        this.currentDocName = docName;
        this.currentVersion = 0;
    }

    public void saveVersion(Document document, String userId) throws Exception {
        int version = document.getVersion();
        if (version > currentVersion) {
            repo.saveVersion(currentDocName, version, document.getContent(), userId);
            currentVersion = version;
        }
    }

    public String loadVersion(int version) throws Exception {
        return repo.getVersion(currentDocName, version);
    }

    public List<String[]> getVersionHistory() throws Exception {
        return repo.getVersions(currentDocName);
    }

    public int getCurrentVersion() {
        return currentVersion;
    }

    public void close() throws Exception {
        repo.close();
    }
}