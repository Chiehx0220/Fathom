// Keyboard and remote navigation: which element is selected, where each arrow key goes, and the options menu of a card.

// Keyboard-only focus ring; registered before DOMContentLoaded so the first Tab is caught.
document.addEventListener('keydown', (e) => {
    if (e.key === 'Tab' || e.key.indexOf('Arrow') === 0) document.body.classList.add('using-keyboard');
}, true);
document.addEventListener('pointerdown', () => document.body.classList.remove('using-keyboard'), true);
// Blur after a pointer click to clear the sticky mobile highlight (a UA ring CSS cannot remove); keyboard activation is excluded.
document.addEventListener('click', (e) => {
    if (document.body.classList.contains('using-keyboard')) return;
    const el = e.target && e.target.closest && e.target.closest('a, button');
    if (el && typeof el.blur === 'function') el.blur();
}, true);

document.addEventListener('DOMContentLoaded', () => {
    // Keys (real or from the phone remote) move a highlight between "stops". A video card is one stop, whatever links and buttons it holds inside; the player's own controls are not stops (the player is the state where nothing is highlighted).
    const isShown = (el) => {
        const rect = el.getBoundingClientRect();
        const style = window.getComputedStyle(el);
        return rect.width > 0 && rect.height > 0 && style.display !== 'none' && style.visibility !== 'hidden';
    };

    window.getFocusableElements = () => {
        const selector = '.card, a, button, input, select, textarea, [tabindex="0"]';
        return Array.from(document.querySelectorAll(selector)).filter(el => {
            if (!isShown(el)) return false;
            const card = el.closest('.card');
            if (card && card !== el) return false;
            return !el.closest('media-player');
        });
    };

    const remoteMedia = () => document.getElementById('player') || document.getElementById('audio-player');
    const clearNavFocus = () => document.querySelectorAll('.nav-focus').forEach(old => old.classList.remove('nav-focus'));

    // Coming back to a region (side/bottom menu, top bar, content) lands where you left it, not on whatever is nearest.
    const regionOf = (el) => (el.classList.contains('sidebar-item') || el.classList.contains('bottom-nav-item')) ? 'nav' : el.closest('header') ? 'header' : 'content';
    const lastInRegion = {};
    
    const setNavFocus = (el) => {
        clearNavFocus();
        if (!el) return;
        lastInRegion[regionOf(el)] = el;
        if (el.matches('.card') && !el.hasAttribute('tabindex')) el.setAttribute('tabindex', '0');
        el.classList.add('nav-focus');
        el.focus({ preventScroll: true });
        el.scrollIntoView({ behavior: 'smooth', block: 'center' });
    };

    // Nearest stop in a direction: distance along it, plus a heavier penalty for drifting sideways, so a grid moves cell to cell.
    window.focusDirection = (dir) => {
        const els = window.getFocusableElements();
        if (els.length === 0) return false;
        const active = document.activeElement;
        const current = els.find(el => el === active || el.contains(active));
        const onScreen = (el) => { const r = el.getBoundingClientRect(); return r.bottom > 0 && r.top < window.innerHeight; };
        if (!document.querySelector('.nav-focus')) {
            // First press only shows where the highlight starts.
            const media = remoteMedia();
            const below = media ? media.getBoundingClientRect().bottom : null;
            // The page's own default focus is its first link (the logo), which is not where a viewer wants to start: begin on the first video on screen.
            const start = (below !== null && els.find(el => el.getBoundingClientRect().top >= below - 1))
                || els.find(el => el.matches('.card') && onScreen(el))
                || current
                || els.find(onScreen)
                || els[0];
            setNavFocus(start);
            return true;
        }
        if (!current) return false;
        // Right out of the side menu goes back to where you were in the content, not to whatever the top bar has nearby.
        if (dir === 'right' && regionOf(current) === 'nav') {
            const back = els.includes(lastInRegion.content) ? lastInRegion.content : els.find(el => el.matches('.card') && onScreen(el));
            if (back) {
                setNavFocus(back);
                return true;
            }
        }
        const a = current.getBoundingClientRect();
        const ax = a.left + a.width / 2, ay = a.top + a.height / 2;
        const fromRegion = regionOf(current);
        let best = null, bestScore = Infinity;
        els.forEach(el => {
            if (el === current) return;
            const toRegion = regionOf(el);
            // The side/bottom menu is entered only by pressing Left (or, on a phone-width page, Down from the last row); Up/Down/Right never wander into it.
            if (toRegion === 'nav' && fromRegion !== 'nav' && dir !== 'left' && !(dir === 'down' && el.classList.contains('bottom-nav-item'))) return;
        // And it is left only by Right (handled above) or Up into the top bar: Left/Down at its edge do nothing.
        if (fromRegion === 'nav' && toRegion !== 'nav' && dir !== 'up') return;
            const r = el.getBoundingClientRect();
            const dx = r.left + r.width / 2 - ax, dy = r.top + r.height / 2 - ay;
            const main = dir === 'right' ? dx : dir === 'left' ? -dx : dir === 'down' ? dy : -dy;
            const cross = (dir === 'left' || dir === 'right') ? Math.abs(dy) : Math.abs(dx);
            // Up/Down need a real vertical gap, so items sharing one row (the top bar) do not count as above or below each other.
            if (main <= ((dir === 'up' || dir === 'down') ? 12 : 4)) return;
            // Staying in the same region wins over a slightly nearer stop in another one.
            const score = main + cross * 2 + (toRegion === fromRegion ? 0 : 400);
            if (score < bestScore) { bestScore = score; best = el; }
        });
        if (!best) return false;
        if (regionOf(best) !== regionOf(current)) {
            const remembered = lastInRegion[regionOf(best)];
            if (remembered && els.includes(remembered)) best = remembered;
        }
        setNavFocus(best);
        return true;
    };

    // The options menu of the selected card: what the card itself offers, as a list the direction keys and OK drive.
    const remoteMenu = (() => {
        let panel = null;
        let actions = [];
        let index = 0;

        const cardInfo = (card) => {
            const link = card.querySelector('a.card-title') || card.querySelector('a[href]');
            if (!link) return null;
            const target = new URL(link.href, location.href);
            const uploader = card.querySelector('a.card-uploader');
            const uploaderTarget = uploader ? new URL(uploader.href, location.href) : null;
            const thumb = card.querySelector('img.card-thumb');
            return {
                href: link.href,
                url: target.searchParams.get('id') || '',
                serviceId: target.searchParams.get('serviceId') || '0',
                isVideo: target.pathname === '/watch',
                title: link.textContent.trim(),
                uploader: uploader ? uploader.textContent.trim() : '',
                uploaderHref: uploader ? uploader.href : '',
                uploaderUrl: uploaderTarget ? (uploaderTarget.searchParams.get('id') || '') : '',
                thumbnail: thumb ? thumb.src : '',
                deleteButton: card.querySelector('.card-remove')
            };
        };

        const close = () => {
            if (panel) panel.remove();
            panel = null;
            actions = [];
        };

        const highlight = () => actions.forEach((a, i) => a.el.classList.toggle('selected', i === index));

        const open = (card) => {
            if (panel) return false;
            const info = cardInfo(card);
            if (!info) return false;
            actions = [{ icon: 'play_arrow', label: 'Open', run: () => { window.location.href = info.href; } }];
            if (info.isVideo) {
                actions.push({ icon: 'schedule', label: 'Save to Watch later', run: () => {
                    fetch('/watch_later_action?action=add' + sharedVideoParamsQs(info.url, info.title, info.uploader, info.thumbnail, info.uploaderUrl) + '&type=video&serviceId=' + info.serviceId + '&back=ajax')
                        .then(() => showToast('Saved to Watch later'))
                        .catch(() => showToast('Could not save'));
                } });
            }
            if (info.uploaderHref) actions.push({ icon: 'person', label: 'Go to channel', run: () => { window.location.href = info.uploaderHref; } });
            if (info.deleteButton) actions.push({ icon: 'delete', label: 'Remove from history', run: () => info.deleteButton.click() });

            panel = document.createElement('div');
            panel.id = 'remote-menu';
            panel.innerHTML = '<div class="remote-menu-scrim"></div><div class="remote-menu-panel" role="menu"></div>';
            panel.querySelector('.remote-menu-scrim').onclick = close;
            const list = panel.querySelector('.remote-menu-panel');
            actions.forEach(a => {
                const button = document.createElement('button');
                button.type = 'button';
                button.className = 'remote-menu-item';
                button.innerHTML = '<span class="material-symbols-rounded"></span><span></span>';
                button.children[0].textContent = a.icon;
                button.children[1].textContent = a.label;
                button.onclick = () => { close(); a.run(); };
                list.appendChild(button);
                a.el = button;
            });
            document.body.appendChild(panel);

            // Beside the card: to its right if there is room, else to its left; kept on screen.
            const r = card.getBoundingClientRect();
            const w = list.offsetWidth, h = list.offsetHeight;
            let left = r.right + 12;
            if (left + w > window.innerWidth - 8) left = Math.max(8, r.left - 12 - w);
            list.style.left = left + 'px';
            list.style.top = Math.max(8, Math.min(r.top, window.innerHeight - h - 8)) + 'px';
            index = 0;
            highlight();
            return true;
        };

        return {
            open: open,
            close: close,
            isOpen: () => !!panel,
            move: (step) => { index = (index + step + actions.length) % actions.length; highlight(); },
            run: () => { const chosen = actions[index]; close(); if (chosen) chosen.run(); }
        };
    })();
    window.remoteMenu = remoteMenu;

    document.addEventListener('pointerdown', clearNavFocus, true);
    // Nothing selected on a watch page means the keys drive the player again.
    window.returnToPlayer = () => {
        clearNavFocus();
        if (document.activeElement && typeof document.activeElement.blur === 'function') document.activeElement.blur();
    };

    const initFocusable = () => {
        document.querySelectorAll('.card').forEach(card => {
            if (!card.hasAttribute('tabindex')) {
                card.setAttribute('tabindex', '0');
            }
        });
    };
    initFocusable();

    // A watch page starts on the player (nothing highlighted); other pages start on their first stop.
    const defaultFocus = () => {
        if (remoteMedia()) return;
        const els = window.getFocusableElements ? window.getFocusableElements() : [];
        const first = els.find(el => el.matches('.card')) || els[0];
        if (first) first.focus({ preventScroll: true });
    };
    setTimeout(defaultFocus, 200);

    document.addEventListener('keydown', (e) => {
        const active = document.activeElement;
        if (active && (active.tagName === 'INPUT' || active.tagName === 'SELECT' || active.tagName === 'TEXTAREA' || active.isContentEditable)) {
            // Typing keeps its keys, except Up/Down on a one-line field, which mean nothing there and must not trap a remote inside the search box.
            const leavesField = active.tagName === 'INPUT' && (e.key === 'ArrowUp' || e.key === 'ArrowDown');
            if (!leavesField) return;
        }
        const media = remoteMedia();
        const onPlayer = !!media && !document.querySelector('.nav-focus');
        if (remoteMenu.isOpen()) {
            e.preventDefault();
            if (e.key === 'ArrowDown') remoteMenu.move(1);
            else if (e.key === 'ArrowUp') remoteMenu.move(-1);
            else if (e.key === 'Enter') remoteMenu.run();
            else if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'ContextMenu' || e.key === 'm') remoteMenu.close();
            return;
        }
        if ((e.key === 'ContextMenu' || e.key === 'm') && !onPlayer) {
            const card = document.querySelector('.card.nav-focus');
            if (card) {
                e.preventDefault();
                remoteMenu.open(card);
            }
            return;
        }
        if (e.key === 'ArrowLeft' || e.key === 'ArrowRight') {
            e.preventDefault();
            const forward = e.key === 'ArrowRight';
            if (onPlayer) {
                if (typeof window.seekVideo === 'function') window.seekVideo(forward ? 10 : -10);
                else media.currentTime = Math.max(0, media.currentTime + (forward ? 10 : -10));
            } else {
                window.focusDirection(forward ? 'right' : 'left');
            }
        } else if (e.key === 'ArrowUp' || e.key === 'ArrowDown') {
            e.preventDefault();
            const moved = window.focusDirection(e.key === 'ArrowDown' ? 'down' : 'up');
            // Nothing above: on a watch page that means back to the player.
            if (!moved && e.key === 'ArrowUp' && media) {
                clearNavFocus();
                if (active && typeof active.blur === 'function') active.blur();
            }
        } else if (e.key === 'Enter') {
            if (onPlayer) {
                e.preventDefault();
                if (media.paused) media.play().catch(() => {}); else media.pause();
            } else if (active) {
                active.click();
                const anchor = active.tagName === 'A' ? active : active.querySelector('a');
                if (anchor && anchor.href) {
                    window.location.href = anchor.href;
                }
            }
        } else if (e.key === 'Backspace' || e.key === 'Escape') {
            if (window.history.length > 1) {
                e.preventDefault();
                window.history.back();
            }
        }
    });
});
