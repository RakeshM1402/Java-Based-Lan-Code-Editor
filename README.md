# <span style="background: linear-gradient(135deg, #8b5cf6, #06b6d4, #f472b6); -webkit-background-clip: text; -webkit-text-fill-color: transparent;">CodeSync</span>

### Real-time Collaborative Code Editor for LAN

<p align="center">
  <img src="https://img.shields.io/badge/Java-17+-blue?style=for-the-badge" alt="Java">
  <img src="https://img.shields.io/badge/Platform-Windows%20%7C%20Linux%20%7C%20Mac-blue?style=for-the-badge" alt="Platform">
  <img src="https://img.shields.io/badge/License-MIT-green?style=for-the-badge" alt="License">
</p>

---

## ⚡ Get Started in 3 Steps

### Step 1: Download & Extract

```bash
git clone https://github.com/RakeshM1402/Java-Based-Lan-Code-Editor.git
cd Java-Based-Lan-Code-Editor
```

### Step 2: One-Click Install

**Windows** — Double-click or run in cmd:
```cmd
install.bat
```

**Linux / Mac**:
```bash
bash install.sh
```

> This automatically checks for Java 17, downloads Maven, and builds the project.

### Step 3: Start Collaborating

```bash
# Run the server
java -jar target\CollaborativeEditor.jar server 5000
```

Open in browser:
```
http://localhost:5100
```

---

## 🎨 Features

| Feature | Description |
|---------|-------------|
| 🌐 **Web Interface** | Modern glassmorphism UI in your browser |
| ⚡ **Real-time Sync** | See edits instantly as others type |
| 🔒 **LAN Ready** | Works without internet — just same network |
| 🎯 **Zero Config** | One script does everything |
| 📱 **Cross-Platform** | Windows, Linux, Mac support |

---

## 📱 How to Connect Friends

### 1️⃣ Start Server
```bash
java -jar target\CollaborativeEditor.jar server 5000
```

You'll see available IPs:
```
=== Server started on port 5000 ===
Available LAN addresses:
  192.168.1.100 (Wi-Fi)
```

### 2️⃣ Share Your IP
Pick the IP shown and share it with friends.

### 3️⃣ Friends Join
```bash
java -jar CollaborativeEditor.jar client 192.168.1.100 5000 FriendName
```

---

## 🗂️ Project Structure

```
Java-Based-Lan-Code-Editor/
├── src/main/
│   ├── java/com/collab/
│   │   ├── server/         # TCP + HTTP server
│   │   ├── client/         # Client connection
│   │   ├── ui/            # Swing editor panel
│   │   ├── core/          # Document & operations
│   │   ├── sync/          # Sync engine
│   │   ├── session/       # User sessions
│   │   └── persistence/   # SQLite storage
│   └── resources/web/      # Web UI
│       ├── index.html      # Main app
│       ├── css/styles.css  # Glassmorphism styling
│       └── js/app.js       # Client logic
│
├── install.bat            # Windows setup
├── install.sh             # Linux/Mac setup
├── mvnw / mvnw.cmd       # Maven wrapper
├── pom.xml               # Dependencies
└── README.md             # You are here
```

---

## 🔧 Manual Build (Optional)

If you prefer not to use the install scripts:

```bash
# Install Maven (https://maven.apache.org/install.html)
mvn clean package

# Or use Maven wrapper (no Maven install needed)
./mvnw clean package     # Linux/Mac
mvnw.cmd clean package  # Windows
```

---

## ❓ Troubleshooting

| Problem | Solution |
|---------|----------|
| `Java not found` | Install Java 17 from [adoptium.net](https://adoptium.net/) |
| Can't connect | Ensure both devices on same network, check firewall |
| Web UI won't load | Use port **5100**, not 5000 |
| Build fails | Delete `target/` folder and run `mvnw.cmd clean package` |

---

## 📖 For Developers

### Architecture

```
┌─────────────────────────────────────────────────────┐
│                     Browser                          │
│            http://localhost:5100                     │
└─────────────────────┬───────────────────────────────┘
                      │ HTTP
┌─────────────────────▼───────────────────────────────┐
│                 HTTP Server (5100)                  │
│              Serves Web UI + API                    │
└─────────────────────┬───────────────────────────────┘
                      │ TCP Sockets
┌─────────────────────▼───────────────────────────────┐
│                 TCP Server (5000)                    │
│            Handles Collaboration                     │
└─────────────────────────────────────────────────────┘
```

### Message Protocol

| Message | Format | Purpose |
|---------|--------|---------|
| JOIN | `JOIN:userId:username` | Connect |
| EDIT | `EDIT:userId:version:type:pos:text` | Edit document |
| SYNC | `SYNC` | Request full document |
| LEAVE | `LEAVE:userId` | Disconnect |
| PING/PONG | — | Keep connection alive |

---

<p align="center" style="margin-top: 40px; opacity: 0.6;">
Built with ☕ and 🎨 — Free to use under MIT License
</p>