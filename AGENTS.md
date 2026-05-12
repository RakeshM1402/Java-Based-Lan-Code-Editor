# Collaborative Code Editor - LAN Edition

## Project Overview

**Purpose**: Java-based real-time collaborative code editor for LAN use (2+ devices)
**Tech Stack**: Java 17, Maven, SQLite, HTML/CSS/JS frontend

---

## Current Issues to Fix

| Issue | File | Fix |
|-------|------|-----|
| No LEAVE handler | Server.java | Add handleLeave() method |
| No server IP display | Server.java | Show all LAN IPs on startup |
| File path bug | EditorPanel.java | Fix null directory handling |
| No heartbeat | Client.java | Add ping/pong mechanism |
| No auto-reconnect | Client.java | Add reconnect logic |

---

## Implementation Plan

### Phase 1: Backend Fixes

**1. Server.java**
- Add LEAVE message handler
- Add method to get all LAN IPs using NetworkInterface API
- Display all IPs on server startup

**2. Client.java**
- Add heartbeat thread (send PING every 10s)
- Add auto-reconnect on connection loss

**3. EditorPanel.java**
- Fix file path null check

### Phase 2: Frontend Integration

**pom.xml additions**:
- Use built-in com.sun.net.httpserver
- Serve static files from src/main/resources/web/

### Phase 3: Modern Web Frontend

**Directory**: src/main/resources/web/
- index.html - Landing page + Editor
- css/styles.css - Glassmorphism styling
- js/app.js - Server/client logic

**UI Flow**:
1. Landing Screen - Start Server | Join Server
2. Server Panel - Enter port, shows LAN IPs with copy
3. Join Panel - Enter IP, port, username
4. Editor View - Real-time collaborative editor

**Design - Glassmorphism**:
- Background: Animated gradient mesh (purple/blue/pink)
- Cards: backdrop-filter blur with transparency
- Fonts: Outfit (display), JetBrains Mono (code)
- Buttons: Gradient with glow effects
- Animations: Staggered fade-in

### Phase 4: API Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| /api/server/start | POST | Start server |
| /api/server/stop | POST | Stop server |
| /api/server/status | GET | Get status + IPs |
| /api/client/join | POST | Connect to server |
| /api/client/leave | POST | Disconnect |
| /api/doc/content | GET | Get document |
| /api/doc/edit | POST | Send edit |
| /api/users | GET | Get users |

### Phase 5: LAN Testing

- Server shows all network interfaces
- User copies appropriate IP
- Works with 2+ devices on same network

---

## Commands

```bash
# Build
mvn clean package

# Run Server
java -jar CollaborativeEditor.jar server 5000

# Run Client  
java -jar CollaborativeEditor.jar client 192.168.1.x 5000 username
```

---

## Status

- [x] Code reviewed
- [x] Backend bugs fixed
- [x] Frontend created
- [x] HTTP integration done
- [x] One-command setup created (mvnw, install.bat, install.sh)
- [x] README.md documentation added
- [ ] LAN tested
