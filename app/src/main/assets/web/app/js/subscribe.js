// Subscribe button (when subscribed, a bell toggles notifications) and the channel's block menu.
(() => {
const h = FT.h;
const SVG = 'http://www.w3.org/2000/svg';
// Native Rounded.NotificationsActive / NotificationsOff paths; subset font lacks muted bell.
const RINGING = 'M18 16v-5c0-3.07-1.64-5.64-4.5-6.32L13.5 4c0-.83-.68-1.5-1.51-1.5S10.5 3.17 10.5 4v.68C7.63 5.36 6 7.92 6 11v5l-1.3 1.29c-.63.63-.19 1.71.7 1.71h13.17c.89 0 1.34-1.08.71-1.71L18 16zM11.99 22c1.1 0 2-.9 2-2h-4c0 1.1.89 2 2 2zM6.77 4.73c.42-.38.43-1.03.03-1.43-.38-.38-1-.39-1.39-.02C3.7 4.84 2.52 6.96 2.14 9.34c-.09.61.38 1.16 1 1.16.48 0 .9-.35.98-.83.3-1.94 1.26-3.67 2.65-4.94zM18.6 3.28c-.4-.37-1.02-.36-1.4.02-.4.4-.38 1.04.03 1.42 1.38 1.27 2.35 3 2.65 4.94.07.48.49.83.98.83.61 0 1.09-.55.99-1.16-.38-2.37-1.55-4.48-3.25-6.05z';
const MUTED = 'M12 22c1.1 0 2-.9 2-2h-4c0 1.1.89 2 2 2zM18 11c0-3.07-1.64-5.64-4.5-6.32L13.5 4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68c-.24.06-.47.15-.69.23L18 13.1V11zM5.41 3.35L4 4.76l2.81 2.81C6.29 8.57 6 9.73 6 11v5l-1.29 1.29c-.63.63-.19 1.71.7 1.71h12.83l1.74 1.74 1.41-1.41L5.41 3.35z';
const bellIcon = (ringing) => {
    const svg = document.createElementNS(SVG, 'svg');
    svg.setAttribute('viewBox', '0 0 24 24');
    svg.setAttribute('class', 'i');
    svg.setAttribute('aria-hidden', 'true');
    const path = document.createElementNS(SVG, 'path');
    path.setAttribute('d', ringing ? RINGING : MUTED);
    path.setAttribute('fill', 'currentColor');
    svg.append(path);
    return svg;
};
FT.subscribeButton = (channel) => {
    const st = { subscribed: false, notify: false };
    const guard = FT.editGuard();
    const button = FT.button(FT.t('subscribe'), null, null, 'primary', 'subscribe');
    const bell = FT.button('', null, null, 'round notify-bell', 'notify-bell');
    const group = h('span', { class: 'subscribe' }, button);
    const paint = () => {
        const label = FT.t(st.notify ? 'notify_off' : 'notify_on');
        button.replaceChildren(FT.t(st.subscribed ? 'subscribed' : 'subscribe'));
        button.classList.toggle('primary', !st.subscribed);
        button.classList.toggle('quiet', st.subscribed);
        bell.replaceChildren(bellIcon(st.notify));
        bell.classList.toggle('on', st.notify);
        bell.setAttribute('aria-label', label);
        bell.setAttribute('aria-pressed', String(st.notify));
        bell.title = label;
        group.classList.toggle('joined', st.subscribed);
        group.replaceChildren(...(st.subscribed ? [bell, button] : [button]));
    };
    const change = (next, request) => {
        const was = { ...st };
        Object.assign(st, next);
        paint();
        guard.run(request()).catch(() => { Object.assign(st, was); paint(); FT.toast(FT.t('action_failed')); });
    };
    button.onclick = () => {
        if (!channel.id) return;
        if (st.subscribed) change({ subscribed: false, notify: false }, () => FT.api.subscribe(channel, false));
        else change({ subscribed: true, notify: false }, () => FT.api.subscribe(channel, true));
    };
    bell.onclick = () => {
        const on = !st.notify;
        change({ notify: on }, () => FT.api.notify(channel, on));
    };
    paint();
    // `since`: read start; stale reads dropped.
    return {
        el: group,
        button,
        set: (next, since) => {
            if (guard.stale(since)) return;
            st.subscribed = !!next.subscribed;
            st.notify = !!next.notify;
            paint();
        },
    };
};

// Block/unblock; a block offers undo; `done(blocked)` follows each change.
FT.setBlocked = (channel, on, done) => FT.api.block(channel.id, on, channel.name).then(() => {
    if (done) done(on);
    const undo = { label: FT.t('undo'), run: () => FT.api.block(channel.id, false).then(() => { if (done) done(false); }).catch(() => FT.toast(FT.t('could_not_save'))) };
    FT.toast(on ? FT.t('channel_blocked', { name: channel.name }) : FT.t('unblocked'), on ? undo : undefined);
});

// Overflow button by Subscribe; `set(state, since)` takes `blocked`.
FT.channelMenu = (channel) => {
    const guard = FT.editGuard();
    let blocked = false;
    const button = FT.button('', 'more_vert', null, 'round quiet', 'channel-menu');
    button.setAttribute('aria-label', FT.t('more'));
    button.title = FT.t('more');
    button.onclick = () => FT.menu.open([{
        icon: 'block', label: FT.t(blocked ? 'unblock_channel' : 'block_channel'), danger: !blocked,
        run: () => guard.run(FT.setBlocked(channel, !blocked, (on) => { blocked = on; })).catch(() => FT.toast(FT.t('could_not_save'))),
    }], { anchor: button });
    return { el: button, set: (state, since) => { if (!guard.stale(since)) blocked = !!state.blocked; } };
};
})();
