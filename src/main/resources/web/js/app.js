let socket = null;
let userId = null;
let lastContent = '';
let version = 0;

const editorIP = window.location.hostname;
const editorPort = 5200;

function showScreen(screenId) {
    document.querySelectorAll('.screen').forEach(s => s.classList.remove('active'));
    document.getElementById(screenId).classList.add('screen ' + screenId);
}

function showJoin() { showScreen('join-screen'); }
function showEditor() { showScreen('editor-screen'); }

window.addEventListener('load', () => {
    document.getElementById('join-ip').value = editorIP;
    document.getElementById('join-port').value = editorPort;
    showJoin();
});

function connectToServer() {
    const serverIP = document.getElementById('join-ip').value.trim();
    const serverPort = document.getElementById('join-port').value.trim();
    const username = document.getElementById('username').value.trim();
    const statusEl = document.getElementById('join-status');
    const errorEl = document.getElementById('connection-error');
    errorEl.style.display = 'none';
    
    if (!serverIP || !username) {
        errorEl.textContent = 'Please fill all fields';
        errorEl.style.display = 'block';
        return;
    }
    
    statusEl.textContent = 'Connecting...';
    
    userId = 'user_' + Math.random().toString(36).substr(2, 9);
    
    try {
        const wsUrl = `ws://${serverIP}:${serverPort}`;
        socket = new WebSocket(wsUrl);
        
        socket.onopen = () => {
            statusEl.textContent = 'Connected!';
            document.querySelector('.status-dot').style.background = '#22c55e';
            document.getElementById('connection-text').textContent = `Connected to ${serverIP}:${serverPort}`;
            socket.send('\u0000JOIN:' + userId + ':' + username + '\u0000');
            showEditor();
        };
        
        socket.onmessage = (event) => handleMessage(event.data);
        
        socket.onclose = () => {
            document.querySelector('.status-dot').style.background = '#ef4444';
            document.getElementById('connection-text').textContent = 'Disconnected';
            statusEl.textContent = 'Connection lost. Refresh to reconnect.';
        };
        
        socket.onerror = () => {
            statusEl.textContent = 'Connection failed';
            errorEl.textContent = 'Cannot connect. Make sure server is running.';
            errorEl.style.display = 'block';
        };
    } catch (e) {
        statusEl.textContent = 'Error: ' + e.message;
    }
}

function handleMessage(message) {
    if (message.startsWith('PONG')) return;
    
    if (message.startsWith('JOIN_OK:')) {
        const parts = message.split(':');
        if (parts.length >= 2) {
            lastContent = parts[0];
            version = parseInt(parts[1]);
            document.getElementById('code-editor').value = lastContent;
            updateVersion();
            updateCharCount();
        }
    } else if (message.startsWith('EDIT_OK:')) {
        const parts = message.split(':');
        if (parts.length >= 3) {
            const content = parts[0];
            if (content !== lastContent) {
                lastContent = content;
                version = parseInt(parts[1]);
                document.getElementById('code-editor').value = content;
                updateVersion();
                updateCharCount();
            }
        }
    }
}

function updateVersion() {
    document.getElementById('version-info').textContent = `Version: ${version}`;
}

function updateCharCount() {
    document.getElementById('char-count').textContent = `Characters: ${lastContent.length}`;
}

document.getElementById('code-editor').addEventListener('input', () => {
    const newContent = document.getElementById('code-editor').value;
    if (newContent !== lastContent) {
        handleTextChange(lastContent, newContent);
        lastContent = newContent;
        updateCharCount();
    }
});

function handleTextChange(oldText, newText) {
    if (!socket || socket.readyState !== WebSocket.OPEN) return;
    
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
    
    if (deleted) socket.send('\u0000EDIT:' + userId + ':' + version + ':DELETE:' + commonPrefix + ':' + deleted + '\u0000');
    if (inserted) socket.send('\u0000EDIT:' + userId + ':' + version + ':INSERT:' + commonPrefix + ':' + inserted + '\u0000');
}

function leaveSession() {
    if (socket) socket.close();
    socket = null;
    userId = null;
    lastContent = '';
    version = 0;
    showJoin();
}

setInterval(() => {
    if (socket && socket.readyState === WebSocket.OPEN) {
        socket.send('\u0000PING\u0000');
    }
}, 10000);
