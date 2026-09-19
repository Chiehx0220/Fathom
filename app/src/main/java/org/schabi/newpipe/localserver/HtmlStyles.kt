package org.schabi.newpipe.localserver

/**
 * Global CSS stylesheet, split out of [HtmlRenderer] purely to keep that file's size
 * manageable - this is a static string with a single consumer (`wrapInTemplate`), so moving
 * it here has no behavioural effect.
 */
object HtmlStyles {

    // Global CSS stylesheet for a premium, themeable, responsive user experience
    @JvmField
    val CSS: String =
            "@import url('https://fonts.googleapis.com/css2?family=Roboto:wght@300;400;500;700&display=swap');\n" +
            "@import url('https://fonts.googleapis.com/css2?family=Material+Symbols+Rounded:opsz,wght,FILL,GRAD@24,400,0..1,0&display=block');\n" +
            ":root {\n" +
            "  --bg-color: #fbfafe;\n" +
            "  --text-color: #1d1b20;\n" +
            "  --header-bg: #f3f4f9;\n" +
            "  --header-border: transparent;\n" +
            "  --logo-color: #6750A4;\n" +
            // Fixed brand-mark colors, unlike --logo-color (follows Material You accent) - only
            // light/dark contrast should change.
            "  --logo-badge-bg: #F2EDE0;\n" +
            "  --logo-badge-fg: #0F1D33;\n" +
            "  --logo-badge-accent: #C99A34;\n" +
            "  --search-input-border: transparent;\n" +
            "  --search-input-bg: #ece6f0;\n" +
            "  --search-input-color: #1d1b20;\n" +
            "  --search-btn-bg: #ece6f0;\n" +
            "  --search-btn-hover: #e8def8;\n" +
            "  --service-tab-bg: #ece6f0;\n" +
            "  --service-tab-color: #49454f;\n" +
            "  --service-tab-hover-bg: #e8def8;\n" +
            "  --service-tab-hover-color: #1d1b20;\n" +
            "  --card-bg: #ffffff;\n" +
            "  --card-border: transparent;\n" +
            "  --card-thumbnail-bg: #ece6f0;\n" +
            "  --card-title-color: #1d1b20;\n" +
            "  --card-meta-color: #49454f;\n" +
            "  --media-info-bg: transparent;\n" +
            "  --media-info-border: rgba(0, 0, 0, 0.05);\n" +
            "  --media-title-color: #1d1b20;\n" +
            "  --media-stats-color: #49454f;\n" +
            "  --uploader-name-color: #1d1b20;\n" +
            "  --uploader-subs-color: #49454f;\n" +
            "  --media-desc-color: #1d1b20;\n" +
            "  --media-desc-bg: #f3f4f9;\n" +
            "  --media-desc-border: transparent;\n" +
            "  --comments-bg: transparent;\n" +
            "  --comments-border: rgba(0, 0, 0, 0.05);\n" +
            "  --comment-count-color: #1d1b20;\n" +
            "  --comment-border: rgba(0, 0, 0, 0.03);\n" +
            "  --comment-author-color: #1d1b20;\n" +
            "  --comment-time-color: #49454f;\n" +
            "  --comment-text-color: #1d1b20;\n" +
            "  --channel-header-bg: transparent;\n" +
            "  --channel-header-border: rgba(0, 0, 0, 0.05);\n" +
            "  --channel-name-color: #1d1b20;\n" +
            "  --channel-desc-color: #49454f;\n" +
            "  --bottom-nav-bg: #f3f4f9;\n" +
            "  --bottom-nav-border: transparent;\n" +
            "  --bottom-nav-item-color: #49454f;\n" +
            "  --bottom-nav-item-active-color: #21005d;\n" +
            "  --bottom-nav-active-pill-bg: #e8def8;\n" +
            "  --settings-card-bg: #ffffff;\n" +
            "  --settings-card-border: transparent;\n" +
            "  --settings-title-color: #1d1b20;\n" +
            "  --settings-section-border: rgba(0, 0, 0, 0.05);\n" +
            "  --settings-section-title-color: #6750A4;\n" +
            "  --setting-label-color: #1d1b20;\n" +
            "  --setting-desc-color: #49454f;\n" +
            "  --textarea-label-color: #1d1b20;\n" +
            "  --textarea-border: #79747e;\n" +
            "  --textarea-bg: #ffffff;\n" +
            "  --textarea-color: #1d1b20;\n" +
            "  --slider-bg: #e8def8;\n" +
            "}\n" +
            "[data-theme=\"dark\"] {\n" +
            "  --bg-color: #141218;\n" +
            "  --text-color: #e6e1e5;\n" +
            "  --header-bg: #1d1b20;\n" +
            "  --header-border: transparent;\n" +
            "  --logo-color: #d0bcff;\n" +
            "  --logo-badge-bg: #0F1D33;\n" +
            "  --logo-badge-fg: #F2EDE0;\n" +
            "  --logo-badge-accent: #E8C468;\n" +
            "  --search-input-border: transparent;\n" +
            "  --search-input-bg: #2b2930;\n" +
            "  --search-input-color: #e6e1e5;\n" +
            "  --search-btn-bg: #2b2930;\n" +
            "  --search-btn-hover: #4a4458;\n" +
            "  --service-tab-bg: #2b2930;\n" +
            "  --service-tab-color: #cac4d0;\n" +
            "  --service-tab-hover-bg: #4a4458;\n" +
            "  --service-tab-hover-color: #e8def8;\n" +
            "  --card-bg: #1d1b20;\n" +
            "  --card-border: transparent;\n" +
            "  --card-thumbnail-bg: #2b2930;\n" +
            "  --card-title-color: #e6e1e5;\n" +
            "  --card-meta-color: #cac4d0;\n" +
            "  --media-info-bg: transparent;\n" +
            "  --media-info-border: rgba(255, 255, 255, 0.05);\n" +
            "  --media-title-color: #e6e1e5;\n" +
            "  --media-stats-color: #cac4d0;\n" +
            "  --uploader-name-color: #e6e1e5;\n" +
            "  --uploader-subs-color: #cac4d0;\n" +
            "  --media-desc-color: #e6e1e5;\n" +
            "  --media-desc-bg: #2b2930;\n" +
            "  --media-desc-border: transparent;\n" +
            "  --comments-bg: transparent;\n" +
            "  --comments-border: rgba(255, 255, 255, 0.05);\n" +
            "  --comment-count-color: #e6e1e5;\n" +
            "  --comment-border: rgba(255, 255, 255, 0.03);\n" +
            "  --comment-author-color: #e6e1e5;\n" +
            "  --comment-time-color: #cac4d0;\n" +
            "  --comment-text-color: #e6e1e5;\n" +
            "  --channel-header-bg: transparent;\n" +
            "  --channel-header-border: rgba(255, 255, 255, 0.05);\n" +
            "  --channel-name-color: #e6e1e5;\n" +
            "  --channel-desc-color: #cac4d0;\n" +
            "  --bottom-nav-bg: #1d1b20;\n" +
            "  --bottom-nav-border: transparent;\n" +
            "  --bottom-nav-item-color: #cac4d0;\n" +
            "  --bottom-nav-item-active-color: #e8def8;\n" +
            "  --bottom-nav-active-pill-bg: #4a4458;\n" +
            "  --settings-card-bg: #1d1b20;\n" +
            "  --settings-card-border: transparent;\n" +
            "  --settings-title-color: #e6e1e5;\n" +
            "  --settings-section-border: rgba(255, 255, 255, 0.05);\n" +
            "  --settings-section-title-color: #d0bcff;\n" +
            "  --setting-label-color: #e6e1e5;\n" +
            "  --setting-desc-color: #cac4d0;\n" +
            "  --textarea-label-color: #e6e1e5;\n" +
            "  --textarea-border: #938f99;\n" +
            "  --textarea-bg: #1d1b20;\n" +
            "  --textarea-color: #e6e1e5;\n" +
            "  --slider-bg: #4a4458;\n" +
            "}\n" +
            "* { box-sizing: border-box; margin: 0; padding: 0; }\n" +
            "body { font-family: 'Roboto', sans-serif; background-color: var(--bg-color); color: var(--text-color); -webkit-font-smoothing: antialiased; transition: background-color 0.2s, color 0.2s; overflow-x: hidden; overflow-wrap: break-word; word-wrap: break-word; }\n" +
            "a { color: inherit; text-decoration: none; }\n" +
            "header { display: flex; align-items: center; justify-content: space-between; background: var(--header-bg); padding: 0 16px; position: fixed; top: 0; left: 0; right: 0; height: 56px; z-index: 1000; border-bottom: 1px solid var(--header-border); transition: background 0.2s, border-bottom 0.2s; }\n" +
            ".top-bar { display: flex; align-items: center; justify-content: space-between; width: 100%; height: 100%; gap: 16px; }\n" +
            ".logo { font-size: 20px; font-weight: 700; color: var(--logo-color); display: flex; align-items: center; gap: 4px; letter-spacing: -0.8px; transition: color 0.2s; font-family: 'Roboto', sans-serif; }\n" +
            ".search-form { display: flex; flex-grow: 1; max-width: 640px; position: relative; margin: 0 16px; border-radius: 28px; background-color: var(--search-input-bg); overflow: hidden; height: 40px; align-items: center; padding-left: 8px; }\n" +
            ".search-input { flex-grow: 1; height: 100%; border: none; background: transparent; color: var(--search-input-color); padding: 0 16px; font-size: 16px; outline: none; }\n" +
            ".search-input:focus { border: none; }\n" +
            ".search-btn { height: 40px; width: 48px; border-radius: 24px; border: none; background: transparent; color: var(--text-color); cursor: pointer; display: flex; align-items: center; justify-content: center; margin-right: 4px; }\n" +
            ".search-btn:hover { background-color: var(--search-btn-hover); }\n" +
            ".service-selector { display: none; }\n" +
            ".container { transition: all 0.2s ease; }\n" +
            // Shared "heading + action button" row. Margin lives on the row, not the h2, so the
            // row's edges (not the text baseline) set the page spacing - keeps heading/button level.
            ".page-header-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 20px; }\n" +
            ".page-header-row h2 { margin: 0; }\n" +
            // Watch History select-mode toolbar. flex-wrap drops the two button actions to their
            // own row on narrow phones instead of crushing them.
            ".history-select-bar { align-items: center; justify-content: space-between; gap: 12px; row-gap: 12px; flex-wrap: wrap; margin-bottom: 20px; padding: 12px 16px; background-color: var(--card-bg); border-radius: 12px; }\n" +
            ".history-select-info { display: flex; align-items: center; gap: 12px; }\n" +
            ".history-select-all-label { display: flex; align-items: center; gap: 8px; cursor: pointer; }\n" +
            ".history-select-count { color: var(--card-meta-color); }\n" +
            ".history-select-actions { display: flex; gap: 8px; }\n" +
            // gap folded in from the MD3 pass directly, not left as a later unconditional rule -
            // that was beating the mobile-only "gap: 20px" rule below. Same merge pattern used
            // throughout this file.
            ".grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 28px 20px; }\n" +
            // background-color/border/border-radius/padding: MD3 pass's !important values, folded
            // in directly - no behavior change, just one source of truth.
            ".card { display: flex; flex-direction: column; cursor: pointer; background-color: transparent; border-radius: 0; padding: 0; border: none; transition: transform 0.2s, box-shadow 0.2s; min-width: 0; overflow: visible; word-break: break-word; overflow-wrap: break-word; }\n" +
            ".card:hover { transform: none; box-shadow: none; }\n" +
            ".card-thumbnail { width: 100%; aspect-ratio: 16/9; background-color: var(--card-thumbnail-bg); object-fit: cover; border-radius: 16px; transition: border-radius 0.2s; flex-shrink: 0; max-height: 240px; }\n" +
            ".card-details { display: flex; gap: 12px; padding: 12px 0 0 0; min-width: 0; overflow: hidden; }\n" +
            ".card-avatar { width: 40px; height: 40px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-weight: bold; color: white; font-size: 15px; flex-shrink: 0; aspect-ratio: 1 / 1; object-fit: cover; }\n" +
            // Always visible (not hover-only) so it's reachable on touch, where hover never fires.
            ".card-delete-btn { position: absolute; top: 8px; right: 8px; width: 32px; height: 32px; border-radius: 50%; border: none; background-color: rgba(0,0,0,0.6); color: #ffffff; display: flex; align-items: center; justify-content: center; cursor: pointer; z-index: 2; transition: background-color 0.2s; }\n" +
            ".card-delete-btn:hover { background-color: rgba(0,0,0,0.8); }\n" +
            ".card-delete-btn .material-symbols-rounded { font-size: 18px; }\n" +
            // Selection indicator hidden until "history-select-mode" is on <body>; delete button
            // hides at the same time to avoid competing for the thumbnail's corners.
            //
            // MD3 circular selection badge (Google Photos/Files convention), not a bare checkbox.
            // Real <input> stretched invisibly over the badge (click/keyboard/a11y semantics), with
            // a Material Symbols check glyph shown via :checked sibling selector - Chrome doesn't
            // render ::before/::after on replaced elements like <input>.
            ".card-select-indicator { display: none; position: absolute; top: 8px; left: 8px; width: 24px; height: 24px; z-index: 2; border-radius: 50%; background-color: rgba(0,0,0,0.35); border: 2px solid rgba(255,255,255,0.9); box-shadow: 0 1px 3px rgba(0,0,0,0.3); align-items: center; justify-content: center; transition: background-color 0.15s var(--md-easing), border-color 0.15s var(--md-easing); }\n" +
            "body.history-select-mode .card-select-indicator { display: flex; }\n" +
            "body.history-select-mode .card-delete-btn { display: none; }\n" +
            ".card-select-checkbox { position: absolute; inset: 0; width: 100%; height: 100%; margin: 0; opacity: 0; cursor: pointer; }\n" +
            ".card-select-check-icon { font-size: 16px; color: var(--md-on-primary, #fff); opacity: 0; transition: opacity 0.15s var(--md-easing); pointer-events: none; }\n" +
            ".card-select-checkbox:checked ~ .card-select-check-icon { opacity: 1; }\n" +
            ".card-select-indicator:has(.card-select-checkbox:checked) { background-color: var(--md-primary); border-color: var(--md-primary); }\n" +
            ".card-info { display: flex; flex-direction: column; flex-grow: 1; min-width: 0; overflow: hidden; word-break: break-word; overflow-wrap: break-word; }\n" +
            // font-size/letter-spacing: MD3 type-scale values (title-medium/body-small), folded
            // in directly.
            ".card-title { font-size: 16px; font-weight: 500; letter-spacing: 0.15px; line-height: 1.4; max-height: 2.8em; overflow: hidden; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; margin-bottom: 4px; color: var(--card-title-color); word-break: break-word; overflow-wrap: break-word; min-width: 0; }\n" +
            ".card-meta { font-size: 12px; letter-spacing: 0.4px; color: var(--card-meta-color); display: flex; flex-direction: column; gap: 2px; word-break: break-word; overflow-wrap: break-word; min-width: 0; }\n" +
            ".card-uploader { font-weight: 500; color: var(--card-meta-color); text-decoration: none; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 100%; display: inline-block; }\n" +
            ".card-uploader:hover { color: var(--text-color); }\n" +
            ".pagination { display: flex; justify-content: center; margin: 32px 0; }\n" +
            ".btn-page { display: inline-block; padding: 10px 24px; border-radius: 100px; font-weight: 500; font-size: 14px; background-color: var(--service-tab-bg); color: var(--text-color); border: none; cursor: pointer; transition: background-color 0.2s; }\n" +
            ".btn-page:hover { background-color: var(--service-tab-hover-bg); }\n" +
            ".sidebar-nav { position: fixed; top: 56px; left: 0; bottom: 0; width: 240px; background-color: var(--bg-color); padding: 12px 4px; display: flex; flex-direction: column; gap: 4px; z-index: 99; overflow-y: auto; }\n" +
            // transition/hover background are the MD3 pass's values, folded in directly (that
            // pass had no !important here but came later, so it already won regardless of order).
            ".sidebar-item { display: flex; align-items: center; gap: 24px; padding: 12px 24px; border-radius: 100px; font-size: 14px; font-weight: 500; color: var(--text-color); transition: background-color 0.2s var(--md-easing); cursor: pointer; margin: 0 12px; }\n" +
            ".sidebar-item:hover { background-color: var(--md-state-hover); }\n" +
            ".sidebar-item.active { font-weight: 700; background-color: var(--bottom-nav-active-pill-bg); color: var(--bottom-nav-item-active-color); }\n" +
            ".sidebar-item.active:hover { background-color: var(--bottom-nav-active-pill-bg); }\n" +
            ".sidebar-icon { font-size: 18px; }\n" +
            "@media (min-width: 769px) {\n" +
            "  .bottom-nav { display: none !important; }\n" +
            "  .sidebar-nav { display: flex !important; width: 72px; }\n" +
            "  .sidebar-nav .sidebar-label { display: none; }\n" +
            "  .sidebar-nav .sidebar-item { justify-content: center; padding: 12px; }\n" +
            "  .container { margin-left: 72px; max-width: calc(100% - 72px); padding: 24px 40px; margin-top: 56px; }\n" +
            /* Rail rests at 72px, flies to 240px on hover, fixed/overlaid so container margin
             * never changes - instant, unlike a click-toggle that reflows the page. */
            "  .sidebar-nav:hover { width: 240px; box-shadow: 2px 0 8px rgba(0,0,0,0.24); }\n" +
            "  .sidebar-nav:hover .sidebar-label { display: inline; }\n" +
            "  .sidebar-nav:hover .sidebar-item { justify-content: flex-start; padding: 12px 24px; }\n" +
            // Desktop only - mobile uses .top-bar.search-active's collapse/expand mechanics instead.
            //
            // Not CSS Grid: asymmetric side widths (logo+switcher ~277px vs icon cluster ~90px)
            // break "1fr" track-sizing whenever content-based minimums differ - a track exceeding
            // its fair share freezes at content size and dumps all slack onto the other fr track,
            // so no fr-ratio combination centers pixel-exactly. Instead: search bar out of flow
            // (position:absolute), HtmlScripts.SCRIPTS' centerSearchBar() measures both flanking
            // groups' real widths on load/resize and centers on the bar's own midpoint.
            "  .top-bar { position: relative; }\n" +
            "  .search-form { position: absolute; left: 50%; top: 50%; transform: translate(-50%, -50%); margin: 0 !important; }\n" +
            "}\n" +
            "@media (max-width: 768px) {\n" +
            /* A 375px top bar can't fit the wordmark + YouTube/BiliBili switcher + search pill +
             * cast icon without crushing something (see .search-form fix below) - icon-only logo
             * on mobile frees the ~90px needed. */
            "  .logo-text { display: none; }\n" +
            "  .logo svg { margin-right: 0 !important; }\n" +
            "  .sidebar-nav { display: none !important; }\n" +
            "  .bottom-nav { display: flex !important; position: fixed; bottom: 0; left: 0; right: 0; height: 80px; background: var(--bottom-nav-bg); border-top: none; box-shadow: 0 -1px 3px rgba(0,0,0,0.05); justify-content: space-around; align-items: center; z-index: 1000; padding-bottom: 8px; }\n" +
            "  .container { margin-left: 0; max-width: 100%; padding: 0 12px; margin-top: 56px; padding-bottom: 96px; }\n" +
            // Bottom padding, not just top - was 0 before, so headings sat flush on the grid below.
            "  .container h2 { padding: 16px 4px 16px 4px; margin: 0 !important; }\n" +
            // .page-header-row's h2 still matches the descendant rule above, stacking its own
            // padding on the row's margin - this wins (later, same specificity) and moves all
            // spacing to the row so heading/button stay level.
            "  .page-header-row { padding: 16px 4px 16px 4px; margin-bottom: 16px; }\n" +
            "  .page-header-row h2 { padding: 0 !important; }\n" +
            "  .history-select-actions { flex: 1 1 100%; }\n" +
            "  .history-select-actions .btn-page { flex: 1; }\n" +
            // repeat(auto-fill, minmax(300px,1fr)), not a hardcoded 1 column - auto-fill already
            // degrades to 1 column below ~620px, so tablets/landscape phones in 620-768px keep
            // two columns instead of one oversized card.
            "  .grid { grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 20px; }\n" +
            "  .card-details { padding: 12px 4px; }\n" +
            "  .bottom-nav-item { display: flex; flex-direction: column; align-items: center; gap: 4px; color: var(--bottom-nav-item-color); font-size: 12px; font-weight: 500; text-decoration: none; flex-grow: 1; justify-content: center; }\n" +
            "  .bottom-nav-item .bottom-nav-icon { display: flex; align-items: center; justify-content: center; width: 64px; height: 32px; border-radius: 16px; transition: background-color 0.2s ease, color 0.2s ease; color: var(--bottom-nav-item-color); }\n" +
            "  .bottom-nav-item.active .bottom-nav-icon { background-color: var(--bottom-nav-active-pill-bg); color: var(--bottom-nav-item-active-color); }\n" +
            "  .bottom-nav-item.active { color: var(--text-color); }\n" +
            "  #theme-toggle { display: none !important; }\n" +
            "  #mobile-theme-row, #mobile-history-row { display: flex !important; }\n" +
            /* .search-form's overflow:hidden resolves its flex auto min-width to 0 (spec: auto
             * min-width is 0, not content size, when overflow isn't visible) - on a tight top bar
             * it alone absorbs the shortfall and crushes to 0px. flex-shrink:0 + min-width:40px
             * pin the collapsed pill's size. */
            /* .top-bar's justify-content:space-between treats the collapsed 40px icon as an
             * independent flex item, floating it with equal space on both sides instead of joining
             * the icon cluster. margin-left:auto claims the leftover space instead, leaving only
             * the ordinary 16px gap to cast. */
            "  .search-form { max-width: 40px; min-width: 40px; flex-shrink: 0; margin: 0; margin-left: auto; padding-left: 0; overflow: hidden; transition: max-width 0.3s ease; border-radius: 40px; }\n" +
            /* A 40x48 rounded box is an oval, not a circle - height:40 + transparent background
             * (matching cast/theme buttons at rest) renders a true circle when collapsed. Scoped
             * to :not(.search-active) so the expanded pill is unaffected. */
            "  .search-form:not(.search-active) { height: 40px; background: transparent; }\n" +
            /* Desktop's margin-right:4px still counts toward the button's size inside the pinned
             * 40px form once collapsed, shrinking it to 36px. */
            "  .search-btn { margin-right: 0; }\n" +
            "  .search-form.search-active { max-width: 100%; width: 100%; margin-left: 8px; padding-left: 8px; }\n" +
            "  .search-input { width: 0; padding: 0; border: none; transition: width 0.3s ease, opacity 0.3s ease; opacity: 0; }\n" +
            "  .search-form.search-active .search-input { width: calc(100% - 48px); padding: 0 16px; border: 1px solid var(--search-input-border); opacity: 1; }\n" +
            "  .search-btn { border-radius: 40px; border: none; background: transparent; }\n" +
            "  .search-form.search-active .search-btn { border-radius: 0 40px 40px 0; border: 1px solid var(--search-input-border); border-left: none; background-color: var(--search-btn-bg); }\n" +
            "  .top-bar.search-active div:first-child, .top-bar.search-active div:last-child { display: none !important; }\n" +
            "  .subs-tab { padding: 8px 12px; gap: 6px; font-size: 13px; }\n" +
            "  .subs-tab .material-symbols-rounded { font-size: 18px; }\n" +
            "}\n" +
            ".player-container { display: flex; flex-direction: column; gap: 20px; margin-top: 16px; }\n" +
            ".player-layout { display: flex; flex-direction: column; gap: 24px; }\n" +
            "@media (min-width: 1024px) {\n" +
            "  .player-layout { display: grid; grid-template-columns: 1fr 360px; gap: 24px; }\n" +
            "}\n" +
            // min-width:0 required: as a grid item of .player-layout's "1fr 360px" track,
            // .main-content's auto min-width is its content's min-content size - an unspaced long
            // URL in a description can blow it past its 1fr share into page-wide horizontal scroll.
            // .sidebar gets the same treatment defensively (same grid).
            ".main-content { display: flex; flex-direction: column; gap: 16px; min-width: 0; }\n" +
            ".sidebar { display: flex; flex-direction: column; gap: 16px; min-width: 0; }\n" +
            ".native-player { width: 100%; aspect-ratio: 16/9; border-radius: 12px; background-color: #000; outline: none; }\n" +
            ".media-info { padding: 16px 0; border-bottom: 1px solid var(--media-info-border); min-width: 0; overflow: hidden; }\n" +
            // font-size/font-weight/letter-spacing: MD3 title-large values, folded in directly.
            ".media-title { font-size: 22px; font-weight: 500; letter-spacing: 0px; margin-bottom: 8px; color: var(--media-title-color); line-height: 1.4; word-break: break-word; overflow-wrap: break-word; min-width: 0; }\n" +
            ".media-stats { font-size: 14px; color: var(--media-stats-color); margin-bottom: 12px; }\n" +
            ".uploader-profile { display: flex; flex-direction: column; gap: 12px; margin-bottom: 16px; }\n" +
            "@media (min-width: 768px) {\n" +
            "  .uploader-profile { flex-direction: row; align-items: center; justify-content: space-between; gap: 16px; }\n" +
            "}\n" +
            ".uploader-main { display: flex; align-items: center; gap: 12px; width: 100%; min-width: 0; }\n" +
            "@media (min-width: 768px) {\n" +
            "  .uploader-main { width: auto; }\n" +
            "}\n" +
            ".uploader-avatar { width: 40px; height: 40px; border-radius: 50%; object-fit: cover; flex-shrink: 0; aspect-ratio: 1 / 1; }\n" +
            ".uploader-info { display: flex; flex-direction: column; justify-content: center; flex-grow: 1; min-width: 0; overflow: hidden; word-break: break-word; overflow-wrap: break-word; }\n" +
            ".uploader-name { font-size: 15px; font-weight: 600; color: var(--uploader-name-color, var(--text-color)); text-decoration: none; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 100%; }\n" +
            ".uploader-subs { font-size: 12px; color: var(--uploader-subs-color, #a0a0a0); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 100%; }\n" +
            ".action-buttons-group { display: flex; align-items: center; gap: 8px; overflow-x: auto; padding-bottom: 4px; scrollbar-width: none; width: 100%; }\n" +
            "@media (min-width: 768px) {\n" +
            "  .action-buttons-group { width: auto; }\n" +
            "}\n" +
            ".action-buttons-group::-webkit-scrollbar { display: none; }\n" +
            // background-color/height/color/cursor: MD3 values, folded in directly. .pill-btn's
            // height:40px still comes from the shared ".action-pill-btn, .btn-page, .pill-btn" rule.
            ".like-dislike-pill { display: inline-flex; align-items: center; background-color: var(--md-surface-high); border-radius: 100px; height: 40px; overflow: hidden; flex-shrink: 0; }\n" +
            ".pill-btn { background: none; border: none; padding: 0 14px; height: 100%; color: var(--md-on-surface); font-weight: 500; font-size: 13px; display: flex; align-items: center; gap: 6px; cursor: pointer; white-space: nowrap; }\n" +
            ".pill-divider { width: 1px; height: 18px; background-color: var(--md-outline-variant); }\n" +
            ".pill-btn.active { color: var(--md-primary); }\n" +
            ".action-pill-btn { background-color: var(--service-tab-bg, rgba(255,255,255,0.1)); color: var(--text-color, #fff); height: 36px; line-height: 36px; padding: 0 18px; border-radius: 100px; font-size: 13px; font-weight: 500; display: inline-flex; align-items: center; text-decoration: none; flex-shrink: 0; border: none; cursor: pointer; white-space: nowrap; }\n" +
            ".action-pill-btn.danger { background-color: var(--md-error, #c00c0c); color: var(--md-on-error, #ffffff); }\n" +
            ".action-pill-btn.watch-later-btn.added { background-color: var(--md-primary); color: var(--md-on-primary); }\n" +
            ".action-pill-btn.disabled { opacity: 0.6; cursor: default; pointer-events: none; }\n" +
            // Consolidated from 3 scattered ".settings-card" rules that each overrode a piece of
            // the last - these are the values that won the cascade; no visual change.
            ".settings-card { background-color: var(--settings-card-bg); padding: 24px; border-radius: 16px; border: 1px solid var(--settings-card-border); box-shadow: none; min-width: 0; overflow: hidden; word-break: break-word; overflow-wrap: break-word; max-width: 600px; margin: 0 auto; }\n" +
            // Control-bar accent theming deliberately dropped (reverted after repeated bugs,
            // back in the video.js era) - Vidstack's Default Layout keeps its own default control
            // colors. Fullscreen fit fix and SponsorBlock markers below are kept. Rounding/overflow
            // for the player itself now lives on .player-wrapper (below), since <media-player> IS
            // that wrapper - no separate rule needed here.
            // SponsorBlock markers - JS-created inside <media-time-slider> (Vidstack's own CSS
            // already gives it position:relative). Colors match SponsorBlock's own established
            // category colors.
            ".sponsor-segment-marker { position: absolute; top: 0; height: 100%; pointer-events: none; opacity: 0.85; }\n" +
            ".sponsor-segment-marker.cat-sponsor { background: #00d400; }\n" +
            ".sponsor-segment-marker.cat-intro { background: #00ffff; }\n" +
            ".sponsor-segment-marker.cat-outro { background: #0202ed; }\n" +
            ".sponsor-segment-marker.cat-interaction { background: #cc00ff; }\n" +
            ".sponsor-segment-marker.cat-selfpromo { background: #ffff00; }\n" +
            ".sponsor-segment-marker.cat-poi_highlight { background: #ff1684; }\n" +
            ".sponsor-segment-marker.cat-preview { background: #008fd6; }\n" +
            ".sponsor-segment-marker.cat-music_offtopic { background: #ff9900; }\n" +
            ".sponsor-segment-marker.cat-filler { background: #7300ff; }\n" +
            // Chapter boundary ticks, same <media-time-slider> JS-injection point as SponsorBlock
            // markers above - a thin line rather than a filled range since a chapter is a single
            // instant, not a start/end segment.
            ".chapter-tick-marker { position: absolute; top: 0; width: 2px; height: 100%; background: rgba(255,255,255,0.7); pointer-events: none; z-index: 1; }\n" +
            // Skip button, YouTube skip-ad role. opacity/pointer-events not display:none, so
            // appear/disappear transitions; bottom offset clears both control-bar heights.
            ".sponsor-skip-btn { position: absolute; right: 16px; bottom: 76px; background: rgba(29,27,32,0.9); color: #fff; border: none; padding: 10px 16px; border-radius: 8px; font-size: 14px; font-weight: 600; cursor: pointer; display: inline-flex; align-items: center; gap: 6px; z-index: 10; opacity: 0; pointer-events: none; transform: translateY(8px); transition: opacity 0.2s var(--md-easing), transform 0.2s var(--md-easing), background-color 0.2s; box-shadow: 0 2px 8px rgba(0,0,0,0.3); }\n" +
            ".sponsor-skip-btn:hover { background: rgba(45,42,54,0.95); }\n" +
            ".sponsor-skip-btn.visible { opacity: 1; pointer-events: auto; transform: translateY(0); }\n" +
            // No fullscreen control-bar size overrides here (video.js needed hand-tuned ones) -
            // Vidstack's Default Layout scales its own control bar responsively in fullscreen.
            // Shared native-<select> styling. appearance:none strips the native arrow so a themed
            // Material Symbols chevron overlays it instead of a fixed-color SVG - inherits the
            // dynamic theme like any other icon. Closed state only; the option list is native
            // OS chrome outside CSS's reach.
            ".md-select-wrap { position: relative; display: inline-flex; align-items: center; }\n" +
            ".md-select { appearance: none; -webkit-appearance: none; -moz-appearance: none; padding: 6px 32px 6px 12px; border-radius: 6px; border: 1px solid var(--search-input-border); background-color: var(--card-bg); color: var(--text-color); font-family: inherit; font-size: 13px; cursor: pointer; }\n" +
            ".md-select-arrow { position: absolute; right: 8px; font-size: 18px; color: var(--md-on-surface-variant); pointer-events: none; }\n" +
            ".channel-card-avatar { width: 48px !important; height: 48px !important; min-width: 48px !important; min-height: 48px !important; border-radius: 50% !important; object-fit: cover !important; aspect-ratio: 1 / 1 !important; flex-shrink: 0; font-size: 18px; background-color: var(--service-tab-bg, #2b2930); }\n" +
            "body.pip-mode header, body.pip-mode .sidebar-nav, body.pip-mode .bottom-nav, body.pip-mode .media-info, body.pip-mode .comments-section, body.pip-mode .sidebar { display: none !important; }\n" +
            "body.pip-mode .container { margin: 0 !important; padding: 0 !important; max-width: 100% !important; margin-top: 0 !important; }\n" +
            "body.pip-mode .player-container { margin-top: 0 !important; }\n" +
            "body.pip-mode .native-player, body.pip-mode .player-wrapper { height: 100vh !important; width: 100vw !important; border-radius: 0 !important; }\n" +
            // border is the MD3 pass's "read as a container, not a floating card" value, folded
            // in directly (that pass came later with no !important, so it already won).
            ".media-description { font-size: 14px; line-height: 1.5; color: var(--media-desc-color); white-space: pre-wrap; word-break: break-word; overflow-wrap: break-word; min-width: 0; background-color: var(--media-desc-bg); padding: 12px; border-radius: 12px; border: none; margin-top: 12px; }\n" +
            ".comments-section { padding-top: 16px; min-width: 0; }\n" +
            ".comment-count { font-size: 16px; font-weight: 500; letter-spacing: 0.15px; margin-bottom: 16px; color: var(--comment-count-color); }\n" +
            ".chapters-section { margin-top: 4px; min-width: 0; }\n" +
            // Card treatment (background + radius), not plain text, so the row reads as a control
            // rather than a label - same affordance problem .md-select-wrap solves for dropdowns.
            ".chapters-header { display: flex; align-items: center; justify-content: space-between; gap: 12px; cursor: pointer; background-color: var(--search-input-bg); padding: 10px 14px; border-radius: 12px; transition: background-color 0.15s ease; }\n" +
            ".chapters-header:hover, .chapters-header:active { background-color: var(--md-state-hover, rgba(124, 58, 237, 0.12)); }\n" +
            ".chapters-header .comment-count { margin-bottom: 0; }\n" +
            ".chapters-header-right { display: flex; align-items: center; gap: 8px; min-width: 0; color: var(--comment-time-color); font-size: 13px; }\n" +
            "#current-chapter-label { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 200px; }\n" +
            // Circular badge around the chevron, same "this is a button" cue as .md-select-arrow's
            // own treatment, so the toggle affordance doesn't rely on the icon shape alone.
            ".chapters-toggle-icon { font-size: 18px; flex-shrink: 0; width: 26px; height: 26px; display: flex; align-items: center; justify-content: center; border-radius: 50%; background-color: var(--card-bg); color: var(--text-color); }\n" +
            ".chapters-list { display: flex; flex-direction: column; gap: 4px; margin-top: 12px; padding: 0 2px; }\n" +
            ".chapter-item { display: flex; align-items: center; gap: 12px; padding: 6px; border-radius: 8px; cursor: pointer; min-width: 0; }\n" +
            ".chapter-item:hover { background-color: var(--search-input-bg); }\n" +
            ".chapter-item.active { background-color: var(--search-input-bg); }\n" +
            ".chapter-thumb { width: 80px; height: 45px; object-fit: cover; border-radius: 6px; flex-shrink: 0; background-color: var(--card-thumbnail-bg); }\n" +
            ".chapter-item-body { display: flex; flex-direction: column; gap: 2px; min-width: 0; overflow: hidden; }\n" +
            ".chapter-item-time { font-size: 12px; font-variant-numeric: tabular-nums; color: var(--comment-time-color); }\n" +
            ".chapter-item-title { font-size: 14px; color: var(--text-color); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }\n" +
            ".comment { display: flex; gap: 12px; margin-bottom: 16px; min-width: 0; }\n" +
            ".comment-avatar { width: 36px; height: 36px; border-radius: 50%; object-fit: cover; flex-shrink: 0; aspect-ratio: 1 / 1; background-color: var(--card-thumbnail-bg); }\n" +
            ".comment-details { display: flex; flex-direction: column; gap: 4px; min-width: 0; overflow: hidden; word-break: break-word; overflow-wrap: break-word; }\n" +
            ".comment-header { display: flex; gap: 8px; align-items: center; min-width: 0; }\n" +
            ".comment-author { font-size: 13px; font-weight: 500; color: var(--comment-author-color); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }\n" +
            ".comment-time { font-size: 12px; color: var(--comment-time-color); flex-shrink: 0; }\n" +
            ".comment-text { font-size: 14px; line-height: 1.4; color: var(--comment-text-color); white-space: pre-wrap; word-break: break-word; overflow-wrap: break-word; min-width: 0; }\n" +
            // YouTube-style comment chrome: verified checkmark, pinned label, icon-led like count,
            // hearted badge - driven by CommentsInfoItem's isUploaderVerified/isPinned/
            // isHeartedByUploader fields, previously unrendered.
            ".comment-verified { font-size: 14px; color: var(--md-primary); vertical-align: middle; margin-left: 2px; }\n" +
            ".comment-pinned { display: flex; align-items: center; gap: 4px; font-size: 12px; font-weight: 500; color: var(--comment-time-color); }\n" +
            ".comment-pinned .material-symbols-rounded { font-size: 14px; }\n" +
            ".comment-meta { display: flex; align-items: center; gap: 12px; margin-top: 4px; }\n" +
            ".comment-like { display: flex; align-items: center; gap: 4px; font-size: 12px; color: var(--comment-time-color); }\n" +
            ".comment-like .material-symbols-rounded { font-size: 16px; }\n" +
            ".comment-hearted .material-symbols-rounded { font-size: 14px; color: #ff4b5c; }\n" +
            // Replies fetch through the same /comments endpoint (getReplies() is just another Page)
            // - .comment-replies starts empty/collapsed, fills on first click (window.toggleReplies).
            ".comment-replies-toggle { display: inline-flex; align-items: center; gap: 4px; margin-top: 6px; font-size: 13px; font-weight: 600; color: var(--md-primary); text-decoration: none; cursor: pointer; }\n" +
            ".comment-replies-toggle .reply-chevron { font-size: 18px; transition: transform 0.2s var(--md-easing); }\n" +
            ".comment-replies-toggle.expanded .reply-chevron { transform: rotate(180deg); }\n" +
            ".comment-replies { display: none; margin-top: 8px; padding-left: 16px; border-left: 2px solid var(--comments-border); }\n" +
            ".comment-replies.expanded { display: block; }\n" +
            ".comment-replies .comment-avatar { width: 28px; height: 28px; }\n" +
            ".comments-load-more-wrapper { text-align: center; margin-top: 8px; }\n" +
            ".channel-header { background-color: var(--channel-header-bg); border-radius: 12px; overflow: hidden; margin-bottom: 24px; border: 1px solid var(--channel-header-border); min-width: 0; }\n" +
            ".channel-banner { width: 100%; height: 160px; object-fit: cover; background: #272727; }\n" +
            ".channel-details { display: flex; padding: 16px; align-items: center; gap: 16px; flex-wrap: wrap; min-width: 0; }\n" +
            ".channel-avatar { width: 80px; height: 80px; border-radius: 50%; object-fit: cover; flex-shrink: 0; aspect-ratio: 1 / 1; }\n" +
            ".channel-info-block { display: flex; flex-direction: column; gap: 4px; flex-grow: 1; min-width: 0; overflow: hidden; word-break: break-word; overflow-wrap: break-word; }\n" +
            ".channel-name { font-size: 24px; font-weight: 700; color: var(--channel-name-color); word-break: break-word; overflow-wrap: break-word; min-width: 0; }\n" +
            ".channel-desc { font-size: 14px; color: var(--channel-desc-color); max-width: 600px; margin-top: 8px; line-height: 1.4; word-break: break-word; overflow-wrap: break-word; min-width: 0; }\n" +
            // MD3 Tabs: flush underline, not a filled pill - outlineVariant divider spans the row,
            // active tab's 3dp border-bottom picks up primary color. Was hardcoded #0f0f0f/#ffffff
            // per theme, the one place not wired to dynamic color - now uses --md-primary like
            // .subs-tab.
            ".channel-tabs-selector { display: flex; border-top: 1px solid var(--media-info-border); border-bottom: 1px solid var(--md-outline-variant); padding: 0 16px; }\n" +
            ".channel-tab-btn { display: flex; align-items: center; padding: 12px 16px; font-size: 14px; font-weight: 500; color: var(--md-on-surface-variant); border-bottom: 3px solid transparent; cursor: pointer; text-decoration: none; transition: color 0.2s var(--md-easing), border-color 0.2s var(--md-easing); }\n" +
            ".channel-tab-btn:hover { color: var(--text-color); }\n" +
            ".channel-tab-btn.active { color: var(--md-primary); border-bottom-color: var(--md-primary); }\n" +
            // border/background-color: MD3 !important values, folded in directly.
            ".loading-placeholder { text-align: center; font-size: 15px; padding: 48px 16px; color: var(--card-meta-color); background-color: var(--md-surface-high); border-radius: 16px; border: none; margin: 16px 0; }\n" +
            ".m3-spinner { width: 40px; height: 40px; border: 4px solid var(--search-input-bg); border-top: 4px solid var(--logo-color); border-radius: 50%; animation: m3-spin 0.8s linear infinite; margin: 24px auto; }\n" +
            "@keyframes m3-spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }\n" +
            ".settings-title { font-size: 20px; font-weight: 700; margin-bottom: 24px; color: var(--settings-title-color); }\n" +
            ".settings-section { margin-bottom: 24px; padding-bottom: 20px; border-bottom: 1px solid var(--settings-section-border); }\n" +
            ".settings-section:last-child { border-bottom: none; }\n" +
            ".settings-section-title { font-size: 16px; font-weight: 500; margin-bottom: 12px; color: var(--settings-section-title-color); }\n" +
            // gap: minimum breathing room when space-between has no slack left - the quality/
            // home-feed-mode <select> (width:100%) touched the label with zero gap on a 375px phone.
            ".setting-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 16px; }\n" +
            ".setting-label-group { display: flex; flex-direction: column; gap: 2px; }\n" +
            ".setting-label { font-size: 14px; font-weight: 500; color: var(--setting-label-color); }\n" +
            ".setting-desc { font-size: 12px; color: var(--setting-desc-color); }\n" +
            ".switch { position: relative; display: inline-block; width: 40px; height: 20px; }\n" +
            ".switch input { opacity: 0; width: 0; height: 0; }\n" +
            ".slider { position: absolute; cursor: pointer; top: 0; left: 0; right: 0; bottom: 0; background-color: var(--slider-bg); transition: .2s; border-radius: 20px; }\n" +
            ".slider:before { position: absolute; content: ''; height: 14px; width: 14px; left: 3px; bottom: 3px; background-color: white; transition: .2s; border-radius: 50%; }\n" +
            "input:checked + .slider { background-color: var(--md-primary, #cc0000); }\n" +
            "input:checked + .slider:before { transform: translateX(20px); }\n" +
            ".textarea-group { display: flex; flex-direction: column; gap: 8px; margin-bottom: 16px; }\n" +
            ".textarea-label { font-size: 14px; font-weight: 500; color: var(--textarea-label-color); }\n" +
            // No :focus rule here on purpose - falls through to the shared body.using-keyboard
            // :focus-visible mechanism instead of a hardcoded #1a73e8 border firing on every click.
            ".settings-textarea { width: 100%; height: 100px; padding: 12px; border-radius: 8px; border: 1px solid var(--textarea-border); background-color: var(--textarea-bg); color: var(--textarea-color); font-size: 14px; outline: none; transition: border-color 0.2s; resize: vertical; font-family: inherit; }\n" +
            ".btn-save { display: inline-block; width: 100%; padding: 12px; border-radius: 24px; font-size: 14px; font-weight: 500; text-align: center; border: none; cursor: pointer; transition: background-color 0.2s; }\n" +
            ".btn-save-primary { background-color: var(--md-primary, #cc0000); color: var(--md-on-primary, #ffffff); }\n" +
            ".btn-save-primary:hover { opacity: 0.9; }\n" +
            ".alert-banner { background-color: rgba(43, 138, 62, 0.1); color: #2b8a3e; padding: 12px; border-radius: 8px; margin-bottom: 20px; font-size: 14px; font-weight: 500; border: 1px solid rgba(43, 138, 62, 0.2); }\n" +
            ".theme-toggle-btn { background: none; border: none; font-size: 20px; cursor: pointer; padding: 8px; border-radius: 50%; display: flex; align-items: center; justify-content: center; color: var(--text-color); }\n" +
            ".theme-toggle-btn:hover { background-color: var(--service-tab-hover-bg); }\n" +
            "#connect-remote-btn { background: none; border: none; font-size: 20px; cursor: pointer; padding: 8px; border-radius: 50%; display: flex; align-items: center; justify-content: center; color: var(--text-color); }\n" +
            "#connect-remote-btn:hover { background-color: var(--service-tab-hover-bg); }\n" +
            ".connect-text { display: none; }\n" +
            /* Compounded with .material-symbols-rounded, not just .theme-icon-* - that class sets
             * display:inline-block later in the file and would win by source order at equal
             * specificity, showing both icons in both themes. */
            ".theme-icon-light.material-symbols-rounded { display: none; }\n" +
            ".theme-icon-dark.material-symbols-rounded { display: block; }\n" +
            "[data-theme=\"dark\"] .theme-icon-light.material-symbols-rounded { display: block; }\n" +
            "[data-theme=\"dark\"] .theme-icon-dark.material-symbols-rounded { display: none; }\n" +
            "body.has-banner header { top: 40px; }\n" +
            "body.has-banner .sidebar-nav { top: 96px; }\n" +
            "body.has-banner .container { margin-top: 96px; }\n" +
            "@media (max-width: 768px) {\n" +
            "  body.has-banner .container { margin-top: 96px; }\n" +
            "}\n" +
            ".sidebar-nav, .container { transition: width 0.2s ease, margin-left 0.2s ease, max-width 0.2s ease; }\n" +
            ".search-suggestions { position: absolute; top: 42px; left: 0; right: 0; background-color: var(--bg-color); border: 1px solid var(--search-input-border); border-radius: 8px; box-shadow: 0 4px 12px rgba(0,0,0,0.15); z-index: 10000; display: none; flex-direction: column; padding: 8px 0; max-height: 350px; overflow-y: auto; }\n" +
            ".search-suggestion-item { display: flex; align-items: center; justify-content: space-between; padding: 8px 16px; cursor: pointer; font-size: 14px; color: var(--text-color); }\n" +
            ".search-suggestion-item:hover { background-color: var(--service-tab-hover-bg); }\n" +
            ".search-suggestion-text { display: flex; align-items: center; gap: 12px; flex-grow: 1; }\n" +
            ".search-suggestion-delete { color: var(--md-error, #cc0000); font-size: 12px; cursor: pointer; padding: 4px 8px; border-radius: 4px; }\n" +
            ".search-suggestion-delete:hover { background-color: rgba(204,0,0,0.1); }\n" +
            // <media-player class="player-wrapper"> IS the wrapper now (no separate div) - class
            // kept generic (not video.js-specific) since it doubles as the mini-player's toggle
            // target and the pip-mode selector above.
            ".player-wrapper { position: relative; width: 100%; border-radius: 12px; overflow: hidden; background: #000; }\n" +
            // Mini-player: fixed corner box once IntersectionObserver reports the wrapper fully
            // scrolled out of view (HtmlScripts.kt's initMiniPlayer()). Overlays sized for the
            // full-width player (double-tap icons, volume HUD, up-next card, SponsorBlock button)
            // read as oversized clutter at this scale, so they're hidden rather than rescaled -
            // Vidstack's Default Layout control bar shrinks itself responsively at this width,
            // no extra CSS needed for that part.
            ".player-wrapper.mini-player { position: fixed; bottom: 16px; right: 16px; width: min(320px, 42vw); z-index: 1000; box-shadow: 0 8px 24px rgba(0,0,0,0.45); }\n" +
            ".player-wrapper.mini-player .double-tap-indicator, .player-wrapper.mini-player .volume-hud, .player-wrapper.mini-player .up-next-overlay, .player-wrapper.mini-player .sponsor-skip-btn, .player-wrapper.mini-player .danmaku-layer { display: none; }\n" +
            ".mini-player-close-btn { position: absolute; top: 6px; right: 6px; display: none; align-items: center; justify-content: center; width: 28px; height: 28px; border: none; border-radius: 50%; background: rgba(0,0,0,0.6); color: #fff; cursor: pointer; z-index: 1001; }\n" +
            ".mini-player-close-btn .material-symbols-rounded { font-size: 16px; }\n" +
            // touch-action:none so a finger drag moves the box instead of scrolling the page.
            ".mini-player-drag-handle { position: absolute; top: 6px; left: 6px; display: none; align-items: center; justify-content: center; width: 28px; height: 28px; border-radius: 50%; background: rgba(0,0,0,0.6); color: #fff; cursor: grab; touch-action: none; z-index: 1001; }\n" +
            ".mini-player-drag-handle:active { cursor: grabbing; }\n" +
            ".mini-player-drag-handle .material-symbols-rounded { font-size: 18px; }\n" +
            // Handle + close button: laid out in mini mode but invisible/untappable until revealed,
            // so they don't sit over the picture all the time. Mouse: revealed on hover of the box
            // (:focus-within covers keyboard). Touch has no hover, so there they follow Vidstack's
            // own controls visibility ([data-controls], toggled by tapping the player) instead -
            // hover-only would leave them unreachable on a phone.
            ".player-wrapper.mini-player .mini-player-close-btn, .player-wrapper.mini-player .mini-player-drag-handle { display: flex; opacity: 0; pointer-events: none; transition: opacity 0.15s ease; }\n" +
            "@media (hover: hover) {\n" +
            "  .player-wrapper.mini-player:hover .mini-player-close-btn, .player-wrapper.mini-player:hover .mini-player-drag-handle, .player-wrapper.mini-player:focus-within .mini-player-close-btn, .player-wrapper.mini-player:focus-within .mini-player-drag-handle { opacity: 1; pointer-events: auto; }\n" +
            "}\n" +
            "@media (hover: none) {\n" +
            "  .player-wrapper.mini-player[data-controls] .mini-player-close-btn, .player-wrapper.mini-player[data-controls] .mini-player-drag-handle { opacity: 1; pointer-events: auto; }\n" +
            "}\n" +
            ".double-tap-indicator {\n" +
            "  position: absolute;\n" +
            "  top: 0;\n" +
            "  bottom: 0;\n" +
            "  width: 35%;\n" +
            "  display: flex;\n" +
            "  flex-direction: column;\n" +
            "  align-items: center;\n" +
            "  justify-content: center;\n" +
            "  background: rgba(0, 0, 0, 0.4);\n" +
            "  color: #fff;\n" +
            "  opacity: 0;\n" +
            "  pointer-events: none;\n" +
            "  transition: opacity 0.25s ease-in-out;\n" +
            "  z-index: 10;\n" +
            "}\n" +
            ".double-tap-indicator.left { left: 0; border-top-left-radius: 12px; border-bottom-left-radius: 12px; }\n" +
            ".double-tap-indicator.right { right: 0; border-top-right-radius: 12px; border-bottom-right-radius: 12px; }\n" +
            ".double-tap-indicator.show { opacity: 1; }\n" +
            ".double-tap-indicator svg { width: 44px; height: 44px; fill: #fff; animation: bounceGlow 0.5s infinite alternate; }\n" +
            ".double-tap-text { font-size: 14px; font-weight: bold; margin-top: 6px; }\n" +
            "@keyframes bounceGlow {\n" +
            "  0% { transform: scale(1); filter: drop-shadow(0 0 2px rgba(255,255,255,0.6)); }\n" +
            "  100% { transform: scale(1.08); filter: drop-shadow(0 0 8px rgba(255,255,255,0.9)); }\n" +
            "}\n" +
            ".volume-hud {\n" +
            "  position: absolute;\n" +
            "  top: 24px;\n" +
            "  left: 50%;\n" +
            "  transform: translateX(-50%);\n" +
            "  background: rgba(0, 0, 0, 0.8);\n" +
            "  color: #fff;\n" +
            "  padding: 8px 16px;\n" +
            "  border-radius: 20px;\n" +
            "  font-size: 13px;\n" +
            "  font-weight: 500;\n" +
            "  z-index: 15;\n" +
            "  display: flex;\n" +
            "  align-items: center;\n" +
            "  gap: 8px;\n" +
            "  opacity: 0;\n" +
            "  transition: opacity 0.2s ease;\n" +
            "  pointer-events: none;\n" +
            "}\n" +
            ".volume-hud.show { opacity: 1; }\n" +
            ".up-next-overlay {\n" +
            "  position: absolute;\n" +
            "  top: 0; left: 0; right: 0; bottom: 0;\n" +
            "  background: rgba(0,0,0,0.88);\n" +
            "  z-index: 20;\n" +
            "  display: flex;\n" +
            "  flex-direction: column;\n" +
            "  align-items: center;\n" +
            "  justify-content: center;\n" +
            "  color: #fff;\n" +
            "  opacity: 0;\n" +
            "  pointer-events: none;\n" +
            "  transition: opacity 0.3s ease;\n" +
            "}\n" +
            ".up-next-overlay.show { opacity: 1; pointer-events: auto; }\n" +
            ".up-next-title { font-size: 12px; text-transform: uppercase; color: #bbb; letter-spacing: 1.5px; margin-bottom: 6px; }\n" +
            ".up-next-name { font-size: 18px; font-weight: bold; text-align: center; max-width: 80%; margin-bottom: 12px; }\n" +
            ".up-next-thumb { width: 160px; aspect-ratio: 16/9; border-radius: 8px; object-fit: cover; box-shadow: 0 4px 12px rgba(0,0,0,0.5); margin-bottom: 16px; }\n" +
            ".up-next-btn-row { display: flex; gap: 12px; }\n" +
            ".up-next-btn { padding: 8px 20px; border-radius: 20px; border: none; font-weight: 600; cursor: pointer; font-size: 13px; }\n" +
            ".up-next-btn-play { background: var(--md-primary, #7c3aed); color: var(--md-on-primary, #fff); }\n" +
            ".up-next-btn-cancel { background: rgba(255,255,255,0.18); color: #fff; }\n" +
            ".up-next-circle { position: relative; width: 56px; height: 56px; margin-bottom: 12px; }\n" +
            ".up-next-circle svg { transform: rotate(-90deg); }\n" +
            ".up-next-circle circle { fill: none; stroke-width: 4; }\n" +
            ".up-next-circle-bg { stroke: rgba(255,255,255,0.2); }\n" +
            ".up-next-circle-val { stroke: var(--md-primary, #7c3aed); stroke-dasharray: 138; stroke-dashoffset: 0; transition: stroke-dashoffset 0.1s linear; }\n" +
            "video, .player-wrapper video { object-fit: fill !important; }\n" +
            // "fill" is fine normally (player box already pinned to 16:9), but browser fullscreen
            // replaces that box with the actual (usually non-16:9) screen shape, stretching the
            // picture worse if orientation.lock() fails. "contain" letterboxes instead.
            // [data-fullscreen] is Vidstack's own boolean-attribute reflection on <media-player>
            // (same convention as its confirmed data-paused/data-can-fullscreen attributes) - not
            // independently confirmed live, but initFullscreenLetterbox() in HtmlScripts.kt sets
            // the same object-fit inline with !important as a JS-side fallback regardless.
            ".player-wrapper[data-fullscreen], .player-wrapper[data-fullscreen] video { object-fit: contain !important; background-color: #000; }\n" +

