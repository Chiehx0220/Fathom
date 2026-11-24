// Playlist picker: save video; field creates new.
(() => {
const h = FT.h;

const choiceRow = (choice, video) => {
    const icon = h('span', {});
    const count = h('span', { class: 'count' });
    const name = h('span', { class: 'grow' }, choice.ghost ? FT.ghost.line() : choice.name);
    const button = h('button', { type: 'button', class: 'menu-item', data: { f: '', key: 'pl:' + choice.id } }, icon, name, count);
    const paint = () => {
        icon.replaceChildren(FT.icon(choice.contains ? 'check' : 'playlist_play'));
        count.textContent = String(choice.videoCount);
        button.classList.toggle('on', choice.contains);
    };
    // Optimistic; reverted on failure.
    const set = (on) => {
        if (on !== choice.contains) choice.videoCount += on ? 1 : -1;
        choice.contains = on;
        paint();
    };
    paint();
    if (choice.ghost) { count.textContent = ''; return FT.ghost.quiet(button); }
    button.onclick = () => {
        const on = !choice.contains;
        set(on);
        FT.api.playlistSet(choice.id, video, on)
            .then(() => FT.toast(FT.t(on ? 'saved_to_playlist' : 'removed_from_playlist', { name: choice.name })))
            .catch(() => { set(!on); FT.toast(FT.t('action_failed')); });
    };
    return button;
};

FT.playlistPicker = async (video) => {
    const picker = h('div', { class: 'picker' }, [1, 2, 3].map((id) => choiceRow({ id: 'ghost:' + id, ghost: true, videoCount: 0, contains: false }, video)));
    const input = h('input', { type: 'text', placeholder: FT.t('new_playlist_name'), maxlength: 100, autocomplete: 'off', enterkeyhint: 'done' });
    input.addEventListener('keydown', (e) => {
        const name = input.value.trim();
        if (e.key !== 'Enter' || !name) return;
        e.preventDefault();
        FT.api.playlistCreate(name, video)
            .then(() => { FT.menu.close(); FT.toast(FT.t('playlist_created', { name })); })
            .catch(() => FT.toast(FT.t('could_not_save')));
    });
    const create = h('div', { class: 'field picker-new', data: { f: '', key: 'playlist-new' }, onclick: () => input.focus() }, FT.icon('add'), input);
    FT.menu.panel(FT.t('save_to_playlist'), h('div', {}, picker, create));
    try {
        const { playlists } = await FT.api.playlists(video.url);
        if (picker.isConnected) picker.replaceChildren(...playlists.map((c) => choiceRow(c, video)));
    } catch (e) {
        if (picker.isConnected) FT.menu.close();
        FT.toast(FT.t('could_not_load_playlists'));
    }
};
})();
