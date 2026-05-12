let socket = null;
let userId = null;
let username = null;
let serverIP = null;
let serverPort = null;
let lastContent = '';
let version = 0;
let reconnectAttempts = 0;
const MAX_RECONNECT = 5;

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
    
    if (reconnectAttempts >= MAX_RECONNECT) {
        statusEl.textContent = 'Max reconnection attempts reached';
        statusEl.className = 'status error';
        return;
    }
    
    statusEl.textContent = 'Connecting...';
    reconnectAttempts++;
    
    userId = 'user_' + Math.random().toString(36).substr(2, 9);
    
    try {
        const wsUrl = `ws://${serverIP}:${serverPort}/ws`;
        console.log('Connecting to WebSocket:', wsUrl);
        
        socket = new WebSocket(wsUrl);
        
        socket.onopen = () => {
            console.log('WebSocket connected!');
            statusEl.textContent = 'Connected!';
            statusEl.className = 'status success';
            document.querySelector('.status-dot').style.background = '#22c55e';
            document.getElementById('connection-text').textContent = `Connected to ${serverIP}:${serverPort}`;
            reconnectAttempts = 0;
            
            socket.send(`JOIN:${userId}:${username}`);
            showEditor();
        };
        
        socket.onmessage = (event) => {
            console.log('Received:', event.data);
            handleMessage(event.data);
        };
        
        socket.onclose = (event) => {
            console.log('WebSocket closed:', event.code, event.reason);
            document.querySelector('.status-dot').style.background = '#ef4444';
            document.getElementById('connection-text').textContent = 'Disconnected';
            
            if (reconnectAttempts < MAX_RECONNECT) {
                setTimeout(() => {
                    statusEl.textContent = `Reconnecting... (${reconnectAttempts}/${MAX_RECONNECT})`;
                    joinServer();
                }, 3000);
            }
        };
        
        socket.onerror = (error) => {
            console.error('WebSocket error:', error);
            statusEl.textContent = 'Connection failed';
            statusEl.className = 'status error';
        };
    } catch (e) {
        statusEl.textContent = 'Error: ' + e.message;
        statusEl.className = 'status error';
    }
}

function handleMessage(message) {
    if (message.startsWith('PONG')) {
        return;
    }
    
    if (message.startsWith('JOIN_OK:')) {
        const parts = message.split(':');
        if (parts.length >= 2) {
            lastContent = parts[0];
            version = parseInt(parts[1]);
            const editor = document.getElementById('code-editor');
            if (editor) {
                editor.value = lastContent;
            }
            updateVersion();
            updateCharCount();
        }
    } else if (message.startsWith('EDIT_OK:')) {
        const parts = message.split(':');
        if (parts.length >= 3) {
            const content = parts[0];
            const newVersion = parseInt(parts[1]);
            const editingUser = parts[2];
            
            if (content !== lastContent) {
                lastContent = content;
                version = newVersion;
                
                const editor = document.getElementById('code-editor');
                if (editor && editingUser !== userId) {
                    const cursorPos = editor.selectionStart;
                    editor.value = content;
                    if (cursorPos <= content.length) {
                        editor.setSelectionRange(cursorPos, cursorPos);
                    }
                }
                updateVersion();
                updateCharCount();
            }
        }
    } else if (message.startsWith('SYNC_OK:')) {
        const parts = message.split(':');
        if (parts.length >= 3) {
            lastContent = parts[0];
            version = parseInt(parts[1]);
            const editor = document.getElementById('code-editor');
            if (editor) {
                editor.value = lastContent;
            }
            updateVersion();
            updateCharCount();
        }
    } else if (message.startsWith('USERS_OK:')) {
        const users = message.split(':')[1].split(',').filter(u => u);
        const usersList = document.getElementById('users-list');
        if (usersList) {
            usersList.innerHTML = users.map(u => `<span class="user-badge">${u}</span>`).join('');
        }
    }
}

function updateVersion() {
    const versionEl = document.getElementById('version-info');
    if (versionEl) {
        versionEl.textContent = `Version: ${version}`;
    }
}

function updateCharCount() {
    const charEl = document.getElementById('char-count');
    if (charEl) {
        charEl.textContent = `Characters: ${lastContent.length}`;
    }
}

const editor = document.getElementById('code-editor');
if (editor) {
    editor.addEventListener('input', () => {
        const newContent = editor.value;
        if (newContent !== lastContent) {
            handleTextChange(lastContent, newContent);
            lastContent = newContent;
            updateCharCount();
        }
    });
}

function handleTextChange(oldText, newText) {
    if (!socket || socket.readyState !== WebSocket.OPEN) {
        return;
    }
    
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
    
    if (deleted) {
        socket.send(`EDIT:${userId}:${version}:DELETE:${commonPrefix}:${deleted}`);
    }
    if (inserted) {
        socket.send(`EDIT:${userId}:${version}:INSERT:${commonPrefix}:${inserted}`);
    }
}

function leaveSession() {
    reconnectAttempts = MAX_RECONNECT;
    if (socket) {
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