            // Bilibili danmaku overlay. pointer-events:none throughout; placed early in
            // <media-player>'s DOM (right after <media-provider>) so later overlays stack above it
            // with no z-index juggling.
            ".danmaku-layer { position: absolute; top: 0; left: 0; right: 0; bottom: 0; overflow: hidden; pointer-events: none; }\n" +
            ".danmaku-item { position: absolute; white-space: nowrap; font-weight: 600; line-height: 1; text-shadow: 0 0 3px rgba(0,0,0,0.9), 0 0 6px rgba(0,0,0,0.6); will-change: transform; }\n" +
            ".danmaku-item.top, .danmaku-item.bottom { left: 50%; transform: translateX(-50%); }\n" +
            // Scroll-type items animate via a JS-driven `transition`, paused in JS instead - this
            // only freezes the CSS `animation` the top/bottom fade variants use.
            ".danmaku-layer.video-paused .danmaku-item.top, .danmaku-layer.video-paused .danmaku-item.bottom { animation-play-state: paused; }\n" +
            "@keyframes danmaku-fade { 0% { opacity: 0; } 10%, 85% { opacity: 1; } 100% { opacity: 0; } }\n" +
            ".danmaku-toggle-btn { display: inline-flex; align-items: center; justify-content: center; width: 32px; height: 32px; border-radius: 50%; border: none; cursor: pointer; background: rgba(124,58,237,0.15); color: var(--text-color); }\n" +
            ".danmaku-toggle-btn .material-symbols-rounded { font-size: 20px; }\n" +
            ".danmaku-toggle-btn.off { opacity: 0.45; }\n" +

