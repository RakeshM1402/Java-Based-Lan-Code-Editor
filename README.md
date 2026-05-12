# Collaborative Code Editor - LAN Edition

Real-time collaborative code editor for Local Area Network (LAN) collaboration.

![Java](https://img.shields.io/badge/Java-17+-blue)
![Platform](https://img.shields.io/badge/Platform-Windows%20%7C%20Linux%20%7C%20Mac-blue)

## Features

- **Real-time Collaboration** - Multiple users can edit simultaneously
- **Modern Web Interface** - Beautiful glassmorphism UI in your browser
- **Cross-Platform** - Works on Windows, Linux, and Mac
- **No Setup Required** - One-click installation
- **LAN Ready** - Share your server IP with friends on the same network

---

## Quick Start

### 1. One-Command Setup

**Windows:**
```cmd
install.bat
```

**Linux/Mac:**
```bash
bash install.sh
```

This automatically:
- Checks for Java 17
- Downloads Maven (if needed)
- Builds the project with all dependencies

### 2. Start the Server

```bash
cd target
java -jar CollaborativeEditor.jar server 5000
```

You'll see output like:
```
=== Server started on port 5000 ===
Available LAN addresses:
  192.168.1.100 (Wi-Fi)
  10.0.0.1 (Ethernet)
```

### 3. Access Web UI

Open your browser and go to:
```
http://localhost:5100
```

Or from another device on your network:
```
http://192.168.1.100:5100
```

### 4. Connect Clients

On another device, run:
```bash
java -jar CollaborativeEditor.jar client 192.168.1.100 5000 MyName
```

---

## Project Structure

```
├── src/main/
│   ├── java/com/collab/
│   │   ├── server/    # Server & HTTP server
│   │   ├── client/    # Client connection
│   │   ├── ui/        # Swing UI components
│   │   ├── core/      # Document, Edit operations
│   │   ├── sync/      # Sync engine & conflict resolver
│   │   ├── session/   # Session management
│   │   └── persistence/ # SQLite database
│   └── resources/web/  # Web UI (HTML/CSS/JS)
├── install.bat         # Windows setup script
├── install.sh          # Linux/Mac setup script
├── mvnw/mvnw.cmd      # Maven wrapper
└── pom.xml             # Maven configuration
```

---

## How It Works

### Architecture

| Component | Port | Description |
|-----------|------|-------------|
| TCP Server | 5000 | Handles client connections, real-time sync |
| HTTP Server | 5100 | Serves web UI dashboard |

### Communication Flow

```
Client <--TCP--> Server (port 5000)
Browser <--HTTP--> HTTP Server (port 5100)
```

### Message Protocol

| Message | Format | Description |
|---------|--------|-------------|
| JOIN | `JOIN:userId:username` | Connect to server |
| EDIT | `EDIT:userId:version:type:pos:text` | Send edit operation |
| LEAVE | `LEAVE:userId` | Disconnect from server |
| PING/PONG | Heartbeat | Keep connection alive |

---

## Requirements

- **Java 17** or higher
- **Internet connection** (for first-time setup only)

---

## Troubleshooting

### "Java not found" error
Install Java 17 from [Adoptium](https://adoptium.net/) or [Oracle](https://www.oracle.com/java/technologies/downloads/)

### Can't connect to server
1. Make sure server is running
2. Check firewall allows connection on port 5000
3. Ensure both devices are on the same network

### Web UI not loading
- Server HTTP is on port 5100, not 5000
- Check if port 5100 is blocked by firewall

### Build fails
1. Delete `target` folder
2. Run `mvnw.cmd clean install` (Windows) or `./mvnw clean install` (Linux/Mac)
3. Check for Java 17: `java -version`

---

## For Developers

### Build Commands

```bash
# Standard Maven build
mvn clean package

# Or use Maven wrapper (no Maven install needed)
./mvnw clean package   # Linux/Mac
mvnw.cmd clean package # Windows

# Skip tests
mvn clean package -DskipTests
```

### Port Configuration

Edit `Server.java` to change default ports:
```java
Server.start(5000, 5100);  // TCP port, HTTP port
```

---

## License

MIT License - Free to use, modify, and distribute.