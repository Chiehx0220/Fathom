// Starts the app once everything above has been defined.
(() => {
// The words of the page's own markup (index.html): data-t-label is an aria-label.
FT.$$('[data-t-label]').forEach((el) => el.setAttribute('aria-label', FT.t(el.dataset.tLabel)));
FT.remote.resume();
FT.start();
})();