            /* ---- Material 3 refinements -------------------------------------------------
             * Appended last to win over earlier rules. Adds MD3 state layers (8%/12% onSurface
             * overlay), filled-button color roles, and MD3/YouTube's flat list treatment.
             * ---------------------------------------------------------------------------- */
            "* { -webkit-tap-highlight-color: transparent; }\n" +

            /* Material Symbols: one font, consistent optical size/stroke weight across icons.
             * opsz matches font-size to keep strokes correct. display=block avoids a ligature-text
             * flash while loading. */
            ".material-symbols-rounded { font-family: 'Material Symbols Rounded'; font-weight: normal; font-style: normal; font-size: 24px; line-height: 1; letter-spacing: normal; text-transform: none; display: inline-block; white-space: nowrap; word-wrap: normal; direction: ltr; -webkit-font-feature-settings: 'liga'; -webkit-font-smoothing: antialiased; font-variation-settings: 'FILL' 0, 'wght' 400, 'GRAD' 0, 'opsz' 24; transition: font-variation-settings 0.2s var(--md-easing); }\n" +
            /* MD3 navigation: the selected destination uses the filled glyph, the rest outlined. */
            ".sidebar-item.active .material-symbols-rounded, .bottom-nav-item.active .material-symbols-rounded { font-variation-settings: 'FILL' 1, 'wght' 400, 'GRAD' 0, 'opsz' 24; }\n" +
            ".sidebar-icon, .bottom-nav-icon { display: inline-flex; align-items: center; justify-content: center; font-size: 0; }\n" +
            /* After a tap, mobile browsers leave the link focused and keep drawing their focus
             * ring, so the logo stayed outlined in purple long after the tap. Suppress the ring
             * only for pointer input — :focus-visible still fires for keyboard users, so this
             * removes the artefact without removing keyboard accessibility. */
            /* Mobile browsers keep a tapped link focused and draw their own ring, which left the
             * logo permanently outlined. Relying on :focus-visible alone is not enough — some
             * browsers do treat a tapped link as focus-visible. So suppress the ring outright and
             * re-enable it only while the user is actually navigating by keyboard (flag set by the
             * script below). Deterministic, and keyboard accessibility is preserved. */
            ":focus, :focus-visible { outline: none !important; box-shadow: none !important; }\n" +
            ".logo:focus { background: transparent !important; border-color: transparent !important; }\n" +
            "body.using-keyboard :focus-visible { outline: 2px solid var(--md-primary) !important; outline-offset: 2px; border-radius: 4px; }\n" +
            /* MD3 standard easing; the stock 'ease' curve reads noticeably less crisp. */
            ":root { --md-easing: cubic-bezier(0.2, 0, 0, 1); }\n" +

