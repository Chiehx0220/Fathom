// AiSList: labels and notices for flagged channels; matched by the phone.
(() => {
const h = FT.h;

// channel url → 'warn' | 'block' | null
const verdicts = FT.phoneLookup(async (entries) => {
    const body = await FT.api.aiList({ channels: entries.map(([channel]) => channel) });
    return { active: body.block || body.warn, answers: body.verdicts };
});

const verdict = (channel) => (channel ? verdicts.get(channel) : Promise.resolve(null));

FT.aiList = {
    card(el) {
        const meta = el.item && el.item.channelId && FT.$('.card-meta', el);
        if (!meta) return;
        verdict(el.item.channelId).then((kind) => {
            if (kind && !FT.$('.ai-tag', meta)) meta.prepend(h('span', { class: 'ai-tag' }, FT.t('ai_suspected')));
        });
    },
    // Notice on a flagged channel's page, with the way to allow it.
    notice(channel) {
        const box = h('div', { class: 'ai-note', hidden: true });
        verdict(channel.id).then((kind) => {
            if (!kind) return;
            const allow = FT.button(FT.t('ai_allow'), 'check', () => FT.api.aiList({ allow: channel.id, name: channel.name }).then(() => {
                verdicts.reset();
                box.hidden = true;
                FT.toast(FT.t('ai_allowed', { name: channel.name }));
            }).catch(() => FT.toast(FT.t('could_not_save'))), '', 'ai-allow');
            box.replaceChildren(FT.icon('info'), h('span', {}, FT.t(kind === 'block' ? 'ai_channel_blocked' : 'ai_channel_warned')), allow);
            box.hidden = false;
        });
        return box;
    },
    // Switched here or on the phone: forget answers; what is on screen stays.
    reset: verdicts.reset,
};

FT.events.on('settings', () => FT.aiList.reset());
})();
