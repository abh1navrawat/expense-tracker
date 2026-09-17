// --- FINTRACK AI FINANCIAL ADVISOR WIDGET ---
document.addEventListener('DOMContentLoaded', () => {
    const triggerBtn = document.getElementById('aiChatTriggerBtn');
    const chatPanel = document.getElementById('aiChatPanel');
    const closeBtn = document.getElementById('aiChatCloseBtn');
    const chatForm = document.getElementById('aiChatForm');
    const chatInput = document.getElementById('aiChatInput');
    const chatBody = document.getElementById('aiChatBody');

    if (!triggerBtn || !chatPanel) return;

    // Toggle Chat Drawer Panel
    triggerBtn.addEventListener('click', () => {
        chatPanel.classList.toggle('active');
        if (chatPanel.classList.contains('active')) {
            chatInput.focus();
        }
    });

    if (closeBtn) {
        closeBtn.addEventListener('click', () => {
            chatPanel.classList.remove('active');
        });
    }

    // Process Message Submission
    if (chatForm) {
        chatForm.addEventListener('submit', (e) => {
            e.preventDefault();
            const message = chatInput.value.trim();
            if (!message) return;

            appendUserMessage(message);
            chatInput.value = '';
            dispatchAiQuery(message);
        });
    }
});

// Helper: Append User Message Bubble
function appendUserMessage(text) {
    const chatBody = document.getElementById('aiChatBody');
    if (!chatBody) return;

    const msgDiv = document.createElement('div');
    msgDiv.className = 'ai-msg ai-msg-user';
    msgDiv.innerHTML = `
        <div class="ai-bubble user-bubble">${escapeHtml(text)}</div>
    `;
    chatBody.appendChild(msgDiv);
    chatBody.scrollTop = chatBody.scrollHeight;
}

// Helper: Append AI Message Bubble
function appendAiMessage(htmlContent) {
    const chatBody = document.getElementById('aiChatBody');
    if (!chatBody) return;

    const msgDiv = document.createElement('div');
    msgDiv.className = 'ai-msg ai-msg-bot';
    msgDiv.innerHTML = `
        <div class="ai-avatar">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M12 2v20M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"/>
            </svg>
        </div>
        <div class="ai-bubble bot-bubble">${htmlContent}</div>
    `;
    chatBody.appendChild(msgDiv);
    chatBody.scrollTop = chatBody.scrollHeight;
}

// Helper: Append Typing Indicator
function appendTypingIndicator() {
    const chatBody = document.getElementById('aiChatBody');
    if (!chatBody) return null;

    const indicator = document.createElement('div');
    indicator.className = 'ai-msg ai-msg-bot typing-indicator-msg';
    indicator.id = 'aiTypingIndicator';
    indicator.innerHTML = `
        <div class="ai-avatar">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M12 2v20M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"/>
            </svg>
        </div>
        <div class="ai-bubble bot-bubble typing-dots">
            <span>Analyzing transactions...</span>
            <span class="dot">.</span><span class="dot">.</span><span class="dot">.</span>
        </div>
    `;
    chatBody.appendChild(indicator);
    chatBody.scrollTop = chatBody.scrollHeight;
    return indicator;
}

// Dispatch Query to Backend API
function dispatchAiQuery(userText) {
    const indicator = appendTypingIndicator();

    fetch('/api/ai/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message: userText })
    })
    .then(res => {
        if (res.status === 401) {
            window.location.href = '/login';
            return;
        }
        if (!res.ok) {
            throw new Error(`HTTP error ${res.status}`);
        }
        return res.json();
    })
    .then(data => {
        if (indicator) indicator.remove();
        if (!data || !data.response) {
            appendAiMessage('Sorry, I could not analyze your spending context at this moment.');
            return;
        }

        const formatted = formatMarkdownText(data.response);
        appendAiMessage(formatted);
    })
    .catch(err => {
        console.error('AI Chatbot query error:', err);
        if (indicator) indicator.remove();
        appendAiMessage('An unexpected error occurred while analyzing your metrics.');
    });
}

// Quick Prompt Click Trigger
function sendQuickPrompt(promptText) {
    const chatPanel = document.getElementById('aiChatPanel');
    if (chatPanel && !chatPanel.classList.contains('active')) {
        chatPanel.classList.add('active');
    }

    appendUserMessage(promptText);
    dispatchAiQuery(promptText);
}

// Simple Markdown to HTML Formatter
function formatMarkdownText(text) {
    let str = escapeHtml(text);

    // Bold text **text**
    str = str.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');

    // Italic text *text*
    str = str.replace(/\*(.*?)\*/g, '<em>$1</em>');

    // Bullet points (lines starting with • or - )
    const lines = str.split('\n');
    let formattedLines = lines.map(line => {
        let trimmed = line.trim();
        if (trimmed.startsWith('•') || trimmed.startsWith('-')) {
            return `<li class="ai-list-item">${trimmed.substring(1).trim()}</li>`;
        }
        return line;
    });

    let result = formattedLines.join('<br>');
    result = result.replace(/(<li class="ai-list-item">.*?<\/li><br>)+/g, match => {
        return `<ul class="ai-list">${match.replace(/<br>/g, '')}</ul>`;
    });

    return result;
}

// Utility: HTML Escaper
function escapeHtml(unsafe) {
    return unsafe
         .replace(/&/g, "&amp;")
         .replace(/</g, "&lt;")
         .replace(/>/g, "&gt;")
         .replace(/"/g, "&quot;")
         .replace(/'/g, "&#039;");
}