            /* Video items: no fill, no border, no shadow, no hover lift (folded into the base
             * .card/.card:hover rules above - MD3 used !important there specifically so it would
             * always win, so stating the final value once is equivalent). Whitespace separates
             * them, exactly as MD3 lists and every modern video client do. The previous raised
             * card with translateY on hover is a Material 1 pattern. */
            /* Thumbnail keeps its 16px radius — that is MD3's "large" shape token, so there is
             * nothing to correct here. */
            ".card:hover .card-thumbnail { filter: brightness(1.06); }\n" +
            ".card-thumbnail, .card-title, .card-meta { transition: filter 0.2s var(--md-easing), color 0.2s var(--md-easing); }\n" +
            ".card-details { padding-top: 12px !important; }\n" +

            /* Filled button. The Subscribe button previously hardcoded white text on the primary
             * colour, which is unreadable whenever the palette makes primary light — as Material
             * You dynamic colour routinely does. onPrimary is the role that always pairs. */
            ".subscribe-btn { background-color: var(--md-primary) !important; color: var(--md-on-primary) !important; height: 40px; padding: 0 24px; display: inline-flex; align-items: center; justify-content: center; border-radius: 20px; font-size: 14px; font-weight: 500; letter-spacing: 0.1px; position: relative; overflow: hidden; transition: box-shadow 0.2s var(--md-easing); border: none; cursor: pointer; text-decoration: none; text-align: center; flex-shrink: 0; margin-left: auto; white-space: nowrap; }\n" +
            ".subscribe-btn:hover { opacity: 1 !important; box-shadow: 0 1px 3px 1px rgba(0,0,0,0.15); }\n" +
            ".subscribe-btn::after { content: ''; position: absolute; inset: 0; background: currentColor; opacity: 0; transition: opacity 0.2s var(--md-easing); pointer-events: none; }\n" +
            ".subscribe-btn:hover::after { opacity: 0.08; }\n" +
            ".subscribe-btn:active::after { opacity: 0.12; }\n" +
            ".subscribe-btn.subscribed, .subscribe-btn.blocked { background-color: var(--md-surface-high) !important; color: var(--md-on-surface) !important; }\n" +
            ".subscribe-btn.danger { background-color: var(--md-error) !important; color: var(--md-on-error) !important; }\n" +

