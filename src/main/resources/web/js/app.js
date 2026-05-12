let socket = null;
let userId = null;
let username = null;
let serverIP = null;
let serverPort = null;
let lastContent = '';
let version = 0;

function showScreen(screenId) {
    document.querySelectorAll('.screen').forEach(s => s.classList.remove('active'));
    document.getElementById(screenId).classList.add('active');
}

function showLanding() { showScreen('landing-screen'); }
function showServerPanel() {
    showScreen('server-panel');
    fetchLANIPs();
}
function showJoinPanel() { showScreen('join-panel'); }
function showEditor() { showScreen('editor-screen'); }

async function fetchLANIPs() {
    try {
        const response = await fetch('/api/server/status');
        const data = await response.json();
        const ipsList = document.getElementById('ips-list');
        ipsList.innerHTML = '';
        if (data.ips && data.ips.length > 0) {
            data.ips.forEach(ip => {
                const div = document.createElement('div');
                div.className = 'ip-item';
                div.innerHTML = `<span>${ip}</span><button class="copy-btn" onclick="copyIP('${ip}')">Copy</button>`;
                ipsList.appendChild(div);
            });
        } else {
            ipsList.innerHTML = '<p style="color: var(--text-muted);">Detecting...</p>';
        }
    } catch (e) {
        document.getElementById('ips-list').innerHTML = '<p style="color: var(--text-muted);">Could not detect IPs</p>';
    }
}

function copyIP(ip) {
    navigator.clipboard.writeText(ip);
    const btn = event.target;
    btn.textContent = 'Copied!';
    setTimeout(() => btn.textContent = 'Copy', 1500);
}

async function startServer() {
    const port = document.getElementById('server-port').value;
    const statusEl = document.getElementById('server-status');
    try {
        const response = await fetch('/api/server/start', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        });
        const data = await response.json();
        statusEl.textContent = `Server started on port ${port}`;
        statusEl.className = 'status success';
        setTimeout(() => {
            serverPort = port;
            showEditor();
        }, 500);
    } catch (e) {
        statusEl.textContent = 'Failed to start server';
        statusEl.className = 'status error';
    }
}

function joinServer() {
    serverIP = document.getElementById('join-ip').value;
    serverPort = document.getElementById('join-port').value;
    username = document.getElementById('username').value;
    const statusEl = document.getElementById('join-status');
    
    if (!serverIP || !username) {
        statusEl.textContent = 'Please fill all fields';
        statusEl.className = 'status error';
        return;
    }
    
    statusEl.textContent = 'Connecting...';
    
    userId = 'user_' + Math.random().toString(36).substr(2, 9);
    
    try {
        socket = new WebSocket(`ws://${serverIP}:${serverPort}`);
        
        socket.onopen = () => {
            statusEl.textContent = 'Connected!';
            statusEl.className = 'status success';
            socket.send(`JOIN:${userId}:${username}`);
            showEditor();
            document.getElementById('connection-text').textContent = `Connected to ${serverIP}:${serverPort}`;
        };
        
        socket.onmessage = (event) => {
            handleMessage(event.data);
        };
        
        socket.onclose = () => {
            document.querySelector('.status-dot').style.background = '#ef4444';
            document.getElementById('connection-text').textContent = 'Disconnected';
            setTimeout(attemptReconnect, 3000);
        };
        
        socket.onerror = () => {
            statusEl.textContent = 'Connection failed';
            statusEl.className = 'status error';
        };
    } catch (e) {
        statusEl.textContent = 'Error: ' + e.message;
        statusEl.className = 'status error';
    }
}

function attemptReconnect() {
    if (socket && socket.readyState === WebSocket.OPEN) return;
    document.getElementById('connection-text').textContent = 'Reconnecting...';
    joinServer();
}

function handleMessage(message) {
    if (message.startsWith('JOIN_OK:')) {
        const parts = message.substring(8).split(':');
        if (parts.length >= 2) {
            lastContent = parts[0];
            version = parseInt(parts[1]);
            document.getElementById('code-editor').value = lastContent;
            updateVersion();
        }
    } else if (message.startsWith('EDIT_OK:')) {
        const parts = message.substring(8).split(':');
        if (parts.length >= 3) {
            lastContent = parts[0];
            version = parseInt(parts[1]);
            const editor = document.getElementById('code-editor');
            const editingUser = parts[2];
            if (editingUser !== userId) {
                editor.value = lastContent;
            }
            updateVersion();
        }
    } else if (message.startsWith('USERS_OK:')) {
        const users = message.substring(8).split(',').filter(u => u);
        const usersList = document.getElementById('users-list');
        usersList.innerHTML = users.map(u => `<span class="user-badge">${u}</span>`).join('');
    }
}

function updateVersion() {
    document.getElementById('version-info').textContent = `Version: ${version}`;
    document.getElementById('char-count').textContent = `Characters: ${lastContent.length}`;
}

const editor = document.getElementById('code-editor');
if (editor) {
    editor.addEventListener('input', () => {
        const newContent = editor.value;
        if (newContent !== lastContent) {
            handleTextChange(lastContent, newContent);
            lastContent = newContent;
            document.getElementById('char-count').textContent = `Characters: ${newContent.length}`;
        }
    });
}

function handleTextChange(oldText, newText) {
    let commonPrefix = 0;
    const minLen = Math.min(oldText.length, newText.length);
    while (commonPrefix < minLen && oldText[commonPrefix] === newText[commonPrefix]) {
        commonPrefix++;
    }
    
    let commonSuffix = 0;
    while (commonSuffix < minLen - commonPrefix && 
           oldText[oldText.length - 1 - commonSuffix] === newText[newText.length - 1 - commonSuffix]) {
        commonSuffix++;
    }
    
    const deleted = oldText.substring(commonPrefix, oldText.length - commonSuffix);
    const inserted = newText.substring(commonPrefix, newText.length - commonSuffix);
    
    if (socket && socket.readyState === WebSocket.OPEN) {
        if (deleted) {
            socket.send(`EDIT:${userId}:${version}:DELETE:${commonPrefix}:${deleted}`);
        }
        if (inserted) {
            socket.send(`EDIT:${userId}:${version}:INSERT:${commonPrefix}:${inserted}`);
        }
        socket.send(`ACTIVITY:Editing`);
    }
}

function leaveSession() {
    if (socket) {
        socket.send(`LEAVE:${userId}`);
        socket.close();
    }
    socket = null;
    userId = null;
    username = null;
    lastContent = '';
    version = 0;
    showLanding();
}

setInterval(() => {
    if (socket && socket.readyState === WebSocket.OPEN) {
        socket.send('PING');
    }
}, 10000);
