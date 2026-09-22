// Small helpers shared by every page: a toast, fetching a fragment, and the query string that describes a video.

function showToast(message) {
    let toast = document.getElementById('app-toast');
    if (!toast) {
        toast = document.createElement('div');
        toast.id = 'app-toast';
        toast.className = 'toast';
        document.body.appendChild(toast);
    }
    toast.textContent = message;
    toast.classList.add('show');
    clearTimeout(toast._timer);
    toast._timer = setTimeout(() => toast.classList.remove('show'), 2200);
}

// The one place fragments are fetched: a plain fetch().then(text) would hand a server error page to onSuccess as if it were content.
function fetchText(url, onSuccess, onError) {
    fetch(url)
        .then((res) => {
            if (!res.ok) throw new Error('HTTP ' + res.status);
            return res.text();
        })
        .then(onSuccess)
        .catch(onError);
}

// What the server needs to know about a video to save it, like it, or add it to a list.
function sharedVideoParamsQs(url, title, uploader, thumbnail, uploaderUrl) {
    return '&url=' + encodeURIComponent(url) + '&title=' + encodeURIComponent(title) +
        '&uploader=' + encodeURIComponent(uploader) + '&thumbnail=' + encodeURIComponent(thumbnail) +
        '&uploaderUrl=' + encodeURIComponent(uploaderUrl);
}

// An avatar whose picture fails to load falls back to its coloured initial (error events do not bubble, hence the capture).
document.addEventListener('error', (e) => {
    const image = e.target;
    if (image && image.tagName === 'IMG') {
        const avatar = image.closest('.avatar');
        if (avatar) avatar.classList.add('avatar-failed');
    }
}, true);
