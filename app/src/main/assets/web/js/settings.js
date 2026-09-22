// Preference switches on the settings page, and the audio-only preference.

document.addEventListener('DOMContentLoaded', () => {
    const settingsThemeToggle = document.getElementById('settings-theme-toggle');
    if (settingsThemeToggle) {
        const currentTheme = document.documentElement.getAttribute('data-theme') || 'light';
        settingsThemeToggle.checked = (currentTheme === 'dark');
        settingsThemeToggle.addEventListener('change', function() {
            const newTheme = settingsThemeToggle.checked ? 'dark' : 'light';
            document.documentElement.setAttribute('data-theme', newTheme);
            localStorage.setItem('theme', newTheme);
        });
    }
    
    const settingsAccentSelect = document.getElementById('settings-accent-select');
    if (settingsAccentSelect) {
        const currentAccent = localStorage.getItem('theme-color') || 'system';
        settingsAccentSelect.value = currentAccent;
        settingsAccentSelect.addEventListener('change', function() {
            const newAccent = settingsAccentSelect.value;
            document.documentElement.setAttribute('data-theme-color', newAccent);
            localStorage.setItem('theme-color', newAccent);
        });
    }
    
    const settingsPureBlackToggle = document.getElementById('settings-pureblack-toggle');
    if (settingsPureBlackToggle) {
        settingsPureBlackToggle.checked = (localStorage.getItem('pure-black') === 'true');
        settingsPureBlackToggle.addEventListener('change', function() {
            const isPureBlack = settingsPureBlackToggle.checked;
            document.documentElement.setAttribute('data-pure-black', isPureBlack);
            localStorage.setItem('pure-black', isPureBlack ? 'true' : 'false');
        });
    }
    
    const settingsAudioOnlyToggle = document.getElementById('settings-audio-only-toggle');
    if (settingsAudioOnlyToggle) {
        settingsAudioOnlyToggle.checked = (localStorage.getItem('audio_only_default') === 'true');
        settingsAudioOnlyToggle.addEventListener('change', function() {
            localStorage.setItem('audio_only_default', settingsAudioOnlyToggle.checked ? 'true' : 'false');
        });
    }
    
    const autoSaveSetting = (paramName, paramValue) => {
        fetch('/settings?action=save&format=ajax&' + encodeURIComponent(paramName) + '=' + encodeURIComponent(paramValue))
            .then(() => { if (typeof showToast === 'function') showToast('Preference saved'); })
            .catch(err => console.error(err));
    };
    const settingFeedMode = document.getElementById('setting-home-feed-mode');
    if (settingFeedMode) {
        settingFeedMode.addEventListener('change', function() { autoSaveSetting('home_feed_mode', this.value); });
    }
    const settingHideWatched = document.getElementById('setting-hide-watched');
    if (settingHideWatched) {
        settingHideWatched.addEventListener('change', function() { autoSaveSetting('hide_watched', this.checked ? 'true' : 'false'); });
    }
    const settingHideShorts = document.getElementById('setting-hide-shorts');
    if (settingHideShorts) {
        settingHideShorts.addEventListener('change', function() { autoSaveSetting('hide_shorts', this.checked ? 'true' : 'false'); });
    }
    
    document.addEventListener('click', function(e) {
        const target = e.target.closest('a');
        if (target && target.href) {
            const urlStr = target.href;
            if (urlStr.indexOf('/watch?') !== -1 && urlStr.indexOf('force_video=true') === -1) {
                if (localStorage.getItem('audio_only_default') === 'true') {
                    e.preventDefault();
                    try {
                        const url = new URL(urlStr);
                        url.pathname = '/audio';
                        window.location.href = url.toString();
                    } catch (err) {
                        window.location.href = urlStr.replace('/watch?', '/audio?');
                    }
                }
            }
        }
    });
});