            /* Tonal buttons (action pills, pagination, like/dislike) share one MD3 spec. */
            ".action-pill-btn, .btn-page, .pill-btn { height: 40px; border-radius: 20px; font-size: 14px; font-weight: 500; letter-spacing: 0.1px; position: relative; overflow: hidden; transition: background-color 0.2s var(--md-easing); }\n" +
            // justify-content, not text-align: these are inline-flex, and text-align has no effect
            // on a flex container's content.
            ".action-pill-btn, .btn-page { background-color: var(--md-surface-high); color: var(--md-on-surface); padding: 0 20px; line-height: normal; display: inline-flex; align-items: center; justify-content: center; gap: 8px; }\n" +
            ".action-pill-btn:hover, .btn-page:hover { background-color: var(--md-state-hover); }\n" +
            ".action-pill-btn:active, .btn-page:active { background-color: var(--md-state-press); }\n" +
            ".pill-btn:hover { background-color: var(--md-state-hover); }\n" +

            /* Icon buttons: MD3 makes these a 40dp circular target with a state layer. */
            ".search-btn, .theme-toggle-btn, #connect-remote-btn { width: 40px; height: 40px; border-radius: 50%; display: inline-flex; align-items: center; justify-content: center; border: none; background: transparent; color: var(--md-on-surface-variant); cursor: pointer; transition: background-color 0.2s var(--md-easing); }\n" +
            ".search-btn:hover, .theme-toggle-btn:hover, #connect-remote-btn:hover { background-color: var(--md-state-hover); }\n" +
            ".search-btn:active, .theme-toggle-btn:active, #connect-remote-btn:active { background-color: var(--md-state-press); }\n" +

