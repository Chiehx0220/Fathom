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
            // Fixed brand mark colors for the header/favicon badge - unlike --logo-color (which
            // shifts with the user's chosen Material You accent), the badge is meant to stay a
            // constant, recognizable mark; only its light/dark contrast should change.
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
            // Shared "heading + action button" row (Watch History's title + Select toggle today,
            // usable anywhere else that pattern shows up). Margin lives here instead of on the h2
            // itself so the row's own top/bottom edges - not just the text baseline - are what the
            // page's spacing is measured from, keeping the heading and its button vertically level.
            ".page-header-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 20px; }\n" +
            ".page-header-row h2 { margin: 0; }\n" +
            // Watch History's select-mode toolbar. flex-wrap lets the two button actions drop to
            // their own row instead of overflowing/crushing on a narrow phone width - the mobile
            // block below turns that wrapped row into two equal-width buttons instead of a left-
            // packed cluster.
            ".history-select-bar { align-items: center; justify-content: space-between; gap: 12px; row-gap: 12px; flex-wrap: wrap; margin-bottom: 20px; padding: 12px 16px; background-color: var(--card-bg); border-radius: 12px; }\n" +
            ".history-select-info { display: flex; align-items: center; gap: 12px; }\n" +
            ".history-select-all-label { display: flex; align-items: center; gap: 8px; cursor: pointer; }\n" +
            ".history-select-count { color: var(--card-meta-color); }\n" +
            ".history-select-actions { display: flex; gap: 8px; }\n" +
            // gap is the MD3 pass's value, folded in directly here instead of left as a separate
            // later rule - the later rule was unconditional, so it was also silently beating the
            // mobile-only "gap: 20px" rule below whenever both applied, which was never intended
            // (user confirmed: mobile should keep its own tighter 20px, not inherit this one).
            // Folding it in here instead of leaving it after the mobile block lets that mobile
            // rule win again, same mechanism as every other merge in this file.
            ".grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 28px 20px; }\n" +
            // background-color/border/border-radius/padding are forced by the MD3 pass further
            // down (flat list treatment, no card chrome) - written directly here since that pass
            // used !important specifically so it would always win regardless of where it sat in
            // the file, so stating the final values in one place changes nothing about the result.
            ".card { display: flex; flex-direction: column; cursor: pointer; background-color: transparent; border-radius: 0; padding: 0; border: none; transition: transform 0.2s, box-shadow 0.2s; min-width: 0; overflow: visible; word-break: break-word; overflow-wrap: break-word; }\n" +
            ".card:hover { transform: none; box-shadow: none; }\n" +
            ".card-thumbnail { width: 100%; aspect-ratio: 16/9; background-color: var(--card-thumbnail-bg); object-fit: cover; border-radius: 16px; transition: border-radius 0.2s; flex-shrink: 0; max-height: 240px; }\n" +
            ".card-details { display: flex; gap: 12px; padding: 12px 0 0 0; min-width: 0; overflow: hidden; }\n" +
            ".card-avatar { width: 40px; height: 40px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-weight: bold; color: white; font-size: 15px; flex-shrink: 0; aspect-ratio: 1 / 1; object-fit: cover; }\n" +
            // Always visible (not hover-only) so it's reachable on touch, where hover never fires.
            ".card-delete-btn { position: absolute; top: 8px; right: 8px; width: 32px; height: 32px; border-radius: 50%; border: none; background-color: rgba(0,0,0,0.6); color: #ffffff; display: flex; align-items: center; justify-content: center; cursor: pointer; z-index: 2; transition: background-color 0.2s; }\n" +
            ".card-delete-btn:hover { background-color: rgba(0,0,0,0.8); }\n" +
            ".card-delete-btn .material-symbols-rounded { font-size: 18px; }\n" +
            // Selection indicator only takes up space/appears once batch-select mode is actually on
            // (toggled via the "history-select-mode" class on <body>) - hidden by default so a page
            // that never enters select mode looks identical to before this feature existed. The
            // delete button hides at the same time so the two affordances don't compete for the
            // same top-right/top-left corners of the thumbnail while selecting many items.
            //
            // MD3-style circular selection badge (Google Photos/Files convention) instead of a bare
            // native checkbox, which read as un-styled and out of place next to the rest of this
            // MD3 pass. The real <input> is stretched invisibly over the whole badge (kept for
            // click/keyboard/screen-reader semantics) with a Material Symbols check glyph as a
            // sibling, shown via a :checked sibling selector - a checked/unchecked state has to be
            // painted this way rather than via ::before/::after on the input itself, which Chrome
            // (and any Chromium-based WebView) never renders on replaced elements like <input>.
            ".card-select-indicator { display: none; position: absolute; top: 8px; left: 8px; width: 24px; height: 24px; z-index: 2; border-radius: 50%; background-color: rgba(0,0,0,0.35); border: 2px solid rgba(255,255,255,0.9); box-shadow: 0 1px 3px rgba(0,0,0,0.3); align-items: center; justify-content: center; transition: background-color 0.15s var(--md-easing), border-color 0.15s var(--md-easing); }\n" +
            "body.history-select-mode .card-select-indicator { display: flex; }\n" +
            "body.history-select-mode .card-delete-btn { display: none; }\n" +
            ".card-select-checkbox { position: absolute; inset: 0; width: 100%; height: 100%; margin: 0; opacity: 0; cursor: pointer; }\n" +
            ".card-select-check-icon { font-size: 16px; color: var(--md-on-primary, #fff); opacity: 0; transition: opacity 0.15s var(--md-easing); pointer-events: none; }\n" +
            ".card-select-checkbox:checked ~ .card-select-check-icon { opacity: 1; }\n" +
            ".card-select-indicator:has(.card-select-checkbox:checked) { background-color: var(--md-primary); border-color: var(--md-primary); }\n" +
            ".card-info { display: flex; flex-direction: column; flex-grow: 1; min-width: 0; overflow: hidden; word-break: break-word; overflow-wrap: break-word; }\n" +
            // font-size/letter-spacing are the MD3 pass's type-scale values (title-medium /
            // body-small), folded in directly - that pass came later in the file with no
            // !important, so at equal specificity it already won regardless of source order.
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
            /* Rail rests at 72px and flies out to 240px on hover, staying fixed/overlaid so the
             * container's margin never changes - that's what makes the click-toggle version feel
             * janky (it reflows the whole page) and this version instant. */
            "  .sidebar-nav:hover { width: 240px; box-shadow: 2px 0 8px rgba(0,0,0,0.24); }\n" +
            "  .sidebar-nav:hover .sidebar-label { display: inline; }\n" +
            "  .sidebar-nav:hover .sidebar-item { justify-content: flex-start; padding: 12px 24px; }\n" +
            // Scoped to desktop only, where the mobile search-collapse/expand mechanics below
            // (.top-bar.search-active, .search-form's max-width transition) don't apply.
            //
            // Two CSS Grid attempts were tried and both failed for the same underlying reason -
            // logo+switcher (~277px) and the icon cluster (~90px) are not the same width, and
            // grid's "1fr" track-sizing algorithm does NOT give equal fr tracks equal final size
            // when their content-based minimums differ (verified live): a track whose content
            // exceeds its "fair" share freezes at its content size, and ALL remaining leftover
            // space then goes to the OTHER fr track alone. So "1fr auto 1fr" (auto middle) left
            // the search bar pinned to its own tiny intrinsic ~279px forever regardless of window
            // width, while "auto 1fr auto" let it grow but put it well off-center (measured
            // 277px/1fr/90px - the extra space piles up on the lighter side, not split evenly).
            // True pixel-centering with asymmetric side content isn't achievable through track
            // ratios alone; it needs both sides measured against the SAME reference. So: take the
            // search bar out of flow (position:absolute) and have HtmlScripts.SCRIPTS'
            // centerSearchBar() measure the two flanking groups' real widths on load/resize, then
            // center it on the bar's own midpoint with a width capped by whichever side is wider
            // - verified live at 769px/800px/1280px: no overlap with either side at any of them,
            // and the bar sits exactly on the true center line regardless of the 277/90 asymmetry.
            "  .top-bar { position: relative; }\n" +
            "  .search-form { position: absolute; left: 50%; top: 50%; transform: translate(-50%, -50%); margin: 0 !important; }\n" +
            "}\n" +
            "@media (max-width: 768px) {\n" +
            /* Confirmed via live measurement (getBoundingClientRect on-device) that a 375px top bar
             * cannot fit the full "Fathom" wordmark + the YouTube/BiliBili switcher + a 40px
             * search pill + the cast icon without something being crushed - see the .search-form
             * fix note below for what that crushing did. Dropping the wordmark to icon-only on
             * mobile (standard responsive pattern) frees the ~90px needed for everything else to
             * keep its real size instead. */
            "  .logo-text { display: none; }\n" +
            "  .logo svg { margin-right: 0 !important; }\n" +
            "  .sidebar-nav { display: none !important; }\n" +
            "  .bottom-nav { display: flex !important; position: fixed; bottom: 0; left: 0; right: 0; height: 80px; background: var(--bottom-nav-bg); border-top: none; box-shadow: 0 -1px 3px rgba(0,0,0,0.05); justify-content: space-around; align-items: center; z-index: 1000; padding-bottom: 8px; }\n" +
            "  .container { margin-left: 0; max-width: 100%; padding: 0 12px; margin-top: 56px; padding-bottom: 96px; }\n" +
            // Bottom padding, not just top: this was 0 before, which is why a heading like
            // "Trending" sat right on top of the first video card below it with no breathing
            // room - top spacing was covered, the gap *after* the heading wasn't.
            "  .container h2 { padding: 16px 4px 16px 4px; margin: 0 !important; }\n" +
            // .page-header-row's h2 (e.g. Watch History's title + Select button) is wrapped inside
            // the row, not the container's direct child, so the descendant rule above still
            // matches it too - stacking its own 16px top/bottom padding on top of the row's own
            // margin-bottom and making the heading text sit lower than the button beside it. This
            // wins (same specificity, declared later) and hands all of the row's spacing to the
            // row itself instead, so the heading and button stay level.
            "  .page-header-row { padding: 16px 4px 16px 4px; margin-bottom: 16px; }\n" +
            "  .page-header-row h2 { padding: 0 !important; }\n" +
            "  .history-select-actions { flex: 1 1 100%; }\n" +
            "  .history-select-actions .btn-page { flex: 1; }\n" +
            // Hardcoding 1 column here (instead of reusing the desktop repeat(auto-fill,
            // minmax(300px,1fr)) pattern) forced a single ~700px-wide card on tablets/landscape
            // phones anywhere in the 620-768px range, where two 300px+ columns would fit
            // comfortably - auto-fill already degrades to 1 column on real phone widths (its
            // minimum needs ~620px for a second column), so it's a strict improvement, not just
            // a mobile-only change.
            "  .grid { grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 20px; }\n" +
            "  .card-details { padding: 12px 4px; }\n" +
            "  .bottom-nav-item { display: flex; flex-direction: column; align-items: center; gap: 4px; color: var(--bottom-nav-item-color); font-size: 12px; font-weight: 500; text-decoration: none; flex-grow: 1; justify-content: center; }\n" +
            "  .bottom-nav-item .bottom-nav-icon { display: flex; align-items: center; justify-content: center; width: 64px; height: 32px; border-radius: 16px; transition: background-color 0.2s ease, color 0.2s ease; color: var(--bottom-nav-item-color); }\n" +
            "  .bottom-nav-item.active .bottom-nav-icon { background-color: var(--bottom-nav-active-pill-bg); color: var(--bottom-nav-item-active-color); }\n" +
            "  .bottom-nav-item.active { color: var(--text-color); }\n" +
            "  #theme-toggle { display: none !important; }\n" +
            "  #mobile-theme-row, #mobile-history-row { display: flex !important; }\n" +
            /* The desktop rule (line ~155) gives the pill 8px of left padding to buffer the input
             * before its own text, and keeps flex-grow:1 for the expanded search bar. Neither is
             * wrong on its own, but on a narrow top bar the logo/switcher group and the icon group
             * together can need more room than the bar has, and .search-form is the only sibling
             * whose overflow:hidden makes its flexbox automatic minimum width resolve to 0 (spec
             * behaviour: an item's auto min-width is 0, not its content size, whenever overflow is
             * not visible) - so it alone absorbs the whole shortfall and gets crushed to 0px,
             * leaving the 40px search-btn poking out of a phantom zero-width container. Verified via
             * getBoundingClientRect() on the live device: form width computed to 0 while the button
             * (unable to shrink past its own icon's content size) sat at 24px, explaining the
             * "sliced" look no padding tweak alone could fix. flex-shrink:0 + min-width:40px pin the
             * collapsed pill at its intended size no matter how tight the rest of the bar is. */
            /* .top-bar uses justify-content:space-between for the desktop layout, where the search
             * bar is meant to be a wide flex-grow item separating the brand from the action icons.
             * Collapsed to a 40px icon on mobile it's still a third independent flex item, so
             * space-between gives it its own equal share of space on both sides - it ends up
             * floating alone with a big gap on either side instead of reading as part of the
             * top-right icon cluster with cast. margin-left:auto (verified live) claims all the
             * leftover space for itself, leaving only the ordinary 16px gap to cast on its right. */
            "  .search-form { max-width: 40px; min-width: 40px; flex-shrink: 0; margin: 0; margin-left: auto; padding-left: 0; overflow: hidden; transition: max-width 0.3s ease; border-radius: 40px; }\n" +
            /* .search-form keeps its desktop height:48px and background-color (needed so the
             * expanded pill has a constant visible surface) even when collapsed to a 40px-wide
             * icon. A 40x48 rounded box is an oval, not a circle - verified live: with height
             * pinned to 40 and background dropped to transparent (matching how the cast/theme
             * buttons already look at rest, background only on hover/press) it renders as a true
             * circle. Scoped to :not(.search-active) so the expanded search bar keeps its pill. */
            "  .search-form:not(.search-active) { height: 40px; background: transparent; }\n" +
            /* .search-btn's own margin-right:4px (desktop rule, meant to space it from whatever
             * follows inside the pill) has nothing after it once collapsed, but still counts toward
             * the button's outer size inside the now-pinned 40px form - confirmed live it was
             * shrinking the button to 36px to make room for that trailing margin. */
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
            // min-width:0 is required here, not just on descendants like .media-info: as a CSS
            // Grid item of .player-layout's "1fr 360px" track, .main-content's default automatic
            // minimum width is its content's min-content size, which can exceed its fair 1fr share
            // and blow the grid past the container - confirmed live with a real video description
            // containing an unspaced "text:https://..." link (common in creator descriptions),
            // which pushed .main-content to 1057px in a 1113px-wide grid meant to split it 1fr/360px,
            // causing page-wide horizontal scroll. .sidebar gets the same treatment defensively,
            // since it sits in the same grid.
            ".main-content { display: flex; flex-direction: column; gap: 16px; min-width: 0; }\n" +
            ".sidebar { display: flex; flex-direction: column; gap: 16px; min-width: 0; }\n" +
            ".native-player { width: 100%; aspect-ratio: 16/9; border-radius: 12px; background-color: #000; outline: none; }\n" +
            ".media-info { padding: 16px 0; border-bottom: 1px solid var(--media-info-border); min-width: 0; overflow: hidden; }\n" +
            // font-size/font-weight/letter-spacing are the MD3 pass's title-large values, folded
            // in directly (that pass had no !important here but came later, so it already won).
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
            // background-color/height/color/cursor below are the MD3 pass's values, folded in
            // directly (that pass had no !important here but came later, so it already won -
            // .pill-btn's height:40px comes from the shared ".action-pill-btn, .btn-page,
            // .pill-btn" rule further down, which still legitimately applies on top of this one).
            ".like-dislike-pill { display: inline-flex; align-items: center; background-color: var(--md-surface-high); border-radius: 100px; height: 40px; overflow: hidden; flex-shrink: 0; }\n" +
            ".pill-btn { background: none; border: none; padding: 0 14px; height: 100%; color: var(--md-on-surface); font-weight: 500; font-size: 13px; display: flex; align-items: center; gap: 6px; cursor: pointer; white-space: nowrap; }\n" +
            ".pill-divider { width: 1px; height: 18px; background-color: var(--md-outline-variant); }\n" +
            ".pill-btn.active { color: var(--md-primary); }\n" +
            ".action-pill-btn { background-color: var(--service-tab-bg, rgba(255,255,255,0.1)); color: var(--text-color, #fff); height: 36px; line-height: 36px; padding: 0 18px; border-radius: 100px; font-size: 13px; font-weight: 500; display: inline-flex; align-items: center; text-decoration: none; flex-shrink: 0; border: none; cursor: pointer; white-space: nowrap; }\n" +
            ".action-pill-btn.danger { background-color: var(--md-error, #c00c0c); color: var(--md-on-error, #ffffff); }\n" +
            ".action-pill-btn.watch-later-btn.added { background-color: var(--md-primary); color: var(--md-on-primary); }\n" +
            ".action-pill-btn.disabled { opacity: 0.6; cursor: default; pointer-events: none; }\n" +
            // Consolidated from what used to be 3 separate ".settings-card" rules scattered
            // through this file, each overriding a piece of the last (border-radius was set to
            // 24px here, then 12px further down, then 16px again even further down in the MD3
            // refinement pass; box-shadow was set here then cancelled with !important later) -
            // these are the values that actually ended up winning the cascade, i.e. this changes
            // nothing about how the page renders, it just stops the 3-way tug-of-war.
            ".settings-card { background-color: var(--settings-card-bg); padding: 24px; border-radius: 16px; border: 1px solid var(--settings-card-border); box-shadow: none; min-width: 0; overflow: hidden; word-break: break-word; overflow-wrap: break-word; max-width: 600px; margin: 0 auto; }\n" +
            // Control-bar theming (play button/control-bar/progress-bar colors) was deliberately
            // dropped - video.js keeps its own default control colors here rather than following
            // the app's dynamic accent, matching the fullscreen fit fix and SponsorBlock markers
            // below (which ARE kept) rather than the rest of the accent-color treatment.
            ".video-js { font-family: inherit; border-radius: 16px; overflow: hidden; }\n" +
            // SponsorBlock segment markers - JS-created (position/width are per-video, computed
            // once the player knows its duration) inside .vjs-progress-holder, which video.js's own
            // CDN CSS already gives position:relative via the shared .vjs-slider class, so these
            // just need position:absolute to anchor correctly. Colors match SponsorBlock's own
            // established category colors (the same ones the browser extension uses), so anyone
            // already familiar with SponsorBlock recognizes them here.
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
            // Skip button - same "appear during a segment" role as YouTube's own skip-ad button.
            // Hidden via opacity/pointer-events (not display:none) so the appear/disappear can
            // transition instead of popping; bottom offset clears both the normal 48px and
            // fullscreen 64px control bar heights with room to spare.
            ".sponsor-skip-btn { position: absolute; right: 16px; bottom: 76px; background: rgba(29,27,32,0.9); color: #fff; border: none; padding: 10px 16px; border-radius: 8px; font-size: 14px; font-weight: 600; cursor: pointer; display: inline-flex; align-items: center; gap: 6px; z-index: 10; opacity: 0; pointer-events: none; transform: translateY(8px); transition: opacity 0.2s var(--md-easing), transform 0.2s var(--md-easing), background-color 0.2s; box-shadow: 0 2px 8px rgba(0,0,0,0.3); }\n" +
            ".sponsor-skip-btn:hover { background: rgba(45,42,54,0.95); }\n" +
            ".sponsor-skip-btn.visible { opacity: 1; pointer-events: auto; transform: translateY(0); }\n" +
            ".video-js.vjs-fullscreen .vjs-control-bar { height: 64px !important; padding: 0 48px !important; font-size: 16px !important; }\n" +
            ".video-js.vjs-fullscreen .vjs-button { font-size: 22px !important; width: 56px !important; }\n" +
            ".video-js.vjs-fullscreen .vjs-time-control { font-size: 14px !important; line-height: 64px !important; }\n" +
            // Shared native-<select> styling, first used by the watch page's quality/audio-track
            // pickers (previously each carried this same block as a copy-pasted inline style
            // attribute). appearance:none strips the browser's own dropdown arrow so a themed
            // Material Symbols chevron can sit over it instead - a plain CSS background-image
            // SVG would have been simpler, but its color would be a fixed hex baked into the
            // image data, unable to follow the device's dynamic theme the way every other icon
            // in this app does; a real glyph in a positioned span inherits color like any other
            // themed element. Only the closed (unopened) state is themeable this way - the
            // expanded option list is native browser/OS chrome outside CSS's reach.
            ".md-select-wrap { position: relative; display: inline-flex; align-items: center; }\n" +
            ".md-select { appearance: none; -webkit-appearance: none; -moz-appearance: none; padding: 6px 32px 6px 12px; border-radius: 6px; border: 1px solid var(--search-input-border); background-color: var(--card-bg); color: var(--text-color); font-family: inherit; font-size: 13px; cursor: pointer; }\n" +
            ".md-select-arrow { position: absolute; right: 8px; font-size: 18px; color: var(--md-on-surface-variant); pointer-events: none; }\n" +
            ".channel-card-avatar { width: 48px !important; height: 48px !important; min-width: 48px !important; min-height: 48px !important; border-radius: 50% !important; object-fit: cover !important; aspect-ratio: 1 / 1 !important; flex-shrink: 0; font-size: 18px; background-color: var(--service-tab-bg, #2b2930); }\n" +
            "body.pip-mode header, body.pip-mode .sidebar-nav, body.pip-mode .bottom-nav, body.pip-mode .media-info, body.pip-mode .comments-section, body.pip-mode .sidebar { display: none !important; }\n" +
            "body.pip-mode .container { margin: 0 !important; padding: 0 !important; max-width: 100% !important; margin-top: 0 !important; }\n" +
            "body.pip-mode .player-container { margin-top: 0 !important; }\n" +
            "body.pip-mode .native-player, body.pip-mode .video-js { height: 100vh !important; width: 100vw !important; border-radius: 0 !important; }\n" +
            // border is the MD3 pass's "read as a container, not a floating card" value, folded
            // in directly (that pass came later with no !important, so it already won).
            ".media-description { font-size: 14px; line-height: 1.5; color: var(--media-desc-color); white-space: pre-wrap; word-break: break-word; overflow-wrap: break-word; min-width: 0; background-color: var(--media-desc-bg); padding: 12px; border-radius: 12px; border: none; margin-top: 12px; }\n" +
            ".comments-section { padding-top: 16px; min-width: 0; }\n" +
            ".comment-count { font-size: 16px; font-weight: 500; letter-spacing: 0.15px; margin-bottom: 16px; color: var(--comment-count-color); }\n" +
            ".comment { display: flex; gap: 12px; margin-bottom: 16px; min-width: 0; }\n" +
            ".comment-avatar { width: 36px; height: 36px; border-radius: 50%; object-fit: cover; flex-shrink: 0; aspect-ratio: 1 / 1; background-color: var(--card-thumbnail-bg); }\n" +
            ".comment-details { display: flex; flex-direction: column; gap: 4px; min-width: 0; overflow: hidden; word-break: break-word; overflow-wrap: break-word; }\n" +
            ".comment-header { display: flex; gap: 8px; align-items: center; min-width: 0; }\n" +
            ".comment-author { font-size: 13px; font-weight: 500; color: var(--comment-author-color); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }\n" +
            ".comment-time { font-size: 12px; color: var(--comment-time-color); flex-shrink: 0; }\n" +
            ".comment-text { font-size: 14px; line-height: 1.4; color: var(--comment-text-color); white-space: pre-wrap; word-break: break-word; overflow-wrap: break-word; min-width: 0; }\n" +
            // Closer to YouTube's own comment chrome: a small verified checkmark riding the
            // author name, a pinned-comment label, an icon-led like count instead of plain "N
            // likes" text, and a heart badge for uploader-hearted comments - all driven by
            // CommentsInfoItem fields (isUploaderVerified/isPinned/isHeartedByUploader) that
            // existed in the extractor from the start but had nothing rendering them until now.
            ".comment-verified { font-size: 14px; color: var(--md-primary); vertical-align: middle; margin-left: 2px; }\n" +
            ".comment-pinned { display: flex; align-items: center; gap: 4px; font-size: 12px; font-weight: 500; color: var(--comment-time-color); }\n" +
            ".comment-pinned .material-symbols-rounded { font-size: 14px; }\n" +
            ".comment-meta { display: flex; align-items: center; gap: 12px; margin-top: 4px; }\n" +
            ".comment-like { display: flex; align-items: center; gap: 4px; font-size: 12px; color: var(--comment-time-color); }\n" +
            ".comment-like .material-symbols-rounded { font-size: 16px; }\n" +
            ".comment-hearted .material-symbols-rounded { font-size: 14px; color: #ff4b5c; }\n" +
            // Replies fetch through the exact same /comments endpoint as top-level pagination
            // (CommentsInfoItem.getReplies() is just another Page) - .comment-replies starts
            // empty and collapsed, filled in and expanded on first click, matching
            // window.toggleReplies in the watch page's inline script.
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
            // MD3 Tabs: flush underline indicator, not a filled pill - a thin outlineVariant
            // divider spans the whole row (the container's own border-bottom), and the active
            // tab's own 3dp border-bottom (same position, painted after/on top) picks up primary
            // color for just that tab's width. Previously hardcoded #0f0f0f/#ffffff per theme
            // instead of using --md-primary, the only place in the stylesheet not wired to the
            // device's dynamic color - unified with .subs-tab below to the same shared look.
            ".channel-tabs-selector { display: flex; border-top: 1px solid var(--media-info-border); border-bottom: 1px solid var(--md-outline-variant); padding: 0 16px; }\n" +
            ".channel-tab-btn { display: flex; align-items: center; padding: 12px 16px; font-size: 14px; font-weight: 500; color: var(--md-on-surface-variant); border-bottom: 3px solid transparent; cursor: pointer; text-decoration: none; transition: color 0.2s var(--md-easing), border-color 0.2s var(--md-easing); }\n" +
            ".channel-tab-btn:hover { color: var(--text-color); }\n" +
            ".channel-tab-btn.active { color: var(--md-primary); border-bottom-color: var(--md-primary); }\n" +
            // border/background-color are the MD3 pass's values (it used !important on both, so
            // they already always won regardless of source order), folded in directly.
            ".loading-placeholder { text-align: center; font-size: 15px; padding: 48px 16px; color: var(--card-meta-color); background-color: var(--md-surface-high); border-radius: 16px; border: none; margin: 16px 0; }\n" +
            ".m3-spinner { width: 40px; height: 40px; border: 4px solid var(--search-input-bg); border-top: 4px solid var(--logo-color); border-radius: 50%; animation: m3-spin 0.8s linear infinite; margin: 24px auto; }\n" +
            "@keyframes m3-spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }\n" +
            ".settings-title { font-size: 20px; font-weight: 700; margin-bottom: 24px; color: var(--settings-title-color); }\n" +
            ".settings-section { margin-bottom: 24px; padding-bottom: 20px; border-bottom: 1px solid var(--settings-section-border); }\n" +
            ".settings-section:last-child { border-bottom: none; }\n" +
            ".settings-section-title { font-size: 16px; font-weight: 500; margin-bottom: 12px; color: var(--settings-section-title-color); }\n" +
            // gap ensures a minimum breathing room between the label and its control even when
            // both are wide enough to have no natural slack left for justify-content:space-between
            // to distribute - confirmed live that the quality/home-feed-mode rows' <select> (which
            // has its own width:100% and greedily fills the remaining flex space) touched the label
            // text with zero gap on a 375px phone without this.
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
            // No :focus rule here on purpose: a hardcoded #1a73e8 border used to fire on every
            // focus (including mouse/touch clicks), both ignoring the device's theme and fighting
            // the app-wide focus system below (body.using-keyboard :focus-visible), which already
            // gives every focusable element a themed --md-primary outline for keyboard users only.
            // Falls through to that shared mechanism instead of special-casing this one element.
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
            /* Every selector here is compounded with .material-symbols-rounded, not just
             * .theme-icon-*: that class also sets "display: inline-block" and is defined later in
             * this stylesheet, so at equal specificity it would win by source order and show both
             * icons in both themes. The [data-theme="dark"] overrides need one extra compounded
             * class of specificity above the base pair for the same reason, regardless of order. */
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
            ".vjs-player-wrapper { position: relative; width: 100%; border-radius: 12px; overflow: hidden; background: #000; }\n" +
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
            "video, .video-js video { object-fit: fill !important; }\n" +
            // "fill" is fine in normal playback since the player box is already pinned to the
            // video's own aspect-ratio (16/9 inline style on the <video> tag), so there's nothing
            // for a mismatched box to stretch against. Real browser fullscreen replaces that box
            // with the device's actual screen shape instead, which usually is NOT 16:9 (typically
            // taller, ~19.5:9 on modern phones) - "fill" then stretches/distorts the picture to
            // match, and if `screen.orientation.lock('landscape')` (advancedJs's fullscreenchange
            // handler) silently fails for any reason - unsupported, denied, wrong element locked -
            // the mismatch gets much worse, since it's stretching 16:9 content into a tall portrait
            // box instead of a wide one. User reported this as the fullscreen picture being cropped
            // /not showing the complete frame. "contain" letterboxes instead (always shows the
            // whole frame, black bars fill the gap) regardless of whether the orientation lock
            // above succeeds - covers both DOM shapes video.js can produce (the tag that carries
            // "video-js" is itself the tech <video> for the cached/MP4 source path, or a wrapper
            // div around a separate child <video> for the DASH-manifest path).
            ".video-js.vjs-fullscreen, .video-js.vjs-fullscreen video { object-fit: contain !important; background-color: #000; }\n" +

            // Bilibili danmaku overlay. pointer-events:none throughout so it never blocks clicks
            // on the video or controls beneath/above it; placed early in vjs-player-wrapper's DOM
            // (right after <video>) so the double-tap/volume-hud/up-next overlays added later
            // naturally stack above it with no z-index juggling needed.
            ".danmaku-layer { position: absolute; top: 0; left: 0; right: 0; bottom: 0; overflow: hidden; pointer-events: none; }\n" +
            ".danmaku-item { position: absolute; white-space: nowrap; font-weight: 600; line-height: 1; text-shadow: 0 0 3px rgba(0,0,0,0.9), 0 0 6px rgba(0,0,0,0.6); will-change: transform; }\n" +
            ".danmaku-item.top, .danmaku-item.bottom { left: 50%; transform: translateX(-50%); }\n" +
            // Scroll-type items animate via a JS-driven `transition` (travel distance depends on
            // measured text/player width), so pausing them is handled in JS instead - this only
            // needs to freeze the CSS `animation` the top/bottom fade variants use.
            ".danmaku-layer.video-paused .danmaku-item.top, .danmaku-layer.video-paused .danmaku-item.bottom { animation-play-state: paused; }\n" +
            "@keyframes danmaku-fade { 0% { opacity: 0; } 10%, 85% { opacity: 1; } 100% { opacity: 0; } }\n" +
            ".danmaku-toggle-btn { display: inline-flex; align-items: center; justify-content: center; width: 32px; height: 32px; border-radius: 50%; border: none; cursor: pointer; background: rgba(124,58,237,0.15); color: var(--text-color); }\n" +
            ".danmaku-toggle-btn .material-symbols-rounded { font-size: 20px; }\n" +
            ".danmaku-toggle-btn.off { opacity: 0.45; }\n" +

            /* ---- Material 3 refinements -------------------------------------------------
             * Appended last so these win over the equivalent earlier rules. Three things the
             * page was missing: MD3 state layers (the 8%/12% onSurface overlay that gives every
             * interactive surface its feedback), correct filled-button colour roles, and the
             * flat list treatment MD3/YouTube use for video items instead of raised cards.
             * ---------------------------------------------------------------------------- */
            "* { -webkit-tap-highlight-color: transparent; }\n" +

            /* Material Symbols. One font means one optical size and one stroke weight for every
             * icon, which the previous hand-picked SVG paths could not guarantee. font-size drives
             * the glyph size, so opsz is matched to it to keep strokes optically correct.
             * 'display=block' avoids the brief flash of raw ligature text while the font loads. */
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
            // justify-content, not text-align: these are display:inline-flex, and text-align has
            // no effect on a flex container's own content - it only ever centers text within a
            // normal inline/block layout. That mismatch is why an earlier attempt at centering the
            // Watch History select-bar's button text (via text-align on a wrapper rule) silently
            // did nothing whenever a button ended up wider than its text, e.g. via flex:1.
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
