// The search bar: on phones it is an icon that opens into a field; everywhere it suggests earlier searches.

document.addEventListener('DOMContentLoaded', () => {
    const form = document.querySelector('.search-form');
    const input = document.querySelector('.search-input');
    const button = document.querySelector('.search-btn');
    const bar = document.querySelector('.top-bar');
    const box = document.getElementById('search-suggestions-box');
    if (!form || !input || !button) return;

    if (window.innerWidth <= 768) collapseOnPhones();
    if (box) suggestEarlierSearches();

    // Phone width: the field opens on tap and closes again when it loses focus empty. The history entry it pushes lets Back close it.
    function collapseOnPhones() {
        const close = () => {
            form.classList.remove('search-active');
            if (bar) bar.classList.remove('search-active');
        };
        const closeAndPop = () => {
            close();
            if (window.history.state && window.history.state.searchActive) window.history.back();
        };

        button.addEventListener('click', (e) => {
            if (form.classList.contains('search-active')) return;
            e.preventDefault();
            form.classList.add('search-active');
            if (bar) bar.classList.add('search-active');
            input.focus();
            window.history.pushState({ searchActive: true }, '');
        });

        window.addEventListener('popstate', () => {
            if (!form.classList.contains('search-active')) return;
            close();
            if (box) box.style.display = 'none';
        });

        input.addEventListener('blur', () => {
            setTimeout(() => {
                if (document.activeElement !== input && input.value.trim() === '') closeAndPop();
            }, 250);
        });

        document.addEventListener('click', (e) => {
            if (!e.composedPath().includes(form) && form.classList.contains('search-active') && input.value.trim() === '') closeAndPop();
        });
    }

    // The search history is small (the server keeps ten) and only changes when a search is submitted or a suggestion removed, so it is fetched once
    // and filtered locally while the viewer types.
    function suggestEarlierSearches() {
        let cachedHistory = null;

        const load = () => {
            if (cachedHistory !== null) {
                render(cachedHistory);
                return;
            }
            fetch('/search-history')
                .then((res) => res.json())
                .then((data) => {
                    cachedHistory = data || [];
                    render(cachedHistory);
                });
        };

        input.addEventListener('focus', load);
        input.addEventListener('input', load);
        input.addEventListener('click', load);
        // The path, not the target: a row removed by its own click is already gone from the page when this runs.
        document.addEventListener('click', (e) => {
            if (!e.composedPath().includes(form)) box.style.display = 'none';
        });

        function render(history) {
            if (!history || history.length === 0) {
                box.style.display = 'none';
                return;
            }
            const typed = input.value.toLowerCase().trim();
            const matches = typed ? history.filter((q) => q.toLowerCase().includes(typed)) : history;
            if (matches.length === 0) {
                box.style.display = 'none';
                return;
            }
            box.innerHTML = '';
            matches.forEach((q) => box.appendChild(suggestionRow(q)));
            box.style.display = 'flex';
        }

        function icon(name) {
            const glyph = document.createElement('span');
            glyph.className = 'material-symbols-rounded';
            glyph.textContent = name;
            return glyph;
        }

        // Two real buttons per row (search again / forget), so keys and a remote reach them like any other control.
        function suggestionRow(q) {
            const row = document.createElement('div');
            row.className = 'search-suggestion-item';

            const pick = document.createElement('button');
            pick.type = 'button';
            pick.className = 'search-suggestion-text';
            const label = document.createElement('span');
            label.textContent = q;
            pick.append(icon('history'), label);
            pick.addEventListener('click', () => {
                input.value = q;
                form.submit();
            });

            const remove = document.createElement('button');
            remove.type = 'button';
            remove.className = 'search-suggestion-delete';
            remove.setAttribute('aria-label', 'Remove from search history');
            remove.append(icon('close'));
            remove.addEventListener('click', () => {
                fetch('/search-history?delete=' + encodeURIComponent(q));
                cachedHistory = cachedHistory ? cachedHistory.filter((item) => item !== q) : null;
                render(cachedHistory);
                input.focus();
            });

            // A press on either button must not take the focus off the field, or the phone layout would fold the bar away first.
            row.addEventListener('mousedown', (e) => e.preventDefault());
            row.append(pick, remove);
            return row;
        }
    }
});
