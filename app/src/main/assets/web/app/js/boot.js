// Boot after all modules are defined.
(() => {
// Static markup strings: data-t-label -> aria-label.
FT.$$('[data-t-label]').forEach((el) => el.setAttribute('aria-label', FT.t(el.dataset.tLabel)));
FT.remote.resume();
FT.start();
FT.deepFlow.watch();
})();
