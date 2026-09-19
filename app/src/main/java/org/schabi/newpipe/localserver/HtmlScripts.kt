package org.schabi.newpipe.localserver

/**
 * The shared `<script>...</script>` block injected into every page by
 * `HtmlRenderer.wrapInTemplate`, split out purely to keep that file's size manageable -
 * this is a static string with a single consumer and no Java variable interpolation inside it, so
 * moving it here has no behavioural effect.
 */
object HtmlScripts {

    @JvmField
    val SCRIPTS: String =
                "    <script>\n" +
                "        (function() {\n" +
                "            const toggleBtn = document.getElementById('theme-toggle');\n" +
                "            if (toggleBtn) {\n" +
                "                toggleBtn.addEventListener('click', function() {\n" +
                "                    const currentTheme = document.documentElement.getAttribute('data-theme') || 'light';\n" +
                "                    const newTheme = currentTheme === 'dark' ? 'light' : 'dark';\n" +
                "                    document.documentElement.setAttribute('data-theme', newTheme);\n" +
                "                    localStorage.setItem('theme', newTheme);\n" +
                "                });\n" +
                "            }\n" +
                "        })();\n" +
                "        \n" +
                "        (function() {\n" +
                "            function centerSearchBar() {\n" +
                "                var bar = document.querySelector('.top-bar');\n" +
                "                var form = document.querySelector('.search-form');\n" +
                "                if (!bar || !form || window.innerWidth < 769) return;\n" +
                "                var kids = bar.children;\n" +
                "                var left = kids[0];\n" +
                "                var right = kids[kids.length - 1];\n" +
                "                var maxSide = Math.max(left.getBoundingClientRect().width, right.getBoundingClientRect().width);\n" +
                "                var available = bar.clientWidth - 2 * maxSide - 64;\n" +
                "                form.style.width = Math.max(160, Math.min(640, available)) + 'px';\n" +
                "            }\n" +
                "            centerSearchBar();\n" +
                "            window.addEventListener('resize', centerSearchBar);\n" +
                "        })();\n" +
                "        \n" +
                "        function updateTVLockBanner() {\n" +
                "            const banner = document.getElementById('tv-lock-banner');\n" +
                "            if (!banner) return;\n" +
                "            const code = localStorage.getItem('server_play_release_code');\n" +
                "            if (code) {\n" +
                "                banner.style.display = 'flex';\n" +
                "                document.body.classList.add('has-banner');\n" +
                "            } else {\n" +
                "                banner.style.display = 'none';\n" +
                "                document.body.classList.remove('has-banner');\n" +
                "            }\n" +
                "        }\n" +
                "        \n" +
                "        function playOnTV(videoUrl, title) {\n" +
                "            const isVideoPage = location.pathname.startsWith('/watch') || location.pathname.startsWith('/audio');\n" +
                "            const idToSend = isVideoPage ? videoUrl : '__connect_only__';\n" +
                "            const code = localStorage.getItem('server_play_release_code') || '';\n" +
                "            const url = '/send-link?id=' + encodeURIComponent(idToSend) + \n" +
                "                        '&release_code=' + encodeURIComponent(code) +\n" +
                "                        '&title=' + encodeURIComponent(title);\n" +
                "            \n" +
                "            fetch(url)\n" +
                "                .then(res => res.json())\n" +
                "                .then(data => {\n" +
                "                    if (data.status === 'success') {\n" +
                "                        localStorage.setItem('server_play_release_code', data.release_code);\n" +
                "                        updateTVLockBanner();\n" +
                "                        startCommandPolling();\n" +
                "                        alert('Successfully connected remote!');\n" +
                "                    } else if (data.status === 'busy') {\n" +
                "                        alert('Server is busy: ' + data.message);\n" +
                "                    } else {\n" +
                "                        alert('Error casting video: ' + (data.message || 'Unknown error'));\n" +
                "                    }\n" +
                "                })\n" +
                "                .catch(err => {\n" +
                "                    alert('Connection error: ' + err);\n" +
                "                });\n" +
                "        }\n" +
                "        \n" +
                "        function showToast(msg) {\n" +
                "            let t = document.getElementById('app-toast');\n" +
                "            if (!t) {\n" +
                "                t = document.createElement('div');\n" +
                "                t.id = 'app-toast';\n" +
                "                t.style.cssText = 'position:fixed;bottom:80px;left:50%;transform:translateX(-50%);background:rgba(28,27,31,0.92);color:#e6e1e5;padding:10px 20px;border-radius:24px;font-size:14px;font-weight:500;z-index:999999;transition:opacity 0.3s ease, transform 0.3s ease;pointer-events:none;box-shadow:0 4px 16px rgba(0,0,0,0.4);border:1px solid rgba(255,255,255,0.1);font-family:Roboto,sans-serif;';\n" +
                "                document.body.appendChild(t);\n" +
                "            }\n" +
                "            t.innerText = msg;\n" +
                "            t.style.opacity = '1';\n" +
                "            t.style.transform = 'translateX(-50%) translateY(0)';\n" +
                "            clearTimeout(t._timer);\n" +
                "            t._timer = setTimeout(() => {\n" +
                "                t.style.opacity = '0';\n" +
                "                t.style.transform = 'translateX(-50%) translateY(10px)';\n" +
                "            }, 2200);\n" +
                "        }\n" +
                "        \n" +
                "        function openShareModal(url, title) {\n" +
                "            const fullUrl = (url && url.startsWith('http')) ? url : ('https://www.youtube.com/watch?v=' + (url || ''));\n" +
                "            let overlay = document.getElementById('share-modal-overlay');\n" +
                "            if (!overlay) {\n" +
                "                overlay = document.createElement('div');\n" +
                "                overlay.id = 'share-modal-overlay';\n" +
                "                overlay.onclick = function(e) { if (e.target === overlay) closeShareModal(); };\n" +
                "                overlay.innerHTML = \n" +
                "                    '<div class=\"share-modal-card\">' +\n" +
                "                    '  <div class=\"share-modal-header\">' +\n" +
                "                    '    <span class=\"share-modal-title\">Share</span>' +\n" +
                "                    '    <button class=\"share-modal-close\" onclick=\"closeShareModal()\">&times;</button>' +\n" +
                "                    '  </div>' +\n" +
                "                    '  <div class=\"share-link-box\">' +\n" +
                "                    '    <input type=\"text\" id=\"share-input-url\" class=\"share-link-input\" readonly>' +\n" +
                "                    '    <button id=\"share-copy-btn\" class=\"share-copy-btn\" onclick=\"copyShareInputUrl()\">Copy</button>' +\n" +
                "                    '  </div>' +\n" +
                "                    '  <div class=\"share-grid\">' +\n" +
                "                    '    <a id=\"share-wa\" class=\"share-item\" target=\"_blank\" rel=\"noopener\">' +\n" +
                "                    '      <div class=\"share-icon-btn\" style=\"background:#25D366;\"><svg viewBox=\"0 0 24 24\" fill=\"currentColor\" width=\"22\" height=\"22\"><path d=\"M12.04 2c-5.46 0-9.91 4.45-9.91 9.91 0 1.75.46 3.45 1.32 4.95L2.05 22l5.25-1.38c1.45.79 3.08 1.21 4.74 1.21 5.46 0 9.91-4.45 9.91-9.91 0-2.65-1.03-5.14-2.9-7.01A9.84 9.84 0 0012.04 2zm.01 1.67c4.55 0 8.24 3.69 8.24 8.24 0 2.2-.86 4.27-2.42 5.82a8.19 8.19 0 01-5.82 2.42c-1.47 0-2.91-.39-4.17-1.14l-.3-.18-3.1 1.18 1.18-3.04-.19-.31A8.2 8.2 0 013.8 11.91c0-4.55 3.69-8.24 8.24-8.24z\"/></svg></div>' +\n" +
                "                    '      <span>WhatsApp</span>' +\n" +
                "                    '    </a>' +\n" +
                "                    '    <a id=\"share-tg\" class=\"share-item\" target=\"_blank\" rel=\"noopener\">' +\n" +
                "                    '      <div class=\"share-icon-btn\" style=\"background:#0088cc;\"><svg viewBox=\"0 0 24 24\" fill=\"currentColor\" width=\"22\" height=\"22\"><path d=\"M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm4.64 6.8c-.15 1.58-.8 5.42-1.13 7.19-.14.75-.42 1-.68 1.03-.58.05-1.02-.38-1.58-.75-.88-.58-1.38-.94-2.23-1.5-.99-.65-.35-1.01.22-1.59.15-.15 2.71-2.48 2.76-2.69.01-.03.01-.14-.07-.2-.08-.06-.19-.04-.27-.02-.12.02-1.96 1.25-5.54 3.67-.52.36-1 .53-1.42.52-.47-.01-1.37-.26-2.03-.48-.82-.27-1.47-.42-1.42-.88.03-.25.38-.51 1.07-.78 4.18-1.82 6.97-3.02 8.37-3.6 3.98-1.65 4.81-1.94 5.35-1.95.12 0 .38.03.55.17.14.12.18.28.2.46-.01.07.01.25 0 .37z\"/></svg></div>' +\n" +
                "                    '      <span>Telegram</span>' +\n" +
                "                    '    </a>' +\n" +
                "                    '    <a id=\"share-tw\" class=\"share-item\" target=\"_blank\" rel=\"noopener\">' +\n" +
                "                    '      <div class=\"share-icon-btn\" style=\"background:#000000;\"><svg viewBox=\"0 0 24 24\" fill=\"currentColor\" width=\"20\" height=\"20\"><path d=\"M18.244 2.25h3.308l-7.227 8.26 8.502 11.24H16.17l-5.214-6.817L4.99 21.75H1.68l7.73-8.835L1.254 2.25H8.08l4.713 6.231zm-1.161 17.52h1.833L7.084 4.126H5.117z\"/></svg></div>' +\n" +
                "                    '      <span>X</span>' +\n" +
                "                    '    </a>' +\n" +
                "                    '    <a id=\"share-em\" class=\"share-item\">' +\n" +
                "                    '      <div class=\"share-icon-btn\" style=\"background:#ea4335;\"><svg viewBox=\"0 0 24 24\" fill=\"currentColor\" width=\"22\" height=\"22\"><path d=\"M20 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 4l-8 5-8-5V6l8 5 8-5v2z\"/></svg></div>' +\n" +
                "                    '      <span>Email</span>' +\n" +
                "                    '    </a>' +\n" +
                "                    '  </div>' +\n" +
                "                    '</div>';\n" +
                "                document.body.appendChild(overlay);\n" +
                "            }\n" +
                "            const input = document.getElementById('share-input-url');\n" +
                "            if (input) input.value = fullUrl;\n" +
                "            const copyBtn = document.getElementById('share-copy-btn');\n" +
                "            if (copyBtn) {\n" +
                "                copyBtn.innerText = 'Copy';\n" +
                "                copyBtn.style.background = 'var(--logo-color, #7c3aed)';\n" +
                "            }\n" +
                "            const encUrl = encodeURIComponent(fullUrl);\n" +
                "            const encTitle = encodeURIComponent(title || 'Fathom Video');\n" +
                "            const wa = document.getElementById('share-wa'); if (wa) wa.href = 'https://api.whatsapp.com/send?text=' + encTitle + '%20' + encUrl;\n" +
                "            const tg = document.getElementById('share-tg'); if (tg) tg.href = 'https://t.me/share/url?url=' + encUrl + '&text=' + encTitle;\n" +
                "            const tw = document.getElementById('share-tw'); if (tw) tw.href = 'https://twitter.com/intent/tweet?text=' + encTitle + '&url=' + encUrl;\n" +
                "            const em = document.getElementById('share-em'); if (em) em.href = 'mailto:?subject=' + encTitle + '&body=' + encUrl;\n" +
                "            const bindClick = (id) => {\n" +
                "                const el = document.getElementById(id);\n" +
                "                if (el) {\n" +
                "                    el.onclick = function(e) {\n" +
                "                        if (window.NewPipeApp && window.NewPipeApp.openExternalUrl) {\n" +
                "                            e.preventDefault();\n" +
                "                            window.NewPipeApp.openExternalUrl(this.href);\n" +
                "                        }\n" +
                "                    };\n" +
                "                }\n" +
                "            };\n" +
                "            bindClick('share-wa'); bindClick('share-tg'); bindClick('share-tw'); bindClick('share-em');\n" +
                "            overlay.classList.add('active');\n" +
                "        }\n" +
                "        \n" +
                "        function closeShareModal() {\n" +
                "            const overlay = document.getElementById('share-modal-overlay');\n" +
                "            if (overlay) overlay.classList.remove('active');\n" +
                "        }\n" +
                "        \n" +
                "        function copyShareInputUrl() {\n" +
                "            const input = document.getElementById('share-input-url');\n" +
                "            const copyBtn = document.getElementById('share-copy-btn');\n" +
                "            if (input && input.value) {\n" +
                "                const markSuccess = () => {\n" +
                "                    if (copyBtn) {\n" +
                "                        copyBtn.innerText = 'Copied!';\n" +
                "                        copyBtn.style.background = '#2e7d32';\n" +
                "                    }\n" +
                "                    showToast('Link copied to clipboard');\n" +
                "                };\n" +
                "                if (navigator.clipboard && navigator.clipboard.writeText) {\n" +
                "                    navigator.clipboard.writeText(input.value).then(markSuccess).catch(() => {\n" +
                "                        input.select();\n" +
                "                        document.execCommand('copy');\n" +
                "                        markSuccess();\n" +
                "                    });\n" +
                "                } else {\n" +
                "                    input.select();\n" +
                "                    document.execCommand('copy');\n" +
                "                    markSuccess();\n" +
                "                }\n" +
                "            }\n" +
                "        }\n" +
                "        \n" +
                "        function shareLink(url, title) {\n" +
                "            openShareModal(url, title);\n" +
                "        }\n" +
                "        \n" +
                "        function startCommandPolling() {\n" +
                "            if (window.wsConnection) return;\n" +
                "            \n" +
                "            // Virtual cursor state\n" +
                "            let vptrX = window.innerWidth / 2, vptrY = window.innerHeight / 2;\n" +
                "            const vptr = document.getElementById('vptr');\n" +
                "            function showVptr() {\n" +
                "                if (vptr) { vptr.style.display = 'block'; vptr.style.left = vptrX + 'px'; vptr.style.top = vptrY + 'px'; }\n" +
                "            }\n" +
                "            function moveVptr(dx, dy) {\n" +
                "                vptrX = Math.max(0, Math.min(window.innerWidth, vptrX + dx));\n" +
                "                vptrY = Math.max(0, Math.min(window.innerHeight, vptrY + dy));\n" +
                "                if (vptr) { vptr.style.left = vptrX + 'px'; vptr.style.top = vptrY + 'px'; }\n" +
                "                const el = document.elementFromPoint(vptrX, vptrY);\n" +
                "                if (el) {\n" +
                "                    const isOverPlayer = el.closest('#video-container') !== null;\n" +
                "                    const controls = document.getElementById('video-controls');\n" +
                "                    if (controls) {\n" +
                "                        controls.style.opacity = isOverPlayer ? '1' : '0';\n" +
                "                        controls.style.pointerEvents = isOverPlayer ? 'auto' : 'none';\n" +
                "                    }\n" +
                "                }\n" +
                "            }\n" +
                "            function clickVptr() {\n" +
                "                if (vptr) vptr.style.transform = 'translate(-50%,-50%) scale(0.7)';\n" +
                "                setTimeout(() => { if (vptr) vptr.style.transform = 'translate(-50%,-50%) scale(1)'; }, 150);\n" +
                "                const el = document.elementFromPoint(vptrX, vptrY);\n" +
                "                if (!el || el === vptr) return;\n" +
                "                el.click();\n" +
                "                const anchor = el.tagName === 'A' ? el : el.closest('a');\n" +
                "                if (anchor && anchor.href && !anchor.href.startsWith('javascript')) {\n" +
                "                    window.location.href = anchor.href;\n" +
                "                }\n" +
                "            }\n" +
                "            \n" +
                "            window.playVideoSPA = function(url) {\n" +
                "                history.pushState(null, '', '/watch?serviceId=0&id=' + encodeURIComponent(url));\n" +
                "                let container = document.querySelector('.container');\n" +
                "                if (container) {\n" +
                "                    container.outerHTML = '<div id=\"watch-container-loader\" style=\"text-align: center; padding: 100px 0; font-family: inherit;\">' +\n" +
                "                      '  <div style=\"display: inline-block; width: 60px; height: 60px; border: 4px solid var(--md-state-hover, rgba(124, 58, 237, 0.1)); border-top: 4px solid var(--md-primary, #7c3aed); border-radius: 50%; animation: spin 1.5s linear infinite;\"></div>' +\n" +
                "                      '  <div style=\"margin-top: 24px; font-size: 16px; font-weight: 500; color: var(--text-color);\">Loading video streams...</div>' +\n" +
                "                      '</div>' +\n" +
                "                      '<div id=\"watch-content\" style=\"display: none;\"></div>';\n" +
                "                } else {\n" +
                "                    window.location.href = '/watch?serviceId=0&id=' + encodeURIComponent(url);\n" +
                "                    return;\n" +
                "                }\n" +
                "                if (!document.getElementById('spa-spin-style')) {\n" +
                "                    const style = document.createElement('style');\n" +
                "                    style.id = 'spa-spin-style';\n" +
                "                    style.innerHTML = '@keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }';\n" +
                "                    document.head.appendChild(style);\n" +
                "                }\n" +
                "                const loader = document.getElementById('watch-container-loader');\n" +
                "                const content = document.getElementById('watch-content');\n" +
                // Same serviceId forwarding as the non-SPA path above.
                "                fetch('/watch-content?serviceId=' + encodeURIComponent(new URLSearchParams(window.location.search).get('serviceId') || '0') + '&id=' + encodeURIComponent(url))\n" +
                "                    .then(res => {\n" +
                "                        if (!res.ok) throw new Error('HTTP ' + res.status);\n" +
                "                        return res.text();\n" +
                "                    })\n" +
                "                    .then(html => {\n" +
                "                        if (loader) loader.remove();\n" +
                "                        if (content) {\n" +
                "                            content.style.display = 'block';\n" +
                "                            content.outerHTML = html;\n" +
                "                            const newContainer = document.querySelector('.container');\n" +
                "                            if (newContainer) {\n" +
                "                                newContainer.querySelectorAll('script').forEach(oldScript => {\n" +
                "                                    const newScript = document.createElement('script');\n" +
                "                                    Array.from(oldScript.attributes).forEach(attr => newScript.setAttribute(attr.name, attr.value));\n" +
                "                                    newScript.appendChild(document.createTextNode(oldScript.innerHTML));\n" +
                "                                    oldScript.parentNode.replaceChild(newScript, oldScript);\n" +
                "                                });\n" +
                "                            }\n" +
                "                        }\n" +
                "                    })\n" +
                "                    .catch(err => {\n" +
                "                        if (loader) loader.remove();\n" +
                "                        const errDiv = document.createElement('div');\n" +
                "                        errDiv.className = 'loading-placeholder';\n" +
                "                        errDiv.style.color = '#ff4b5c';\n" +
                "                        errDiv.style.borderColor = 'rgba(255, 75, 92, 0.2)';\n" +
                "                        errDiv.innerText = 'Failed to load video: ' + err.message;\n" +
                "                        document.body.appendChild(errDiv);\n" +
                "                    });\n" +
                "            };\n" +
                "            \n" +
                "            window.wsConnection = new WebSocket('ws://' + location.hostname + ':8081');\n" +
                "            window.wsConnection.onmessage = function(event) {\n" +
                "                const cmd = event.data;\n" +
                "                if (cmd.startsWith('play_video:')) {\n" +
                "                    const url = cmd.substring('play_video:'.length);\n" +
                "                    window.playVideoSPA(url);\n" +
                "                } else if (cmd.startsWith('pointer_move:')) {\n" +
                "                    const parts = cmd.substring('pointer_move:'.length).split(',');\n" +
                "                    const dx = parseFloat(parts[0]) || 0;\n" +
                "                    const dy = parseFloat(parts[1]) || 0;\n" +
                "                    showVptr();\n" +
                "                    moveVptr(dx, dy);\n" +
                "                } else if (cmd === 'pointer_click') {\n" +
                "                    showVptr();\n" +
                "                    clickVptr();\n" +
                "                } else if (cmd.startsWith('pointer_scroll:')) {\n" +
                "                    const dy = parseFloat(cmd.substring('pointer_scroll:'.length)) || 0;\n" +
                "                    window.scrollBy({ top: dy, behavior: 'smooth' });\n" +
                "                } else if (cmd === 'back') {\n" +
                "                    if (window.history.length > 1) window.history.back();\n" +
                "                } else if (cmd === 'play_pause') {\n" +
                "                    if (window.videoPlayer) {\n" +
                "                        if (window.videoPlayer.paused) {\n" +
                "                            window.videoPlayer.play().catch(e => {});\n" +
                "                        } else {\n" +
                "                            window.videoPlayer.pause();\n" +
                "                        }\n" +
                "                    } else {\n" +
                "                        const media = document.getElementById('player') || document.getElementById('audio-player');\n" +
                "                        if (media) {\n" +
                "                            if (media.readyState < 2) {\n" +
                "                                const loader = document.getElementById('video-loader');\n" +
                "                                if (loader) loader.style.display = 'block';\n" +
                "                                media.play().catch(e => {});\n" +
                "                            } else {\n" +
                "                                if (media.paused) media.play().catch(e => {}); else media.pause();\n" +
                "                            }\n" +
                "                        }\n" +
                "                    }\n" +
                "                } else if (cmd === 'forward') {\n" +
                "                    if (typeof window.seekVideo === 'function') window.seekVideo(10);\n" +
                "                    else {\n" +
                "                        const media = document.getElementById('player') || document.getElementById('audio-player');\n" +
                "                        if (media) media.currentTime += 10;\n" +
                "                    }\n" +
                "                } else if (cmd === 'rewind') {\n" +
                "                    if (typeof window.seekVideo === 'function') window.seekVideo(-10);\n" +
                "                    else {\n" +
                "                        const media = document.getElementById('player') || document.getElementById('audio-player');\n" +
                "                        if (media) media.currentTime -= 10;\n" +
                "                    }\n" +
                "                }\n" +
                "            };\n" +
                "            window.wsConnection.onclose = function() {\n" +
                "                window.wsConnection = null;\n" +
                "                setTimeout(() => {\n" +
                "                    if (localStorage.getItem('server_play_release_code')) {\n" +
                "                        startCommandPolling();\n" +
                "                    }\n" +
                "                }, 1000);\n" +
                "            };\n" +
                "        }\n" +
                "        \n" +
                "        function releaseTVLock() {\n" +
                "            const code = localStorage.getItem('server_play_release_code');\n" +
                "            if (!code) return;\n" +
                "            \n" +
                "            fetch('/release-lock?release_code=' + encodeURIComponent(code))\n" +
                "                .then(res => res.json())\n" +
                "                .then(data => {\n" +
                "                    localStorage.removeItem('server_play_release_code');\n" +
                "                    updateTVLockBanner();\n" +
                "                    if (window.wsConnection) {\n" +
                "                        window.wsConnection.close();\n" +
                "                        window.wsConnection = null;\n" +
                "                    }\n" +
                "                })\n" +
                "                .catch(err => {\n" +
                "                    localStorage.removeItem('server_play_release_code');\n" +
                "                    updateTVLockBanner();\n" +
                "                    if (window.wsConnection) {\n" +
                "                        window.wsConnection.close();\n" +
                "                        window.wsConnection = null;\n" +
                "                    }\n" +
                "                    alert('Connection error/released locally: ' + err);\n" +
                "                });\n" +
                "        }\n" +
                "        \n" +
                // Drives the keyboard-only focus ring (see the :focus rules in the stylesheet).
                // Registered outside DOMContentLoaded so the very first Tab is caught.
                "        document.addEventListener('keydown', (e) => {\n" +
                "            if (e.key === 'Tab' || e.key.indexOf('Arrow') === 0) document.body.classList.add('using-keyboard');\n" +
                "        }, true);\n" +
                "        document.addEventListener('pointerdown', () => document.body.classList.remove('using-keyboard'), true);\n" +
                // The sticky mobile highlight is a UA-drawn ring, not this stylesheet's (grey, not
                // the reported purple) - can't be removed via CSS, so blur after a pointer click
                // instead. Keyboard activation excluded so focus isn't stolen from keyboard users.
                "        document.addEventListener('click', (e) => {\n" +
                "            if (document.body.classList.contains('using-keyboard')) return;\n" +
                "            const el = e.target && e.target.closest && e.target.closest('a, button');\n" +
                "            if (el && typeof el.blur === 'function') el.blur();\n" +
                "        }, true);\n" +
                "        \n" +
                "        document.addEventListener('DOMContentLoaded', () => {\n" +
                "            updateTVLockBanner();\n" +
                "            \n" +
                "            window.getFocusableElements = () => {\n" +
                "                const selector = 'a, button, input, select, textarea, [tabindex=\"0\"]';\n" +
                "                return Array.from(document.querySelectorAll(selector)).filter(el => {\n" +
                "                    const rect = el.getBoundingClientRect();\n" +
                "                    return rect.width > 0 && rect.height > 0 && \n" +
                "                           window.getComputedStyle(el).display !== 'none' &&\n" +
                "                           window.getComputedStyle(el).visibility !== 'hidden';\n" +
                "                });\n" +
                "            };\n" +
                "            \n" +
                "            window.focusNext = (reverse = false) => {\n" +
                "                const els = window.getFocusableElements();\n" +
                "                if (els.length === 0) return;\n" +
                "                const active = document.activeElement;\n" +
                "                let idx = els.indexOf(active);\n" +
                "                if (idx === -1) {\n" +
                "                    els[0].focus();\n" +
                "                    return;\n" +
                "                }\n" +
                "                if (reverse) {\n" +
                "                    idx = (idx - 1 + els.length) % els.length;\n" +
                "                } else {\n" +
                "                    idx = (idx + 1) % els.length;\n" +
                "                }\n" +
                "                els[idx].focus();\n" +
                "                els[idx].scrollIntoView({ behavior: 'smooth', block: 'center' });\n" +
                "            };\n" +
                "            \n" +
                "            window.focusVertical = (down = true) => {\n" +
                "                const els = window.getFocusableElements();\n" +
                "                if (els.length === 0) return;\n" +
                "                const active = document.activeElement;\n" +
                "                let idx = els.indexOf(active);\n" +
                "                if (idx === -1) {\n" +
                "                    els[0].focus();\n" +
                "                    return;\n" +
                "                }\n" +
                "                \n" +
                "                let cols = 1;\n" +
                "                const firstRect = els[0].getBoundingClientRect();\n" +
                "                for (let i = 1; i < els.length; i++) {\n" +
                "                    const r = els[i].getBoundingClientRect();\n" +
                "                    if (Math.abs(r.top - firstRect.top) < 15) {\n" +
                "                        cols++;\n" +
                "                    } else {\n" +
                "                        break;\n" +
                "                    }\n" +
                "                }\n" +
                "                \n" +
                "                const step = down ? cols : -cols;\n" +
                "                let newIdx = idx + step;\n" +
                "                if (newIdx < 0) newIdx = 0;\n" +
                "                if (newIdx >= els.length) newIdx = els.length - 1;\n" +
                "                \n" +
                "                els[newIdx].focus();\n" +
                "                els[newIdx].scrollIntoView({ behavior: 'smooth', block: 'center' });\n" +
                "            };\n" +
                "            \n" +
                "            const initFocusable = () => {\n" +
                "                document.querySelectorAll('.card').forEach(card => {\n" +
                "                    if (!card.hasAttribute('tabindex')) {\n" +
                "                        card.setAttribute('tabindex', '0');\n" +
                "                    }\n" +
                "                });\n" +
                "            };\n" +
                "            initFocusable();\n" +
                "            \n" +
                "            const defaultFocus = () => {\n" +
                "                const els = window.getFocusableElements ? window.getFocusableElements() : [];\n" +
                "                if (els.length > 0) {\n" +
                "                    els[0].focus();\n" +
                "                }\n" +
                "            };\n" +
                "            setTimeout(defaultFocus, 200);\n" +
                "            \n" +
                "            // Global keyboard keydown handler to replace Arrow keys with Tab / Shift-Tab linear focus cycle\n" +
                "            document.addEventListener('keydown', (e) => {\n" +
                "                const active = document.activeElement;\n" +
                "                if (active && (active.tagName === 'INPUT' || active.tagName === 'SELECT' || active.tagName === 'TEXTAREA' || active.isContentEditable)) {\n" +
                "                    return; // Let native typing handle arrows inside inputs\n" +
                "                }\n" +
                "                if (e.key === 'ArrowLeft') {\n" +
                "                    e.preventDefault();\n" +
                "                    const player = document.getElementById('player') || document.getElementById('audio-player');\n" +
                "                    if (player) {\n" +
                "                        if (typeof window.seekVideo === 'function') window.seekVideo(-10);\n" +
                "                        else player.currentTime = Math.max(0, player.currentTime - 10);\n" +
                "                    } else {\n" +
                "                        if (window.focusNext) window.focusNext(true);\n" +
                "                    }\n" +
                "                } else if (e.key === 'ArrowRight') {\n" +
                "                    e.preventDefault();\n" +
                "                    const player = document.getElementById('player') || document.getElementById('audio-player');\n" +
                "                    if (player) {\n" +
                "                        if (typeof window.seekVideo === 'function') window.seekVideo(10);\n" +
                "                        else player.currentTime = Math.min(player.duration || 0, player.currentTime + 10);\n" +
                "                    } else {\n" +
                "                        if (window.focusNext) window.focusNext(false);\n" +
                "                    }\n" +
                "                } else if (e.key === 'ArrowUp') {\n" +
                "                    e.preventDefault();\n" +
                "                    if (window.focusVertical) window.focusVertical(false);\n" +
                "                } else if (e.key === 'ArrowDown') {\n" +
                "                    e.preventDefault();\n" +
                "                    if (window.focusVertical) window.focusVertical(true);\n" +
                "                } else if (e.key === 'Enter') {\n" +
                "                    if (active) {\n" +
                "                        active.click();\n" +
                "                        const anchor = active.tagName === 'A' ? active : active.querySelector('a');\n" +
                "                        if (anchor && anchor.href) {\n" +
                "                            window.location.href = anchor.href;\n" +
                "                        }\n" +
                "                    }\n" +
                "                } else if (e.key === 'Backspace' || e.key === 'Escape') {\n" +
                "                    if (window.history.length > 1) {\n" +
                "                        e.preventDefault();\n" +
                "                        window.history.back();\n" +
                "                    }\n" +
                "                }\n" +
                "            });\n" +
                "            \n" +
                "            // Resume command polling if we are currently connected/locked\n" +
                "            if (localStorage.getItem('server_play_release_code')) {\n" +
                "                startCommandPolling();\n" +
                "            }\n" +
                "            \n" +
                "            const settingsThemeToggle = document.getElementById('settings-theme-toggle');\n" +
                "            if (settingsThemeToggle) {\n" +
                "                const currentTheme = document.documentElement.getAttribute('data-theme') || 'light';\n" +
                "                settingsThemeToggle.checked = (currentTheme === 'dark');\n" +
                "                settingsThemeToggle.addEventListener('change', function() {\n" +
                "                    const newTheme = settingsThemeToggle.checked ? 'dark' : 'light';\n" +
                "                    document.documentElement.setAttribute('data-theme', newTheme);\n" +
                "                    localStorage.setItem('theme', newTheme);\n" +
                "                });\n" +
                "            }\n" +
                "            \n" +
                "            const settingsAccentSelect = document.getElementById('settings-accent-select');\n" +
                "            if (settingsAccentSelect) {\n" +
                "                const currentAccent = localStorage.getItem('theme-color') || 'system';\n" +
                "                settingsAccentSelect.value = currentAccent;\n" +
                "                settingsAccentSelect.addEventListener('change', function() {\n" +
                "                    const newAccent = settingsAccentSelect.value;\n" +
                "                    document.documentElement.setAttribute('data-theme-color', newAccent);\n" +
                "                    localStorage.setItem('theme-color', newAccent);\n" +
                "                });\n" +
                "            }\n" +
                "            \n" +
                "            const settingsPureBlackToggle = document.getElementById('settings-pureblack-toggle');\n" +
                "            if (settingsPureBlackToggle) {\n" +
                "                settingsPureBlackToggle.checked = (localStorage.getItem('pure-black') === 'true');\n" +
                "                settingsPureBlackToggle.addEventListener('change', function() {\n" +
                "                    const isPureBlack = settingsPureBlackToggle.checked;\n" +
                "                    document.documentElement.setAttribute('data-pure-black', isPureBlack);\n" +
                "                    localStorage.setItem('pure-black', isPureBlack ? 'true' : 'false');\n" +
                "                });\n" +
                "            }\n" +
                "            \n" +
                "            const settingsAudioOnlyToggle = document.getElementById('settings-audio-only-toggle');\n" +
                "            if (settingsAudioOnlyToggle) {\n" +
                "                settingsAudioOnlyToggle.checked = (localStorage.getItem('audio_only_default') === 'true');\n" +
                "                settingsAudioOnlyToggle.addEventListener('change', function() {\n" +
                "                    localStorage.setItem('audio_only_default', settingsAudioOnlyToggle.checked ? 'true' : 'false');\n" +
                "                });\n" +
                "            }\n" +
                "            \n" +
                "            const autoSaveSetting = (paramName, paramValue) => {\n" +
                "                fetch('/settings?action=save&format=ajax&' + encodeURIComponent(paramName) + '=' + encodeURIComponent(paramValue))\n" +
                "                    .then(() => { if (typeof showToast === 'function') showToast('Preference saved'); })\n" +
                "                    .catch(err => console.error(err));\n" +
                "            };\n" +
                "            const settingQuality = document.getElementById('setting-video-quality');\n" +
                "            if (settingQuality) {\n" +
                "                settingQuality.addEventListener('change', function() { autoSaveSetting('video_quality', this.value); });\n" +
                "            }\n" +
                "            const settingFeedMode = document.getElementById('setting-home-feed-mode');\n" +
                "            if (settingFeedMode) {\n" +
                "                settingFeedMode.addEventListener('change', function() { autoSaveSetting('home_feed_mode', this.value); });\n" +
                "            }\n" +
                "            const settingHideWatched = document.getElementById('setting-hide-watched');\n" +
                "            if (settingHideWatched) {\n" +
                "                settingHideWatched.addEventListener('change', function() { autoSaveSetting('hide_watched', this.checked ? 'true' : 'false'); });\n" +
                "            }\n" +
                "            const settingHideShorts = document.getElementById('setting-hide-shorts');\n" +
                "            if (settingHideShorts) {\n" +
                "                settingHideShorts.addEventListener('change', function() { autoSaveSetting('hide_shorts', this.checked ? 'true' : 'false'); });\n" +
                "            }\n" +
                "            \n" +
                "            document.addEventListener('click', function(e) {\n" +
                "                const target = e.target.closest('a');\n" +
                "                if (target && target.href) {\n" +
                "                    const urlStr = target.href;\n" +
                "                    if (urlStr.indexOf('/watch?') !== -1 && urlStr.indexOf('force_video=true') === -1) {\n" +
                "                        if (localStorage.getItem('audio_only_default') === 'true') {\n" +
                "                            e.preventDefault();\n" +
                "                            try {\n" +
                "                                const url = new URL(urlStr);\n" +
                "                                url.pathname = '/audio';\n" +
                "                                window.location.href = url.toString();\n" +
                "                            } catch (err) {\n" +
                "                                window.location.href = urlStr.replace('/watch?', '/audio?');\n" +
                "                            }\n" +
                "                        }\n" +
                "                    }\n" +
                "                }\n" +
                "            });\n" +
                "            \n" +
                "            const searchForm = document.querySelector('.search-form');\n" +
                "            const searchInput = document.querySelector('.search-input');\n" +
                "            const searchBtn = document.querySelector('.search-btn');\n" +
                "            const topBar = document.querySelector('.top-bar');\n" +
                "            const suggestionsBox = document.getElementById('search-suggestions-box');\n" +
                "            \n" +
                "            if (searchForm && searchInput && searchBtn) {\n" +
                "                if (window.innerWidth <= 768) {\n" +
                "                    searchBtn.addEventListener('click', function(e) {\n" +
                "                        if (!searchForm.classList.contains('search-active')) {\n" +
                "                            e.preventDefault();\n" +
                "                            searchForm.classList.add('search-active');\n" +
                "                            if (topBar) topBar.classList.add('search-active');\n" +
                "                            searchInput.focus();\n" +
                "                            window.history.pushState({ searchActive: true }, '');\n" +
                "                        }\n" +
                "                    });\n" +
                "                    \n" +
                "                    window.addEventListener('popstate', function(e) {\n" +
                "                        if (searchForm.classList.contains('search-active')) {\n" +
                "                            searchForm.classList.remove('search-active');\n" +
                "                            if (topBar) topBar.classList.remove('search-active');\n" +
                "                            if (suggestionsBox) suggestionsBox.style.display = 'none';\n" +
                "                        }\n" +
                "                    });\n" +
                "                    \n" +
                "                    searchInput.addEventListener('blur', function() {\n" +
                "                        setTimeout(() => {\n" +
                "                            if (document.activeElement !== searchInput && searchInput.value.trim() === '') {\n" +
                "                                searchForm.classList.remove('search-active');\n" +
                "                                if (topBar) topBar.classList.remove('search-active');\n" +
                "                                if (window.history.state && window.history.state.searchActive) {\n" +
                "                                    window.history.back();\n" +
                "                                }\n" +
                "                            }\n" +
                "                        }, 250);\n" +
                "                    });\n" +
                "                    \n" +
                "                    document.addEventListener('click', function(e) {\n" +
                "                        if (!searchForm.contains(e.target) && searchForm.classList.contains('search-active')) {\n" +
                "                            if (searchInput.value.trim() === '') {\n" +
                "                                searchForm.classList.remove('search-active');\n" +
                "                                if (topBar) topBar.classList.remove('search-active');\n" +
                "                                if (window.history.state && window.history.state.searchActive) {\n" +
                "                                    window.history.back();\n" +
                "                                }\n" +
                "                            }\n" +
                "                        }\n" +
                "                    });\n" +
                "                }\n" +
                "                \n" +
                "                if (suggestionsBox) {\n" +
                "                    // The full search-history list is small (server caps it at 10) and only\n" +
                "                    // actually changes when a search is submitted or a suggestion is deleted -\n" +
                "                    // neither of which happens while the user is still mid-keystroke. Fetching it\n" +
                "                    // fresh on every 'input' event (every keystroke) re-requested the same\n" +
                "                    // unchanged data over and over; cache it after the first fetch and just\n" +
                "                    // re-filter locally for the rest of that page view.\n" +
                "                    let cachedHistory = null;\n" +
                "                    searchInput.addEventListener('focus', showSuggestions);\n" +
                "                    searchInput.addEventListener('input', showSuggestions);\n" +
                "                    searchInput.addEventListener('click', showSuggestions);\n" +
                "                    \n" +
                "                    document.addEventListener('click', function(e) {\n" +
                "                        if (!searchForm.contains(e.target)) {\n" +
                "                            suggestionsBox.style.display = 'none';\n" +
                "                        }\n" +
                "                    });\n" +
                "                    \n" +
                "                    function showSuggestions() {\n" +
                "                        if (cachedHistory !== null) {\n" +
                "                            renderSuggestions(cachedHistory);\n" +
                "                            return;\n" +
                "                        }\n" +
                "                        fetch('/search-history')\n" +
                "                            .then(res => res.json())\n" +
                "                            .then(data => {\n" +
                "                                cachedHistory = data || [];\n" +
                "                                renderSuggestions(cachedHistory);\n" +
                "                            });\n" +
                "                    }\n" +
                "                    \n" +
                "                    function renderSuggestions(data) {\n" +
                "                                if (data && data.length > 0) {\n" +
                "                                    const filterVal = searchInput.value.toLowerCase().trim();\n" +
                "                                    const filtered = filterVal ? data.filter(q => q.toLowerCase().includes(filterVal)) : data;\n" +
                "                                    if (filtered.length === 0) {\n" +
                "                                        suggestionsBox.style.display = 'none';\n" +
                "                                        return;\n" +
                "                                    }\n" +
                "                                    suggestionsBox.innerHTML = '';\n" +
                "                                    filtered.forEach(q => {\n" +
                "                                        const item = document.createElement('div');\n" +
                "                                        item.className = 'search-suggestion-item';\n" +
                "                                        \n" +
                "                                        const textDiv = document.createElement('div');\n" +
                "                                        textDiv.className = 'search-suggestion-text';\n" +
                "                                        textDiv.innerHTML = `<svg viewBox=\"0 0 24 24\" fill=\"currentColor\" width=\"18\" height=\"18\" style=\"color:var(--card-meta-color);\"><path d=\"M11.9 21.1c-4.3 0-8-3.1-8.7-7.4-.1-.7-.1-1.4 0-2.1.8-4.4 4.6-7.5 9-7.5H13V2l5.3 4.2-5.3 4.2V8.1H12.2c-3.1 0-5.7 2.2-6.2 5.2-.1.5-.1 1 0 1.5.5 3 3.1 5.2 6.2 5.2 3.5 0 6.3-2.8 6.3-6.3h2c0 4.6-3.7 8.2-8.6 8.2zm-.4-12.6h1v4.8l4.2 2.5-.5.9-4.7-2.8z\"/></svg><span>\${q}</span>`;\n" +
                "                                        \n" +
                "                                        textDiv.addEventListener('mousedown', (e) => {\n" +
                "                                            e.preventDefault();\n" +
                "                                            searchInput.value = q;\n" +
                "                                            searchForm.submit();\n" +
                "                                        });\n" +
                "                                        \n" +
                "                                        const delBtn = document.createElement('span');\n" +
                "                                        delBtn.className = 'search-suggestion-delete';\n" +
                "                                        delBtn.textContent = 'Remove';\n" +
                "                                        delBtn.addEventListener('mousedown', (e) => {\n" +
                "                                            e.preventDefault();\n" +
                "                                            e.stopPropagation();\n" +
                "                                            fetch('/search-history?delete=' + encodeURIComponent(q));\n" +
                "                                            cachedHistory = cachedHistory ? cachedHistory.filter(item => item !== q) : null;\n" +
                "                                            renderSuggestions(cachedHistory);\n" +
                "                                        });\n" +
                "                                        \n" +
                "                                        item.appendChild(textDiv);\n" +
                "                                        item.appendChild(delBtn);\n" +
                "                                        suggestionsBox.appendChild(item);\n" +
                "                                    });\n" +
                "                                    suggestionsBox.style.display = 'flex';\n" +
                "                                } else {\n" +
                "                                    suggestionsBox.style.display = 'none';\n" +
                "                                }\n" +
                "                    }\n" +
                "                }\n" +
                "            }\n" +
                "        });\n" +
                "        \n" +
                "        function toggleSubscribe(event, btn, uploaderUrl, name, avatar) {\n" +
                "            event.preventDefault();\n" +
                "            const isSub = btn.classList.contains('subscribed');\n" +
                "            const action = isSub ? 'unsubscribe' : 'subscribe';\n" +
                "            const url = '/subscribe?action=' + action + '&id=' + encodeURIComponent(uploaderUrl) + '&name=' + encodeURIComponent(name) + '&avatar=' + encodeURIComponent(avatar) + '&back=ajax';\n" +
                "            \n" +
                "            if (isSub) {\n" +
                "                btn.classList.remove('subscribed');\n" +
                "                btn.textContent = 'Subscribe';\n" +
                "            } else {\n" +
                "                btn.classList.add('subscribed');\n" +
                "                btn.textContent = 'Subscribed';\n" +
                "            }\n" +
                "            \n" +
                "            fetch(url).catch(() => {\n" +
                "                if (isSub) {\n" +
                "                    btn.classList.add('subscribed');\n" +
                "                    btn.textContent = 'Subscribed';\n" +
                "                } else {\n" +
                "                    btn.classList.remove('subscribed');\n" +
                "                    btn.textContent = 'Subscribe';\n" +
                "                }\n" +
                "            });\n" +
                "        }\n" +
                "        \n" +
                "        function toggleBlock(event, btn, uploaderUrl) {\n" +
                "            event.preventDefault();\n" +
                "            const isBlocked = btn.classList.contains('blocked');\n" +
                "            const action = isBlocked ? 'unblock' : 'block';\n" +
                "            const url = '/block_channel?action=' + action + '&id=' + encodeURIComponent(uploaderUrl) + '&back=ajax';\n" +
                "            const setBlocked = (blocked) => {\n" +
                "                btn.classList.toggle('blocked', blocked);\n" +
                "                btn.classList.toggle('danger', !blocked);\n" +
                "                btn.textContent = blocked ? 'Blocked' : 'Block';\n" +
                "            };\n" +
                "            \n" +
                "            setBlocked(!isBlocked);\n" +
                "            fetch(url).catch(() => setBlocked(isBlocked));\n" +
                "        }\n" +
                "        \n" +
                // Single chokepoint for fragment fetches - a plain fetch().then(res.text()) treats
                // a 500 like a 200, handing the error page's body to onSuccess as if it were content.
                "        function fetchText(url, onSuccess, onError) {\n" +
                "            fetch(url)\n" +
                "                .then(res => {\n" +
                "                    if (!res.ok) throw new Error('HTTP ' + res.status);\n" +
                "                    return res.text();\n" +
                "                })\n" +
                "                .then(onSuccess)\n" +
                "                .catch(onError);\n" +
                "        }\n" +
                "        \n" +
                // Shared "share this video" query-string shape, used by toggleWatchLater() and
                // toggleLikeState() - each prepends its own endpoint/action.
                "        function sharedVideoParamsQs(url, title, uploader, thumbnail, uploaderUrl) {\n" +
                "            return '&url=' + encodeURIComponent(url) + '&title=' + encodeURIComponent(title) +\n" +
                "                '&uploader=' + encodeURIComponent(uploader) + '&thumbnail=' + encodeURIComponent(thumbnail) +\n" +
                "                '&uploaderUrl=' + encodeURIComponent(uploaderUrl);\n" +
                "        }\n" +
                "        \n" +
                // outerHTML-swaps the wrapper button with the next grid+pagination fragment, so
                // already-loaded rows above stay put instead of the page re-navigating.
                "        function loadMoreSearch(btn, nextPage, svcId, query) {\n" +
                "            const wrapper = btn.parentElement;\n" +
                "            btn.textContent = 'Loading...';\n" +
                "            btn.style.pointerEvents = 'none';\n" +
                "            fetchText('/search?ajax=1&serviceId=' + svcId + '&q=' + encodeURIComponent(query) + '&nextPage=' + encodeURIComponent(nextPage),\n" +
                "                html => { if (wrapper) wrapper.outerHTML = html; },\n" +
                "                () => { btn.textContent = 'Failed to load. Tap to retry'; btn.style.pointerEvents = 'auto'; });\n" +
                "        }\n" +
                "        \n" +
                "        function loadMoreChannel(btn, nextPage, svcId, channelUrl, tab) {\n" +
                "            const wrapper = btn.parentElement;\n" +
                "            btn.textContent = 'Loading...';\n" +
                "            btn.style.pointerEvents = 'none';\n" +
                "            fetchText('/channel?ajax=1&serviceId=' + svcId + '&id=' + encodeURIComponent(channelUrl) + '&tab=' + tab + '&nextPage=' + encodeURIComponent(nextPage),\n" +
                "                html => { if (wrapper) wrapper.outerHTML = html; },\n" +
                "                () => { btn.textContent = 'Failed to load. Tap to retry'; btn.style.pointerEvents = 'auto'; });\n" +
                "        }\n" +
                "        \n" +
                "        function loadMorePlaylist(btn, nextPage, svcId, playlistUrl) {\n" +
                "            const wrapper = btn.parentElement;\n" +
                "            btn.textContent = 'Loading...';\n" +
                "            btn.style.pointerEvents = 'none';\n" +
                "            fetchText('/playlist?ajax=1&serviceId=' + svcId + '&id=' + encodeURIComponent(playlistUrl) + '&nextPage=' + encodeURIComponent(nextPage),\n" +
                "                html => { if (wrapper) wrapper.outerHTML = html; },\n" +
                "                () => { btn.textContent = 'Failed to load. Tap to retry'; btn.style.pointerEvents = 'auto'; });\n" +
                "        }\n" +
                "        \n" +
                "        function toggleWatchLater(event, btn, url, title, uploader, thumbnail, serviceId, uploaderUrl) {\n" +
                "            event.preventDefault();\n" +
                "            const isSaved = btn.classList.contains('added');\n" +
                "            const action = isSaved ? 'remove' : 'add';\n" +
                "            const qs = '/watch_later_action?action=' + action + sharedVideoParamsQs(url, title, uploader, thumbnail, uploaderUrl) +\n" +
                "                '&type=video&serviceId=' + serviceId + '&back=ajax';\n" +
                "            \n" +
                "            if (isSaved) {\n" +
                "                btn.classList.remove('added');\n" +
                "                btn.innerHTML = '<span class=\"material-symbols-rounded\" style=\"font-size:18px;\">schedule</span>Watch Later';\n" +
                "            } else {\n" +
                "                btn.classList.add('added');\n" +
                "                btn.innerHTML = '<span class=\"material-symbols-rounded\" style=\"font-size:18px;\">check</span>Saved';\n" +
                "            }\n" +
                "            \n" +
                "            fetch(qs).catch(() => {\n" +
                "                if (isSaved) {\n" +
                "                    btn.classList.add('added');\n" +
                "                    btn.innerHTML = '<span class=\"material-symbols-rounded\" style=\"font-size:18px;\">check</span>Saved';\n" +
                "                } else {\n" +
                "                    btn.classList.remove('added');\n" +
                "                    btn.innerHTML = '<span class=\"material-symbols-rounded\" style=\"font-size:18px;\">schedule</span>Watch Later';\n" +
                "                }\n" +
                "            });\n" +
                "        }\n" +
                "        \n" +
                "        function toggleLikeState(event, btn, kind, url, title, uploader, thumbnail, uploaderUrl) {\n" +
                "            event.preventDefault();\n" +
                "            const pill = btn.closest('.like-dislike-pill');\n" +
                "            const likeBtn = pill.querySelector('.like-btn');\n" +
                "            const dislikeBtn = pill.querySelector('.dislike-btn');\n" +
                "            const prevLike = likeBtn ? likeBtn.classList.contains('active') : false;\n" +
                "            const prevDislike = dislikeBtn ? dislikeBtn.classList.contains('active') : false;\n" +
                "            const wasActive = btn.classList.contains('active');\n" +
                "            const action = wasActive ? 'remove' : kind;\n" +
                "            const apply = (liked, disliked) => {\n" +
                "                if (likeBtn) likeBtn.classList.toggle('active', liked);\n" +
                "                if (dislikeBtn) dislikeBtn.classList.toggle('active', disliked);\n" +
                "            };\n" +
                "            apply(!wasActive && kind === 'like', !wasActive && kind === 'dislike');\n" +
                "            const qs = '/rate_video?action=' + action + sharedVideoParamsQs(url, title, uploader, thumbnail, uploaderUrl) + '&back=ajax';\n" +
                "            fetch(qs).catch(() => apply(prevLike, prevDislike));\n" +
                "        }\n" +
                "        \n" +
                "        function removeHistoryItem(event, btn, videoUrl, serviceId) {\n" +
                "            event.preventDefault();\n" +
                "            event.stopPropagation();\n" +
                "            btn.style.pointerEvents = 'none';\n" +
                "            const url = '/history_action?action=remove&url=' + encodeURIComponent(videoUrl) + '&serviceId=' + serviceId + '&back=ajax';\n" +
                "            fetch(url).then(res => {\n" +
                "                const card = btn.closest('.card');\n" +
                "                if (card) {\n" +
                "                    card.style.transition = 'opacity 0.3s ease, transform 0.3s ease';\n" +
                "                    card.style.opacity = '0';\n" +
                "                    card.style.transform = 'scale(0.9)';\n" +
                "                    setTimeout(() => card.remove(), 300);\n" +
                "                }\n" +
                "            }).catch(() => {\n" +
                "                btn.style.pointerEvents = '';\n" +
                "            });\n" +
                "        }\n" +
                "        \n" +
                "        function toggleHistorySelectMode() {\n" +
                "            const active = document.body.classList.toggle('history-select-mode');\n" +
                "            const bar = document.getElementById('history-select-bar');\n" +
                "            if (bar) bar.style.display = active ? 'flex' : 'none';\n" +
                "            if (!active) {\n" +
                "                document.querySelectorAll('.card-select-checkbox').forEach(cb => { cb.checked = false; });\n" +
                "                const selectAll = document.getElementById('history-select-all');\n" +
                "                if (selectAll) selectAll.checked = false;\n" +
                "            }\n" +
                "            updateHistorySelectCount();\n" +
                "        }\n" +
                "        \n" +
                "        function toggleSelectAllHistory(checkbox) {\n" +
                "            document.querySelectorAll('.card-select-checkbox').forEach(cb => { cb.checked = checkbox.checked; });\n" +
                "            updateHistorySelectCount();\n" +
                "        }\n" +
                "        \n" +
                "        function updateHistorySelectCount() {\n" +
                "            const count = document.querySelectorAll('.card-select-checkbox:checked').length;\n" +
                "            const label = document.getElementById('history-select-count');\n" +
                "            if (label) label.textContent = count + ' selected';\n" +
                "        }\n" +
                "        \n" +
                // Collapsed by default (chapters-list starts display:none - see the chapters
                // section markup in HtmlRendererWatch.kt) so current-chapter-label in the header
                // is the only thing visible until the user asks for the full list.
                "        function toggleChaptersSection() {\n" +
                "            const list = document.getElementById('chapters-list');\n" +
                "            const icon = document.getElementById('chapters-toggle-icon');\n" +
                "            if (!list) return;\n" +
                "            const expanded = list.style.display !== 'none';\n" +
                "            list.style.display = expanded ? 'none' : 'flex';\n" +
                "            if (icon) icon.textContent = expanded ? 'expand_more' : 'expand_less';\n" +
                "        }\n" +
                "        \n" +
                "        function deleteSelectedHistory(serviceId) {\n" +
                "            const checked = document.querySelectorAll('.card-select-checkbox:checked');\n" +
                "            if (checked.length === 0) return;\n" +
                "            if (!confirm('Delete ' + checked.length + ' selected item(s) from history?')) return;\n" +
                "            const urls = Array.from(checked).map(cb => cb.dataset.url);\n" +
                "            Promise.all(urls.map(u => fetch('/history_action?action=remove&url=' + encodeURIComponent(u) + '&serviceId=' + serviceId + '&back=ajax')))\n" +
                "                .then(() => { location.reload(); })\n" +
                "                .catch(() => { location.reload(); });\n" +
                "        }\n" +
                "        \n" +
                watchPlayerScripts() +
                "    </script>\n";