            /* Navigation + service switcher state layers. .sidebar-item's own transition/hover
             * are folded into its base rule earlier in the file - they had no !important but came
             * later, so they already won regardless of source order. */
            ".service-switcher a { height: 32px; display: inline-flex; align-items: center; transition: background-color 0.2s var(--md-easing); }\n" +
            ".service-switcher a:hover { background-color: var(--md-state-hover); }\n" +

            /* Hairlines use outlineVariant rather than a flat alpha wash. */
            ".media-info, .comments-section, .channel-header { border-color: var(--md-outline-variant) !important; }\n" +

            /* Subscriptions page. Previously its own "filled pill in a rounded tray" treatment,
             * different from .channel-tab-btn's underline style for the exact same job (switching
             * between content views within a page) - unified onto the same MD3 Tabs underline
             * pattern as .channel-tab-btn above, so there's one tab language in the app rather
             * than two. Channel rows previously had no container, no hover feedback, and their
             * subtitle repeated the title as plain text with a bold red emoji badge. */
            ".subs-tabbar { display: flex; border-bottom: 1px solid var(--md-outline-variant); margin-bottom: 24px; width: 100%; max-width: 100%; overflow-x: auto; scrollbar-width: none; -ms-overflow-style: none; }\n" +
            ".subs-tabbar::-webkit-scrollbar { display: none; }\n" +
            ".subs-tab { display: flex; align-items: center; gap: 8px; padding: 12px 16px; font-size: 14px; font-weight: 500; color: var(--md-on-surface-variant); text-decoration: none; white-space: nowrap; border-bottom: 3px solid transparent; cursor: pointer; transition: color 0.2s var(--md-easing), border-color 0.2s var(--md-easing); }\n" +
            ".subs-tab .material-symbols-rounded { font-size: 20px; }\n" +
            ".subs-tab:hover { color: var(--text-color); }\n" +
            ".subs-tab.active { color: var(--md-primary); border-bottom-color: var(--md-primary); }\n" +
            ".subs-tab.active .material-symbols-rounded { font-variation-settings: 'FILL' 1, 'wght' 400, 'GRAD' 0, 'opsz' 20; }\n" +
            /* Base .card sets "flex-direction: column" for video cards (thumbnail over details);
             * without an explicit override here that value still wins for this property even
             * though display:flex above is forced, stacking the avatar over the centered text
             * instead of laying out a row. width:100% is needed too: a flex-formatting-context
             * grid item does not reliably stretch to fill its spanned tracks on its own, so without
             * it the row shrinks to its content width and sits whichever column auto-placement
             * left it in instead of spanning edge-to-edge. */
            ".grid .channel-row-card { grid-column: 1 / -1; display: flex !important; flex-direction: row !important; width: 100%; align-items: center; gap: 16px; padding: 10px 16px !important; border-radius: 16px; transition: background-color 0.2s var(--md-easing); }\n" +
            ".grid .channel-row-card:hover { background-color: var(--md-state-hover); }\n" +
            ".grid .channel-row-card:active { background-color: var(--md-state-press); }\n" +
            ".channel-row-badge { display: inline-flex; align-items: center; gap: 4px; font-size: 12px; color: var(--md-on-surface-variant); }\n" +
            ".channel-row-badge .material-symbols-rounded { font-size: 14px; }\n";
}
