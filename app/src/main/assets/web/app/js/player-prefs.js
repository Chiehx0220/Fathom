// Volume, mute, speed and autoplay survive visits here; Video.js keeps nothing.
(() => {
const KEY = 'fathom-player';

const load = () => { try { return JSON.parse(localStorage.getItem(KEY)) || {}; } catch (e) { return {}; } };
const store = (changes) => { try { localStorage.setItem(KEY, JSON.stringify({ ...load(), ...changes })); } catch (e) {} };

FT.playerCore.prefs = {
    save: (media) => store({ volume: media.volume, muted: media.muted, rate: media.playbackRate }),
    restore(media) {
        const prefs = load();
        if (typeof prefs.volume === 'number') media.volume = prefs.volume;
        if (typeof prefs.muted === 'boolean') media.muted = prefs.muted;
        if (typeof prefs.rate === 'number') media.playbackRate = prefs.rate;
    },
    autoplay: () => load().autoplay !== false,
    setAutoplay: (on) => store({ autoplay: on }),
};
})();
