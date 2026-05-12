let socket = null;
let userId = null;
let username = null;
let serverIP = null;
let serverPort = null;
let lastContent = '';
let version = 0;
let isConnected = false;

function showScreen(screenId) {
    document.querySelectorAll('.screen').forEach(s => s.classList.remove('active'));
    document.getElementById(screenId).classList.add('active');
}

function showJoin() { showScreen('join-screen'); }
function showEditor() { showScreen('editor-screen'); }

function connectToServer() {
    serverIP = document.getElementById('join-ip').value.trim();
    serverPort = document.getElementById('join-port').value.trim();
    username = document.getElementById('username').value.trim();
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
        const wsUrl = `ws://${serverIP}:${serverPort}/ws`;
        console.log('Connecting to:', wsUrl);
        
        socket = new WebSocket(wsUrl);
        
        socket.onopen = () => {
            console.log('Connected!');
            statusEl.textContent = 'Connected!';
            isConnected = true;
            document.querySelector('.status-dot').style.background = '#22c55e';
            document.getElementById('connection-text').textContent = `Connected to ${serverIP}:${serverPort}`;
            
            socket.send('\u0000JOIN:' + userId + ':' + username + '\u0000');
            showEditor();
        };
        
        socket.onmessage = (event) => {
            console.log('Received:', event.data);
            handleMessage(event.data);
        };
        
        socket.onclose = (event) => {
            console.log('Closed:', event.code, event.reason);
            isConnected = false;
            document.querySelector('.status-dot').style.background = '#ef4444';
            document.getElementById('connection-text').textContent = 'Disconnected';
            
            if (event.code !== 1000 && event.code !== -1) {
                statusEl.textContent = 'Connection lost. Refresh to reconnect.';
            }
        };
        
        socket.onerror = (error) => {
            console.error('Error:', error);
            statusEl.textContent = 'Connection failed';
            errorEl.textContent = 'Cannot connect to server. Make sure the server is running.';
            errorEl.style.display = 'block';
        };
    } catch (e) {
        statusEl.textContent = 'Error: ' + e.message;
        errorEl.textContent = e.message;
        errorEl.style.display = 'block';
    }
}

function handleMessage(message) {
    if (message.startsWith('PONG')) return;
    
    if (message.startsWith('JOIN_OK:')) {
        const parts = message.split(':');
        if (parts.length >= 2) {
            lastContent = parts[0];
            version = parseInt(parts[1]);
            const editor = document.getElementById('code-editor');
            if (editor) editor.value = lastContent;
            updateVersion();
            updateCharCount();
        }
    } else if (message.startsWith('EDIT_OK:')) {
        const parts = message.split(':');
        if (parts.length >= 3) {
            const content = parts[0];
            const newVersion = parseInt(parts[1]);
            
            if (content !== lastContent) {
                lastContent = content;
                version = newVersion;
                const editor = document.getElementById('code-editor');
                if (editor) editor.value = content;
                updateVersion();
                updateCharCount();
            }
        }
    }
}

function updateVersion() {
    const el = document.getElementById('version-info');
    if (el) el.textContent = `Version: ${version}`;
}

function updateCharCount() {
    const el = document.getElementById('char-count');
    if (el) el.textContent = `Characters: ${lastContent.length}`;
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
    username = null;
    lastContent = '';
    version = 0;
    isConnected = false;
    showJoin();
}

window.addEventListener('load', () => {
    showJoin();
});

setInterval(() => {
    if (socket && socket.readyState === WebSocket.OPEN) {
        socket.send('\u0000PING\u0000');
    }
}, 10000);
