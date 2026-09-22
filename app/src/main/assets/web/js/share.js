// The share dialog: a link to copy, and the usual places to send it.

const SHARE_TARGETS = [
    {
        id: 'whatsapp', label: 'WhatsApp', size: 22,
        href: (url, title) => 'https://api.whatsapp.com/send?text=' + title + '%20' + url,
        path: 'M12.04 2c-5.46 0-9.91 4.45-9.91 9.91 0 1.75.46 3.45 1.32 4.95L2.05 22l5.25-1.38c1.45.79 3.08 1.21 4.74 1.21 5.46 0 9.91-4.45 9.91-9.91 0-2.65-1.03-5.14-2.9-7.01A9.84 9.84 0 0012.04 2zm.01 1.67c4.55 0 8.24 3.69 8.24 8.24 0 2.2-.86 4.27-2.42 5.82a8.19 8.19 0 01-5.82 2.42c-1.47 0-2.91-.39-4.17-1.14l-.3-.18-3.1 1.18 1.18-3.04-.19-.31A8.2 8.2 0 013.8 11.91c0-4.55 3.69-8.24 8.24-8.24z',
    },
    {
        id: 'telegram', label: 'Telegram', size: 22,
        href: (url, title) => 'https://t.me/share/url?url=' + url + '&text=' + title,
        path: 'M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm4.64 6.8c-.15 1.58-.8 5.42-1.13 7.19-.14.75-.42 1-.68 1.03-.58.05-1.02-.38-1.58-.75-.88-.58-1.38-.94-2.23-1.5-.99-.65-.35-1.01.22-1.59.15-.15 2.71-2.48 2.76-2.69.01-.03.01-.14-.07-.2-.08-.06-.19-.04-.27-.02-.12.02-1.96 1.25-5.54 3.67-.52.36-1 .53-1.42.52-.47-.01-1.37-.26-2.03-.48-.82-.27-1.47-.42-1.42-.88.03-.25.38-.51 1.07-.78 4.18-1.82 6.97-3.02 8.37-3.6 3.98-1.65 4.81-1.94 5.35-1.95.12 0 .38.03.55.17.14.12.18.28.2.46-.01.07.01.25 0 .37z',
    },
    {
        id: 'x', label: 'X', size: 20,
        href: (url, title) => 'https://twitter.com/intent/tweet?text=' + title + '&url=' + url,
        path: 'M18.244 2.25h3.308l-7.227 8.26 8.502 11.24H16.17l-5.214-6.817L4.99 21.75H1.68l7.73-8.835L1.254 2.25H8.08l4.713 6.231zm-1.161 17.52h1.833L7.084 4.126H5.117z',
    },
    {
        id: 'email', label: 'Email', size: 22, external: false,
        href: (url, title) => 'mailto:?subject=' + title + '&body=' + url,
        path: 'M20 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 4l-8 5-8-5V6l8 5 8-5v2z',
    },
];

function buildShareDialog() {
    const overlay = document.createElement('div');
    overlay.id = 'share-modal-overlay';
    overlay.className = 'share-overlay';
    const targets = SHARE_TARGETS.map((t) =>
        '<a class="share-item share-' + t.id + '" data-target="' + t.id + '"' + (t.external === false ? '' : ' target="_blank" rel="noopener"') + '>' +
        '<span class="share-icon"><svg viewBox="0 0 24 24" fill="currentColor" width="' + t.size + '" height="' + t.size + '"><path d="' + t.path + '"/></svg></span>' +
        '<span>' + t.label + '</span></a>').join('');
    overlay.innerHTML =
        '<div class="share-card" role="dialog" aria-label="Share">' +
        '<div class="share-header"><span class="share-title">Share</span>' +
        '<button type="button" class="icon-btn" data-action="share-close" aria-label="Close"><span class="material-symbols-rounded">close</span></button></div>' +
        '<div class="share-link"><input type="text" id="share-input-url" class="share-input" readonly>' +
        '<button type="button" id="share-copy-btn" class="btn btn-filled" data-action="share-copy">Copy</button></div>' +
        '<div class="share-grid">' + targets + '</div></div>';
    overlay.addEventListener('click', (e) => { if (e.target === overlay) closeShareModal(); });
    // Inside the app's web view, external links go to the system through the app.
    overlay.querySelectorAll('.share-item').forEach((link) => {
        link.addEventListener('click', (e) => {
            if (window.NewPipeApp && window.NewPipeApp.openExternalUrl) {
                e.preventDefault();
                window.NewPipeApp.openExternalUrl(link.href);
            }
        });
    });
    document.body.appendChild(overlay);
    return overlay;
}

function openShareModal(url, title) {
    const fullUrl = (url && url.startsWith('http')) ? url : ('https://www.youtube.com/watch?v=' + (url || ''));
    const overlay = document.getElementById('share-modal-overlay') || buildShareDialog();
    overlay.querySelector('#share-input-url').value = fullUrl;
    overlay.querySelector('#share-copy-btn').textContent = 'Copy';
    const encodedUrl = encodeURIComponent(fullUrl);
    const encodedTitle = encodeURIComponent(title || 'Fathom Video');
    SHARE_TARGETS.forEach((t) => {
        overlay.querySelector('[data-target="' + t.id + '"]').href = t.href(encodedUrl, encodedTitle);
    });
    overlay.classList.add('active');
}

function closeShareModal() {
    const overlay = document.getElementById('share-modal-overlay');
    if (overlay) overlay.classList.remove('active');
}

function copyShareLink() {
    const input = document.getElementById('share-input-url');
    if (!input || !input.value) return;
    const done = () => {
        document.getElementById('share-copy-btn').textContent = 'Copied!';
        showToast('Link copied to clipboard');
    };
    const fallback = () => {
        input.select();
        document.execCommand('copy');
        done();
    };
    if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(input.value).then(done).catch(fallback);
    } else {
        fallback();
    }
}

// The watch page still calls this by its old name.
function shareLink(url, title) {
    openShareModal(url, title);
}
