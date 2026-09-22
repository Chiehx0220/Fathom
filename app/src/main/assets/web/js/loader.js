// A region marked `data-fill="/url"` fills itself from that fragment once the page is up, showing its spinner until then.
// This is how a page appears at once and its slow parts (the home feed, the subscription feed) arrive afterwards.

function fillFromServer(region) {
    if (region.dataset.filling) return;
    region.dataset.filling = '1';
    fetchText(region.dataset.fill,
        (html) => {
            region.innerHTML = html;
            hydrateRegions(region);
            // Lets a page's own script (the player) start now that its content is in place.
            region.dispatchEvent(new CustomEvent('fathom:content', { bubbles: true }));
        },
        (error) => {
            const notice = document.createElement('div');
            notice.className = 'notice notice-error';
            notice.textContent = 'Failed to load: ' + error.message;
            region.replaceChildren(notice);
        });
}

// Also called after a page is injected into the current one, which the load event would not cover.
function hydrateRegions(root) {
    (root || document).querySelectorAll('[data-fill]').forEach(fillFromServer);
}

document.addEventListener('DOMContentLoaded', () => hydrateRegions(document));