    // video.js watch-page behaviors, shared across every /watch render instead of each living as
    // its own hand-escaped Kotlin string in HtmlRendererWatch.kt (which had already grown to ~7
    // near-duplicate (function(){...})() blocks, one per feature). Per-request data (segment
    // times, chapter titles, URLs) stays server-side and is passed in as plain call arguments -
    // e.g. "initChapterMarkers(player, [{s:12,t:\"Intro\"}]);" - same pattern the rest of this file
    // already uses for toggleSubscribe()/toggleWatchLater()/etc.
    // A function, not a property: SCRIPTS above calls it inside its own "..." + "..." chain, and a
    // property here would either (a) not exist yet when SCRIPTS's own initializer runs, since
    // object properties init in declaration order and this one is declared after SCRIPTS, or
    // (b) if const, get inlined back into SCRIPTS's chain and re-fold the two into one string,
    // risking the same 65535-byte constant-pool ceiling STATIC_ASSET_VERSION's comment
    // (HtmlRendererCommon.kt) already had to work around once. A function call is neither.
    private fun watchPlayerScripts(): String =
                "        function initAdvancedPlayerControls(player, nextUrl, nextTitle, nextThumb) {\n" +
                // <media-player> IS the wrapper element (no separate .el() to reach into) and the
                // double-tap/volume-hud/autoplay-overlay markup is already rendered as its direct
                // children server-side - video.js's old ready()-time relocation of these into
                // player.el() (needed so they'd stay visible once video.js's fullscreen target
                // took over) has no equivalent step to do here.
                "            const wrapper = player;\n" +
                "            \n" +
                "            // Double tap & Double click to seek\n" +
                "            if (wrapper) {\n" +
                "                let lastTap = 0;\n" +
                "                wrapper.addEventListener(\"touchstart\", function(e) {\n" +
                "                    const now = Date.now();\n" +
                "                    const DOUBLE_PRESS_DELAY = 300;\n" +
                "                    if (now - lastTap < DOUBLE_PRESS_DELAY) {\n" +
                "                        e.preventDefault();\n" +
                "                        const rect = wrapper.getBoundingClientRect();\n" +
                "                        const touchX = e.touches[0].clientX - rect.left;\n" +
                "                        const isLeft = touchX < rect.width * 0.4;\n" +
                "                        const isRight = touchX > rect.width * 0.6;\n" +
                "                        if (isLeft) {\n" +
                "                            window.seekVideo(-10);\n" +
                "                            showDoubleTapRipple(\"left\");\n" +
                "                        } else if (isRight) {\n" +
                "                            window.seekVideo(10);\n" +
                "                            showDoubleTapRipple(\"right\");\n" +
                "                        }\n" +
                "                    }\n" +
                "                    lastTap = now;\n" +
                "                }, { passive: false });\n" +
                "                \n" +
                "                wrapper.addEventListener(\"dblclick\", function(e) {\n" +
                "                    e.preventDefault();\n" +
                "                    const rect = wrapper.getBoundingClientRect();\n" +
                "                    const clickX = e.clientX - rect.left;\n" +
                "                    const isLeft = clickX < rect.width * 0.4;\n" +
                "                    const isRight = clickX > rect.width * 0.6;\n" +
                "                    if (isLeft) {\n" +
                "                        window.seekVideo(-10);\n" +
                "                        showDoubleTapRipple(\"left\");\n" +
                "                    } else if (isRight) {\n" +
                "                        window.seekVideo(10);\n" +
                "                        showDoubleTapRipple(\"right\");\n" +
                "                    }\n" +
                "                });\n" +
                "            }\n" +
                "            \n" +
                "            function showDoubleTapRipple(side) {\n" +
                "                const ind = document.getElementById(\"double-tap-\" + side);\n" +
                "                if (ind) {\n" +
                "                    ind.classList.add(\"show\");\n" +
                "                    setTimeout(() => ind.classList.remove(\"show\"), 650);\n" +
                "                }\n" +
                "            }\n" +
                "            \n" +
                "            // Swipe vertically on right side to adjust volume\n" +
                "            if (wrapper) {\n" +
                "                let touchStartY = 0;\n" +
                "                let initialVolume = 1;\n" +
                "                let isSwipeActive = false;\n" +
                "                \n" +
                "                wrapper.addEventListener(\"touchstart\", function(e) {\n" +
                "                    if (e.touches.length === 1) {\n" +
                "                        const rect = wrapper.getBoundingClientRect();\n" +
                "                        const touchX = e.touches[0].clientX - rect.left;\n" +
                "                        if (touchX > rect.width * 0.5) {\n" +
                "                            touchStartY = e.touches[0].clientY;\n" +
                "                            initialVolume = player.volume;\n" +
                "                            isSwipeActive = true;\n" +
                "                        }\n" +
                "                    }\n" +
                "                }, { passive: true });\n" +
                "                \n" +
                "                wrapper.addEventListener(\"touchmove\", function(e) {\n" +
                "                    if (isSwipeActive && e.touches.length === 1) {\n" +
                "                        e.preventDefault();\n" +
                "                        const deltaY = touchStartY - e.touches[0].clientY;\n" +
                "                        const rect = wrapper.getBoundingClientRect();\n" +
                "                        const volumeChange = deltaY / (rect.height * 0.8);\n" +
                "                        const newVolume = Math.max(0, Math.min(1, initialVolume + volumeChange));\n" +
                "                        player.volume = newVolume;\n" +
                "                        showVolumeHUD(Math.round(newVolume * 100));\n" +
                "                    }\n" +
                "                }, { passive: false });\n" +
                "                \n" +
                "                wrapper.addEventListener(\"touchend\", function() {\n" +
                "                    isSwipeActive = false;\n" +
                "                });\n" +
                "            }\n" +
                "            \n" +
                "            let volumeHudTimeout = null;\n" +
                "            function showVolumeHUD(volumePercent) {\n" +
                "                const hud = document.getElementById(\"volume-hud-indicator\");\n" +
                "                const text = document.getElementById(\"volume-hud-text\");\n" +
                "                const icon = document.getElementById(\"volume-hud-icon\");\n" +
                "                if (hud && text && icon) {\n" +
                "                    text.innerText = volumePercent + \"%\";\n" +
                "                    if (volumePercent === 0) icon.innerText = \"🔇\";\n" +
                "                    else if (volumePercent < 30) icon.innerText = \"🔈\";\n" +
                "                    else if (volumePercent < 70) icon.innerText = \"🔉\";\n" +
                "                    else icon.innerText = \"🔊\";\n" +
                "                    hud.classList.add(\"show\");\n" +
                "                    clearTimeout(volumeHudTimeout);\n" +
                "                    volumeHudTimeout = setTimeout(() => hud.classList.remove(\"show\"), 1000);\n" +
                "                }\n" +
                "            }\n" +
                "            \n" +
                "            // Autoplay Queue\n" +
                "            let autoplayTimer = null;\n" +
                "            let autoplayInterval = null;\n" +
                "            player.addEventListener(\"ended\", function() {\n" +
                "                if (!nextUrl) return;\n" +
                "                const overlay = document.getElementById(\"autoplay-overlay\");\n" +
                "                const titleEl = document.getElementById(\"autoplay-next-title\");\n" +
                "                const thumbEl = document.getElementById(\"autoplay-next-thumb\");\n" +
                "                const progressCircle = document.getElementById(\"autoplay-progress-circle\");\n" +
                "                \n" +
                "                if (overlay && titleEl && thumbEl && progressCircle) {\n" +
                "                    titleEl.innerText = nextTitle;\n" +
                "                    thumbEl.src = nextThumb;\n" +
                "                    overlay.classList.add(\"show\");\n" +
                "                    \n" +
                "                    const totalDash = 138;\n" +
                "                    progressCircle.style.strokeDashoffset = 0;\n" +
                "                    \n" +
                "                    autoplayTimer = setTimeout(() => {\n" +
                "                        window.location.href = nextUrl;\n" +
                "                    }, 5000);\n" +
                "                    \n" +
                "                    let elapsed = 0;\n" +
                "                    autoplayInterval = setInterval(() => {\n" +
                "                        elapsed += 100;\n" +
                "                        const progress = elapsed / 5000;\n" +
                "                        progressCircle.style.strokeDashoffset = totalDash * progress;\n" +
                "                    }, 100);\n" +
                "                }\n" +
                "            });\n" +
                "            \n" +
                "            function clearAutoplay() {\n" +
                "                clearTimeout(autoplayTimer);\n" +
                "                clearInterval(autoplayInterval);\n" +
                "                const overlay = document.getElementById(\"autoplay-overlay\");\n" +
                "                if (overlay) overlay.classList.remove(\"show\");\n" +
                "            }\n" +
                "            \n" +
                "            const cancelBtn = document.getElementById(\"autoplay-cancel\");\n" +
                "            if (cancelBtn) cancelBtn.addEventListener(\"click\", clearAutoplay);\n" +
                "            \n" +
                "            const playNowBtn = document.getElementById(\"autoplay-play-now\");\n" +
                "            if (playNowBtn) {\n" +
                "                playNowBtn.addEventListener(\"click\", () => {\n" +
                "                    if (nextUrl) window.location.href = nextUrl;\n" +
                "                });\n" +
                "            }\n" +
                "            \n" +
                "            // Keyboard Shortcuts\n" +
                "            document.addEventListener(\"keydown\", (e) => {\n" +
                "                const active = document.activeElement;\n" +
                "                if (active && (active.tagName === \"INPUT\" || active.tagName === \"SELECT\" || active.tagName === \"TEXTAREA\" || active.isContentEditable)) {\n" +
                "                    return;\n" +
                "                }\n" +
                "                if (e.key === \" \" || e.key === \"k\" || e.key === \"K\") {\n" +
                "                    e.preventDefault();\n" +
                "                    if (player.paused) player.play().catch(e => {}); else player.pause();\n" +
                "                } else if (e.key === \"j\" || e.key === \"J\") {\n" +
                "                    e.preventDefault();\n" +
                "                    window.seekVideo(-10);\n" +
                "                    showDoubleTapRipple(\"left\");\n" +
                "                } else if (e.key === \"l\" || e.key === \"L\") {\n" +
                "                    e.preventDefault();\n" +
                "                    window.seekVideo(10);\n" +
                "                    showDoubleTapRipple(\"right\");\n" +
                "                } else if (e.key === \"m\" || e.key === \"M\") {\n" +
                "                    e.preventDefault();\n" +
                "                    player.muted = !player.muted;\n" +
                "                    showVolumeHUD(player.muted ? 0 : Math.round(player.volume * 100));\n" +
                "                }\n" +
                "            });\n" +
                "            \n" +
                // Picture-in-Picture: Default Video Layout already ships its own PIP button
                // (Vidstack detects support itself, see the player root's data-can-pip
                // attribute) - no need to hand-register a custom control like video.js required.
                "        }\n" +
                "        \n" +
                // CSS object-fit:contain (HtmlStyles.kt) still let real mobile fullscreen stretch/
                // crop the picture, unreproducible on desktop. This computes the letterboxed size
                // directly in JS instead - explicit pixel dimensions on the inner <video> via
                // setProperty(...,'important'), winning the cascade regardless of what defeated the
                // CSS-only version.
                "        function initFullscreenLetterbox(player) {\n" +
                "            function fitVideoLetterbox() {\n" +
                "                var videoTag = player.querySelector(\"video\");\n" +
                "                if (!videoTag) return;\n" +
                "                if (!player.state.fullscreen) {\n" +
                "                    videoTag.style.removeProperty(\"width\");\n" +
                "                    videoTag.style.removeProperty(\"height\");\n" +
                "                    videoTag.style.removeProperty(\"position\");\n" +
                "                    videoTag.style.removeProperty(\"top\");\n" +
                "                    videoTag.style.removeProperty(\"left\");\n" +
                "                    return;\n" +
                "                }\n" +
                // Read straight off the native <video> element rather than a player-level
                // videoWidth/videoHeight accessor - avoids depending on whether Vidstack exposes
                // one, and this is exactly the element being resized below anyway.
                "                var vw = videoTag.videoWidth || 16;\n" +
                "                var vh = videoTag.videoHeight || 9;\n" +
                "                var availW = window.innerWidth;\n" +
                "                var availH = window.innerHeight;\n" +
                "                var targetW, targetH;\n" +
                "                if (vw / vh > availW / availH) {\n" +
                "                    targetW = availW;\n" +
                "                    targetH = availW * vh / vw;\n" +
                "                } else {\n" +
                "                    targetH = availH;\n" +
                "                    targetW = availH * vw / vh;\n" +
                "                }\n" +
                "                videoTag.style.setProperty(\"width\", targetW + \"px\", \"important\");\n" +
                "                videoTag.style.setProperty(\"height\", targetH + \"px\", \"important\");\n" +
                "                videoTag.style.setProperty(\"position\", \"absolute\", \"important\");\n" +
                "                videoTag.style.setProperty(\"top\", ((availH - targetH) / 2) + \"px\", \"important\");\n" +
                "                videoTag.style.setProperty(\"left\", ((availW - targetW) / 2) + \"px\", \"important\");\n" +
                "            }\n" +
                "            player.addEventListener(\"fullscreen-change\", fitVideoLetterbox);\n" +
                "            player.addEventListener(\"loaded-metadata\", fitVideoLetterbox);\n" +
                "            window.addEventListener(\"resize\", fitVideoLetterbox);\n" +
                "            window.addEventListener(\"orientationchange\", fitVideoLetterbox);\n" +
                "        }\n" +
                "        \n" +
                // videoId is already URL-encoded server-side (HtmlRendererCommon.encodeUrl) and
                // concatenated as-is - do not encodeURIComponent() it again here, that's the
                // escapeJs-vs-encodeUrl double-encoding bug class documented elsewhere in this
                // file (see e.g. HtmlRendererCommon.kt's history-save chokepoint comment).
                "        function initWatchProgressReporting(player, videoId, serviceId) {\n" +
                "            var lastReported = -1;\n" +
                "            function reportProgress() {\n" +
                "                var dur = player.duration;\n" +
                "                if (!dur) return;\n" +
                "                var percent = Math.round((player.currentTime / dur) * 100);\n" +
                "                if (percent === lastReported) return;\n" +
                "                lastReported = percent;\n" +
                "                var url = \"/api/v1/watch_progress?id=\" + videoId + \"&serviceId=\" + serviceId + \"&percent=\" + percent + \"&durationSeconds=\" + Math.round(dur);\n" +
                "                fetch(url, { keepalive: true }).catch(function() {});\n" +
                "            }\n" +
                "            var progressTimer = setInterval(reportProgress, 15000);\n" +
                "            player.addEventListener(\"pause\", reportProgress);\n" +
                "            window.addEventListener(\"pagehide\", reportProgress);\n" +
                "        }\n" +
                "        \n" +
                // Download button <-> Flow's native downloader (/api/v1/download,
                // LocalHttpServerDownloadHandlers.kt). videoId is already URL-encoded (concatenated
                // as-is, same double-encoding rule as initWatchProgressReporting above). The server
                // is the source of truth: the button only renders what /download says, polling
                // while a download is in flight, so it also reflects downloads started from the app.
                "        function initDownloadButton(videoId, serviceId) {\n" +
                "            var btn = document.getElementById(\"download-btn\");\n" +
                "            if (!btn) return;\n" +
                "            var state = \"none\", timer = null;\n" +
                "            function call(action) {\n" +
                "                return fetch(\"/api/v1/download?action=\" + action + \"&serviceId=\" + serviceId + \"&id=\" + videoId)\n" +
                "                    .then(function(res) { return res.json().then(function(body) { return { ok: res.ok, body: body }; }); });\n" +
                "            }\n" +
                "            function icon(name) { return '<span class=\"material-symbols-rounded\" style=\"font-size:18px;\">' + name + '</span>'; }\n" +
                "            function render(s) {\n" +
                "                state = s.state;\n" +
                "                btn.classList.remove(\"added\", \"danger\");\n" +
                "                btn.title = \"\";\n" +
                "                if (state === \"pending\" || state === \"downloading\") {\n" +
                "                    btn.innerHTML = icon(\"downloading\") + \"Downloading \" + s.progress + \"%\";\n" +
                "                    btn.title = \"Click to cancel\";\n" +
                "                } else if (state === \"paused\") {\n" +
                "                    btn.innerHTML = icon(\"pause_circle\") + \"Paused \" + s.progress + \"%\";\n" +
                "                    btn.title = \"Click to cancel\";\n" +
                "                } else if (state === \"completed\") {\n" +
                "                    btn.classList.add(\"added\");\n" +
                "                    btn.innerHTML = icon(\"download_done\") + \"Downloaded\";\n" +
                "                    btn.title = \"Click to delete the downloaded file\";\n" +
                "                } else if (state === \"failed\") {\n" +
                "                    btn.classList.add(\"danger\");\n" +
                "                    btn.innerHTML = icon(\"error\") + \"Retry download\";\n" +
                "                } else {\n" +
                "                    btn.innerHTML = icon(\"download\") + \"Download\";\n" +
                "                }\n" +
                "                var active = state === \"pending\" || state === \"downloading\" || state === \"paused\";\n" +
                "                if (active && !timer) timer = setInterval(refresh, 2000);\n" +
                "                if (!active && timer) { clearInterval(timer); timer = null; }\n" +
                "            }\n" +
                "            function refresh() {\n" +
                "                call(\"status\").then(function(r) { if (r.ok) render(r.body); }).catch(function() {});\n" +
                "            }\n" +
                "            btn.addEventListener(\"click\", function() {\n" +
                "                var action;\n" +
                "                if (state === \"none\" || state === \"failed\") action = \"start\";\n" +
                "                else if (state === \"completed\") { if (!confirm(\"Delete the downloaded file?\")) return; action = \"delete\"; }\n" +
                "                else action = \"cancel\";\n" +
                "                call(action).then(function(r) {\n" +
                "                    if (r.ok) render(r.body); else alert(r.body.error || \"Download failed\");\n" +
                "                }).catch(function() { alert(\"Could not reach the server\"); });\n" +
                "            });\n" +
                "            refresh();\n" +
                "        }\n" +
                "        \n" +
                "        function initSponsorBlockMarkers(player, segments) {\n" +
                "            var skipBtn = document.getElementById(\"sponsor-skip-btn\");\n" +
                "            var skipLabel = document.getElementById(\"sponsor-skip-label\");\n" +
                "            var current = null;\n" +
                "            function placeMarkers() {\n" +
                "                var holder = player.querySelector(\"media-time-slider\");\n" +
                "                var dur = player.duration;\n" +
                "                if (!holder || !dur) return;\n" +
                "                segments.forEach(function(seg) {\n" +
                "                    var marker = document.createElement(\"div\");\n" +
                "                    marker.className = \"sponsor-segment-marker cat-\" + seg.c;\n" +
                "                    marker.style.left = (seg.s / dur * 100) + \"%\";\n" +
                "                    marker.style.width = (Math.max(seg.e - seg.s, 0) / dur * 100) + \"%\";\n" +
                // A short segment's proportional width can round to a sub-pixel value on a narrow
                // mobile progress bar (measured: 0.16px for a 7s segment in a 33-min video on a
                // 375px phone) - invisible and untappable. min-width floors the visual marker
                // without touching the real start/end times the skip-button logic uses.
                "                    marker.style.minWidth = \"3px\";\n" +
                "                    holder.appendChild(marker);\n" +
                "                });\n" +
                "            }\n" +
                "            player.addEventListener(\"loaded-metadata\", placeMarkers, { once: true });\n" +
                "            if (player.duration) placeMarkers();\n" +
                "            player.addEventListener(\"time-update\", function() {\n" +
                "                var t = player.currentTime;\n" +
                "                var seg = null;\n" +
                "                for (var i = 0; i < segments.length; i++) {\n" +
                "                    if (t >= segments[i].s && t < segments[i].e) { seg = segments[i]; break; }\n" +
                "                }\n" +
                "                if (seg) {\n" +
                "                    if (current !== seg) {\n" +
                "                        current = seg;\n" +
                "                        if (skipLabel) skipLabel.textContent = \"跳過 \" + seg.l;\n" +
                "                        if (skipBtn) skipBtn.classList.add(\"visible\");\n" +
                "                    }\n" +
                "                } else if (current) {\n" +
                "                    current = null;\n" +
                "                    if (skipBtn) skipBtn.classList.remove(\"visible\");\n" +
                "                }\n" +
                "            });\n" +
                "            if (skipBtn) skipBtn.addEventListener(\"click\", function() {\n" +
                "                if (current) {\n" +
                "                    player.currentTime = current.e;\n" +
                "                    skipBtn.classList.remove(\"visible\");\n" +
                "                    current = null;\n" +
                "                }\n" +
                "            });\n" +
                "        }\n" +
                "        \n" +
                "        function initChapterMarkers(player, chapters) {\n" +
                "            var label = document.getElementById(\"current-chapter-label\");\n" +
                "            var listEl = document.getElementById(\"chapters-list\");\n" +
                "            var activeIndex = -1;\n" +
                "            function placeTicks() {\n" +
                "                var holder = player.querySelector(\"media-time-slider\");\n" +
                "                var dur = player.duration;\n" +
                "                if (!holder || !dur) return;\n" +
                "                chapters.forEach(function(ch) {\n" +
                "                    if (ch.s <= 0) return;\n" +
                "                    var tick = document.createElement(\"div\");\n" +
                "                    tick.className = \"chapter-tick-marker\";\n" +
                "                    tick.style.left = (ch.s / dur * 100) + \"%\";\n" +
                "                    holder.appendChild(tick);\n" +
                "                });\n" +
                "            }\n" +
                "            player.addEventListener(\"loaded-metadata\", placeTicks, { once: true });\n" +
                "            if (player.duration) placeTicks();\n" +
                "            player.addEventListener(\"time-update\", function() {\n" +
                "                var t = player.currentTime;\n" +
                "                var idx = 0;\n" +
                "                for (var i = 0; i < chapters.length; i++) {\n" +
                "                    if (t >= chapters[i].s) idx = i; else break;\n" +
                "                }\n" +
                "                if (idx === activeIndex) return;\n" +
                "                activeIndex = idx;\n" +
                "                if (label) label.textContent = chapters[idx].t;\n" +
                "                if (listEl) {\n" +
                "                    listEl.querySelectorAll(\".chapter-item\").forEach(function(el, i) {\n" +
                "                        el.classList.toggle(\"active\", i === idx);\n" +
                "                    });\n" +
                "                }\n" +
                "            });\n" +
                "            window.seekToChapter = function(seconds) {\n" +
                "                player.currentTime = seconds;\n" +
                "                document.querySelector(\".player-wrapper\").scrollIntoView({ behavior: \"smooth\", block: \"start\" });\n" +
                "            };\n" +
                "        }\n" +
                "        \n" +
                // Shrinks .player-wrapper to a fixed corner box once IntersectionObserver
                // reports it fully scrolled out of view, so playback stays visible/controllable
                // while reading the description or comments below. #player-space-holder keeps
                // that space's height in normal flow so the wrapper going position:fixed doesn't
                // jump the rest of the page up. Suspended during real fullscreen; the close button
                // pauses and disconnects the observer rather than just hiding, so it doesn't
                // immediately re-trigger.
                "        function initMiniPlayer(player) {\n" +
                "            var wrapper = document.querySelector(\".player-wrapper\");\n" +
                "            var holder = document.getElementById(\"player-space-holder\");\n" +
                "            var sentinel = document.getElementById(\"mini-player-sentinel\");\n" +
                "            var closeBtn = document.getElementById(\"mini-player-close-btn\");\n" +
                "            var dragHandle = document.getElementById(\"mini-player-drag-handle\");\n" +
                "            if (!wrapper || !holder || !sentinel) return;\n" +
                "            var isMini = false, dismissed = false;\n" +
                // Position is inline left/top (overriding the CSS bottom/right default) once the
                // user has dragged it; kept in localStorage so it stays where they left it.
                // Clamped on every apply so a saved spot from a bigger window can't strand it
                // off-screen after a resize/rotation.
                "            var POS_KEY = \"miniPlayerPos\";\n" +
                "            function applyPos(x, y) {\n" +
                "                var maxX = Math.max(0, window.innerWidth - wrapper.offsetWidth);\n" +
                "                var maxY = Math.max(0, window.innerHeight - wrapper.offsetHeight);\n" +
                "                wrapper.style.left = Math.min(Math.max(0, x), maxX) + \"px\";\n" +
                "                wrapper.style.top = Math.min(Math.max(0, y), maxY) + \"px\";\n" +
                "                wrapper.style.right = \"auto\";\n" +
                "                wrapper.style.bottom = \"auto\";\n" +
                "            }\n" +
                "            function clearPos() {\n" +
                "                wrapper.style.left = \"\"; wrapper.style.top = \"\";\n" +
                "                wrapper.style.right = \"\"; wrapper.style.bottom = \"\";\n" +
                "            }\n" +
                "            function restorePos() {\n" +
                "                try {\n" +
                "                    var saved = JSON.parse(localStorage.getItem(POS_KEY));\n" +
                "                    if (saved && typeof saved.x === \"number\" && typeof saved.y === \"number\") applyPos(saved.x, saved.y);\n" +
                "                } catch (e) {}\n" +
                "            }\n" +
                "            function enterMini() {\n" +
                "                if (isMini || dismissed || player.state.fullscreen) return;\n" +
                "                isMini = true;\n" +
                "                holder.style.height = wrapper.offsetHeight + \"px\";\n" +
                "                wrapper.classList.add(\"mini-player\");\n" +
                "                restorePos();\n" +
                "            }\n" +
                "            function exitMini() {\n" +
                "                if (!isMini) return;\n" +
                "                isMini = false;\n" +
                "                wrapper.classList.remove(\"mini-player\");\n" +
                "                clearPos();\n" +
                "                holder.style.height = \"0px\";\n" +
                "            }\n" +
                // A dedicated handle rather than dragging the whole box - the box is covered by
                // Vidstack's own controls (tap = play/pause, slider drags) that would fight a
                // drag gesture. Pointer capture keeps the drag tracking outside the handle.
                "            if (dragHandle) {\n" +
                "                var dragging = false, offX = 0, offY = 0;\n" +
                "                dragHandle.addEventListener(\"pointerdown\", function(e) {\n" +
                "                    var r = wrapper.getBoundingClientRect();\n" +
                "                    dragging = true;\n" +
                "                    offX = e.clientX - r.left;\n" +
                "                    offY = e.clientY - r.top;\n" +
                "                    dragHandle.setPointerCapture(e.pointerId);\n" +
                "                    e.preventDefault();\n" +
                "                });\n" +
                "                dragHandle.addEventListener(\"pointermove\", function(e) {\n" +
                "                    if (dragging) applyPos(e.clientX - offX, e.clientY - offY);\n" +
                "                });\n" +
                "                function endDrag(e) {\n" +
                "                    if (!dragging) return;\n" +
                "                    dragging = false;\n" +
                "                    try { dragHandle.releasePointerCapture(e.pointerId); } catch (x) {}\n" +
                "                    var r = wrapper.getBoundingClientRect();\n" +
                "                    try { localStorage.setItem(POS_KEY, JSON.stringify({ x: r.left, y: r.top })); } catch (x) {}\n" +
                "                }\n" +
                "                dragHandle.addEventListener(\"pointerup\", endDrag);\n" +
                "                dragHandle.addEventListener(\"pointercancel\", endDrag);\n" +
                "            }\n" +
                "            window.addEventListener(\"resize\", function() {\n" +
                "                if (isMini && wrapper.style.left) applyPos(parseFloat(wrapper.style.left), parseFloat(wrapper.style.top));\n" +
                "            });\n" +
                // Watches the sentinel, not the wrapper - the wrapper's own geometry changes the
                // instant mini-player toggles (position:fixed moves it back into view), which fed
                // straight back into this same observer and flickered enter/exit forever. The
                // sentinel's position only moves with page scroll, never with mini-player state,
                // so cause (scrolled past) and effect (wrapper repositioned) can't cross-trigger.
                "            var observer = new IntersectionObserver(function(entries) {\n" +
                "                if (entries[0].isIntersecting) exitMini(); else enterMini();\n" +
                "            }, { threshold: 0 });\n" +
                "            observer.observe(sentinel);\n" +
                "            player.addEventListener(\"fullscreen-change\", function() {\n" +
                "                if (player.state.fullscreen) exitMini();\n" +
                "            });\n" +
                "            if (closeBtn) closeBtn.addEventListener(\"click\", function() {\n" +
                "                player.pause();\n" +
                "                dismissed = true;\n" +
                "                exitMini();\n" +
                "                observer.disconnect();\n" +
                "            });\n" +
                "        }\n" +
                "        \n" +
                // Bilibili danmaku ("bullet comments") overlay. Scroll-type comments animate via
                // CSS `transform` (travel distance needs runtime-measured player/text width, not
                // known until render); top/bottom are fixed-position fades. getLastingTime()
                // always returns -1 (extractor bug) - duration hardcoded to Bilibili's typical
                // defaults instead.
                "        function initDanmakuOverlay(player, danmakuUrl) {\n" +
                "            var layer = document.getElementById(\"danmaku-layer\");\n" +
                "            var toggleBtn = document.getElementById(\"danmaku-toggle-btn\");\n" +
                "            if (!layer) return;\n" +
                // danmaku-layer is now rendered directly as a child of <media-player> itself
                // (which, unlike video.js's separate .el() vs outer-wrapper split, is the actual
                // fullscreen target here) - this relocation is likely a no-op in practice, but
                // kept as a harmless defensive no-op (re-appending an existing child just reorders
                // it) since the exact fullscreen-target element wasn't independently confirmed.
                "            var danmakuHome = layer.parentNode, danmakuNextSibling = layer.nextSibling;\n" +
                "            player.addEventListener(\"fullscreen-change\", function() {\n" +
                "                if (player.state.fullscreen) {\n" +
                "                    player.appendChild(layer);\n" +
                "                } else if (danmakuHome) {\n" +
                "                    danmakuHome.insertBefore(layer, danmakuNextSibling);\n" +
                "                }\n" +
                "            });\n" +
                "            var SCROLL_DURATION = 8, FIXED_DURATION = 4;\n" +
                "            var comments = [], nextIndex = 0, enabled = true;\n" +
                "            var scrollLaneUntil = new Array(14).fill(0);\n" +
                "            var topLaneUntil = new Array(4).fill(0);\n" +
                "            var bottomLaneUntil = new Array(4).fill(0);\n" +
                "            function pickLane(untilArr, lanes, now, dur) {\n" +
                "                for (var i = 0; i < lanes; i++) {\n" +
                "                    if (untilArr[i] <= now) { untilArr[i] = now + dur; return i; }\n" +
                "                }\n" +
                "                var idx = 0;\n" +
                "                for (var i = 1; i < lanes; i++) { if (untilArr[i] < untilArr[idx]) idx = i; }\n" +
                "                untilArr[idx] = now + dur;\n" +
                "                return idx;\n" +
                "            }\n" +
                "            function spawn(item) {\n" +
                "                var h = layer.clientHeight || 200;\n" +
                "                var w = layer.clientWidth || 800;\n" +
                "                var el = document.createElement(\"div\");\n" +
                "                el.className = \"danmaku-item \" + item.position;\n" +
                "                el.textContent = item.text;\n" +
                // Scaled by player WIDTH, not height - height-scaling produced 50-60px comments
                // covering most of the frame.
                "                el.style.fontSize = Math.max(14, Math.min(30, Math.round(w * item.size * 0.028))) + \"px\";\n" +
                "                el.style.color = item.color;\n" +
                "                var laneH = parseFloat(el.style.fontSize) * 1.5;\n" +
                "                var now = player.currentTime;\n" +
                "                if (item.position === \"top\" || item.position === \"bottom\") {\n" +
                "                    var lanes = Math.max(1, Math.min(4, Math.floor(h * 0.35 / laneH)));\n" +
                "                    var untilArr = item.position === \"top\" ? topLaneUntil : bottomLaneUntil;\n" +
                "                    var lane = pickLane(untilArr, lanes, now, FIXED_DURATION);\n" +
                "                    el.style[item.position] = (8 + lane * laneH) + \"px\";\n" +
                "                    el.style.animation = \"danmaku-fade \" + FIXED_DURATION + \"s linear\";\n" +
                "                    el.addEventListener(\"animationend\", function() { el.remove(); });\n" +
                "                    layer.appendChild(el);\n" +
                "                } else {\n" +
                "                    var lanes = Math.max(1, Math.floor(h / laneH));\n" +
                "                    var lane = pickLane(scrollLaneUntil, Math.min(14, lanes), now, SCROLL_DURATION);\n" +
                "                    el.style.top = (lane * laneH) + \"px\";\n" +
                "                    var startX = layer.clientWidth;\n" +
                "                    el.style.transform = \"translateX(\" + startX + \"px)\";\n" +
                "                    layer.appendChild(el);\n" +
                "                    var endX = -el.offsetWidth;\n" +
                "                    el.dataset.startX = startX; el.dataset.endX = endX; el.dataset.duration = SCROLL_DURATION;\n" +
                "                    requestAnimationFrame(function() {\n" +
                "                        el.style.transition = \"transform \" + SCROLL_DURATION + \"s linear\";\n" +
                "                        el.style.transform = \"translateX(\" + endX + \"px)\";\n" +
                "                    });\n" +
                "                    el.addEventListener(\"transitionend\", function() { el.remove(); });\n" +
                "                }\n" +
                "            }\n" +
                "            function tick() {\n" +
                "                if (!enabled || !comments.length) return;\n" +
                "                var t = player.currentTime;\n" +
                "                while (nextIndex < comments.length && comments[nextIndex].time <= t) {\n" +
                "                    if (t - comments[nextIndex].time < 1.2) spawn(comments[nextIndex]);\n" +
                "                    nextIndex++;\n" +
                "                }\n" +
                "            }\n" +
                "            function resync() {\n" +
                "                layer.innerHTML = \"\";\n" +
                "                scrollLaneUntil.fill(0); topLaneUntil.fill(0); bottomLaneUntil.fill(0);\n" +
                "                var t = player.currentTime;\n" +
                "                nextIndex = 0;\n" +
                "                while (nextIndex < comments.length && comments[nextIndex].time < t) nextIndex++;\n" +
                "            }\n" +
                "            player.addEventListener(\"time-update\", tick);\n" +
                "            player.addEventListener(\"seeking\", resync);\n" +
                "            player.addEventListener(\"pause\", function() {\n" +
                "                layer.classList.add(\"video-paused\");\n" +
                "                layer.querySelectorAll(\".danmaku-item.scroll\").forEach(function(el) {\n" +
                "                    var m = new DOMMatrixReadOnly(getComputedStyle(el).transform);\n" +
                "                    el.style.transition = \"none\";\n" +
                "                    el.style.transform = \"translateX(\" + m.m41 + \"px)\";\n" +
                "                    el.dataset.pausedX = m.m41;\n" +
                "                });\n" +
                "            });\n" +
                "            player.addEventListener(\"play\", function() {\n" +
                "                layer.classList.remove(\"video-paused\");\n" +
                "                layer.querySelectorAll(\".danmaku-item.scroll\").forEach(function(el) {\n" +
                "                    if (el.dataset.pausedX === undefined) return;\n" +
                "                    var startX = parseFloat(el.dataset.pausedX);\n" +
                "                    var endX = parseFloat(el.dataset.endX);\n" +
                "                    var totalDist = parseFloat(el.dataset.startX) - endX;\n" +
                "                    var remainDist = startX - endX;\n" +
                "                    var remainDur = totalDist > 0 ? parseFloat(el.dataset.duration) * (remainDist / totalDist) : 0;\n" +
                "                    delete el.dataset.pausedX;\n" +
                "                    if (remainDur <= 0) { el.remove(); return; }\n" +
                "                    requestAnimationFrame(function() {\n" +
                "                        el.style.transition = \"transform \" + remainDur + \"s linear\";\n" +
                "                        el.style.transform = \"translateX(\" + endX + \"px)\";\n" +
                "                    });\n" +
                "                });\n" +
                "            });\n" +
                "            if (toggleBtn) {\n" +
                "                toggleBtn.addEventListener(\"click\", function() {\n" +
                "                    enabled = !enabled;\n" +
                "                    toggleBtn.classList.toggle(\"off\", !enabled);\n" +
                "                    if (!enabled) layer.innerHTML = \"\";\n" +
                "                });\n" +
                "            }\n" +
                "            fetch(danmakuUrl)\n" +
                "                .then(function(res) { return res.json(); })\n" +
                "                .then(function(data) {\n" +
                "                    comments = (data.danmaku || []).sort(function(a, b) { return a.time - b.time; });\n" +
                "                    resync();\n" +
                "                })\n" +
                "                .catch(function() {});\n" +
                "        }\n" +
                "        \n";

    // SCRIPTS with its <script>/</script> wrapper stripped, served cacheably at /static/script.js.
    // Computed from SCRIPTS, not hand-duplicated, so the two can't drift.
    @JvmField
    val RAW_JS: String = SCRIPTS.substring(SCRIPTS.indexOf('\n') + 1, SCRIPTS.lastIndexOf("</script>"))
}
