<div align="center">
  <img src="Assets/logo.png" alt="Flow Logo" width="140" height="140">
  <br><br>

  <div align="center">

<img src="https://img.shields.io/badge/Status-Active_Development-success?style=for-the-badge&logo=github-actions">
<img src="https://img.shields.io/badge/Fork_of-A--EDev%2FFlow-blue?style=for-the-badge&logo=github">

<br>

<!-- Tech Stack -->
<img src="https://img.shields.io/badge/Platform-Android_8.0+-3DDC84?style=for-the-badge&logo=android&logoColor=white">
<img src="https://img.shields.io/badge/Kotlin-100%25-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white">
<img src="https://img.shields.io/badge/Compose-Material_3-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white">

<br>

<a href="LICENSE">
  <img src="https://img.shields.io/badge/License-GPL_v3.0-blue?style=for-the-badge&logo=gnu-bash&logoColor=white">
</a>
<img src="https://img.shields.io/github/last-commit/Chiehx0220/Flow?style=for-the-badge&color=red">

</div>

  <br><br>

  <h3>Flow, plus Bilibili.</h3>
  <p>
    A fork of <a href="https://github.com/A-EDev/Flow"><b>Flow</b></a> — the privacy-respecting YouTube and YouTube Music client for Android — that adds native Bilibili support alongside everything Flow already does.<br>
    Search, watch, subscribe to, and get local recommendations for both YouTube and Bilibili in one app, with no account and no tracking on either side.
  </p>

  <p>
    <a href="#building-from-source"><b>Build from Source</b></a> ·
    <a href="#whats-different-in-this-fork"><b>What's Different</b></a> ·
    <a href="https://github.com/A-EDev/Flow"><b>Upstream Flow</b></a> ·
    <a href="#-support-development"><b>Support Upstream Flow</b></a>
  </p>
</div>

---

## Why this fork?

Flow already does the hard part: a real recommendation engine that runs entirely on-device, no accounts, no tracking. The one thing missing for me was Bilibili — I watch a mix of YouTube and Bilibili content and wanted one app that could search, play, and recommend across both instead of switching apps.

This fork adds Bilibili as a first-class service throughout the app, on top of a custom extractor ([Chiehx0220/PipePipeExtractor](https://github.com/Chiehx0220/PipePipeExtractor), itself a fork of [PipePipeExtractor](https://github.com/InfinityLoop1308/PipePipeExtractor)), while staying merge-compatible with upstream Flow so it can keep picking up upstream's own YouTube/FlowNeuro improvements over time.

---

## What's different in this fork

- **Bilibili search** — video, channel, and playlist results alongside YouTube's
- **Bilibili channel pages** — videos and playlists tabs, subscriber count, avatar
- **Bilibili playback** — native extraction (not a web view), quality selection, resume position
- **Bilibili comments and bullet comments (弹幕)** — including bangumi (premium series) episodes
- **Bilibili subscriptions** — RSS-backed where available, falling back to the channel-tabs path where it isn't
- **Bilibili watch history and likes**, kept alongside YouTube's in the same library
- **NewPipe-format import/export** correctly tags Bilibili subscriptions and history entries with their own service id, instead of silently defaulting to YouTube
- A YouTube/Bilibili source switcher on the relevant screens (search, home feed) so either service can be filtered in or out
- **A built-in local web server** — browse, search, and watch (YouTube and Bilibili, including danmaku/bullet comments) from any browser on the same network, no app install needed on the other device. Ported over from [localtube](https://github.com/diekaiju/localtube), a separate self-hosted NewPipeExtractor-based server project — see [Acknowledgments](#acknowledgments).

Everything below this is Flow itself, unchanged, and applies equally to Bilibili where the two features overlap (history, likes, playlists, the recommendation engine).

---

## Why Flow?

Most open-source YouTube clients give you playback but no way to discover new content. You either use the official app and get tracked, or you use an alternative and lose recommendations entirely.

Flow gives you both. The recommendation engine learns what you like by analyzing your watch behavior locally. It never leaves your device. You can inspect everything it knows about you, adjust it, or wipe it at any time.

---

## Features

### Video
- High-quality playback via ExoPlayer (Media3) with resolution switching (1080p, 720p, 480p, 360p)
- SponsorBlock — automatically skips sponsors, intros, outros, and filler
- DeArrow — replaces clickbait thumbnails and titles with community-sourced alternatives
- Return Youtube Dislike (RYD)
- Background playback — listen to audio with the screen off
- Picture-in-Picture — keep watching while using other apps
- Casting to smart TVs and streaming devices
- Playback speed control (0.25x to 2x)
- Video chapters with seek jumping
- Gesture controls for brightness, volume, and seeking
- Subtitles with customizable font size, color, and background
- Downloads with VP9, AV1, and standard format support
- Resume playback from where you left off

### Music
- Dedicated music player with album art and audio visualizations
- Queue management with add, remove, and reorder
- Shuffle and repeat (single/all)
- Persistent mini player across the app
- Synchronized lyrics display
- Fetches tracks from YouTube Music

### Recommendations (FlowNeuro Engine)
- Runs 100% on-device — no server, no telemetry, no account needed
- Learns from what you watch, skip, like, dislike, search for, and how long you watch
- Distinguishes weekday and weekend patterns, morning and night preferences
- Detects when you're getting bored of a topic and mixes in new content
- Prevents your feed from collapsing into the same 2-3 topics
- Surfaces related videos from your recent watches to create natural topic transitions
- Uses engagement signals (like-to-view ratios) to filter out low-quality content
- Full transparency dashboard — see what the algorithm knows and why it recommended something
- Export/import your entire recommendation profile as a file

### Library
- Local watch history
- Favorites and custom playlists
- Shorts feed with bookmarking
- Continue watching shelf
- Subscription management with cached feeds

### Local Server *(fork-only)*
- Runs a local web server on your device — open its address from a browser on any other device on the same Wi-Fi, no app or account needed there
- Search, browse channels, and watch — YouTube and Bilibili both — including comments and Bilibili's bullet comments (danmaku)
- Subscriptions, watch history, and watch-later stay in sync with the app
- Ported from [localtube](https://github.com/diekaiju/localtube), a separate self-hosted server project — see Acknowledgments

### Privacy
- No Google or Bilibili account required
- No ads, analytics, or tracking
- All data stored locally on your device
- Import subscriptions and history from NewPipe
- Export or delete everything at any time

### Appearance
- 11 themes: Light, Dark, OLED Black, Ocean Blue, Forest Green, Sunset Orange, Purple Nebula, Midnight Black, Rose Gold, Arctic Ice, Crimson Red
- Built entirely with Jetpack Compose and Material 3

---

## Screenshots

<div align="center">
  <table>
    <tr>
      <td align="center"><b>Home Feed</b><br><img src="Assets/Home.jpeg" width="240"></td>
      <td align="center"><b>Video Player</b><br><img src="Assets/VideoPlayer.jpeg" width="240"></td>
      <td align="center"><b>Personality Screen</b><br><img src="Assets/Personality.jpeg" width="240"></td>
    </tr>
    <tr>
      <td align="center"><b>Music Player</b><br><img src="Assets/MusicPlayer.jpeg" width="240"></td>
      <td align="center"><b>Music Hub</b><br><img src="Assets/Music.jpeg" width="240"></td>
      <td align="center"><b>Your Library</b><br><img src="Assets/Library.jpeg" width="240"></td>
    </tr>
    <tr>
      <td align="center"><b>Shorts</b><br><img src="Assets/Shorts.jpeg" width="240"></td>
      <td align="center"><b>Subscriptions</b><br><img src="Assets/Subscriptions.jpeg" width="240"></td>
      <td align="center"><b>Channel View</b><br><img src="Assets/Channel.jpeg" width="240"></td>
    </tr>
    <tr>
      <td align="center"><b>Artist Page</b><br><img src="Assets/Artist.jpeg" width="240"></td>
      <td align="center"><i>Bilibili search — screenshot coming soon</i></td>
      <td align="center"><i>Bilibili channel page — screenshot coming soon</i></td>
    </tr>
  </table>
</div>

---

<a id="building-from-source"></a>
## Building from Source

This fork doesn't currently publish signed release builds — upstream's release workflow needs a signing key this fork doesn't have, so the reliable way to run it today is to build it yourself:

```bash
git clone https://github.com/Chiehx0220/Flow.git
cd Flow
git checkout pipepipe
./gradlew assembleGithubDebug
```

The resulting APK is unsigned/debug-signed and installable directly on a device with developer options enabled.

### Requirements
**Minimum Requirement:** Android 8.0+

---

<a id="support--donations"></a>
## 💰 Support Development

**This fork is a personal project with no donation channel of its own.** The section below supports **upstream Flow**, the project this fork builds on — not this fork specifically. If you'd like to support the Bilibili work here instead, contributions and issue reports on [this repo](https://github.com/Chiehx0220/Flow) are the way to do that.

Flow (upstream) is a free and open-source project. As an independent developer without traditional banking access, its author relies on community support to keep it going.

<a href="https://patreon.com/A_EDev" target="_blank" rel="noreferrer noopener">
  <img src="https://img.shields.io/badge/Patreon-Support_Flow-FF424D?style=for-the-badge&amp;logo=patreon&amp;logoColor=white" alt="Support Flow on Patreon">
</a>

<br>

**Prefer to send Crypto directly?** These wallets belong to **A-EDev**, upstream Flow's author:

| Coin | Network | Address |
| :--- | :--- | :--- |
| **USDT** | TRC20 (Tron) | `TRz7VDrTWwCLCfQmYBEJakqcZgbFNWfUMP` |
| **Bitcoin** | BTC | `bc1qgmkkxxvzvsymtpfazqfl93jw6k4jgy0xmrtnv8` |
| **Ethereum** | ERC-20 | `0xfbac6f464fec7fe458e318971a42ba45b305b70e` |
| **Solana** | SOL | `7b3SLgiVPb8qQUvERSPGRWoFoiGEDvkFuY98M1GEngug` |
| **Monero** | XMR | `8AgaxZnpEvT8VXJpczpL7BQejwSEw97saJmKYqq4zKErbe9bkYSwUhJ813msPPbdYhF11oz4N7tfEj4Zi6k27fKD83ca1if` |

---

<a id="acknowledgments"></a>
## 🙏 Acknowledgments

This fork stands on the shoulders of:

*   **[Flow](https://github.com/A-EDev/Flow)** by A-EDev: The app this fork is built on — the player, FlowNeuro recommendation engine, and YouTube/Music experience are theirs.
*   **[PipePipeExtractor](https://github.com/InfinityLoop1308/PipePipeExtractor)** by InfinityLoop1308: The NewPipeExtractor fork that added Bilibili and NicoNico support, and the base this fork's own [extractor fork](https://github.com/Chiehx0220/PipePipeExtractor) builds on.
*   **[NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor)** / **[NewPipe](https://github.com/TeamNewPipe/NewPipe)**: The extraction library and app this whole lineage descends from.
*   **[localtube](https://github.com/diekaiju/localtube)** by diekaiju: The self-hosted NewPipeExtractor-based web server this fork's built-in Local Server feature was ported from.
*   **[PipePipe](https://codeberg.org/NullPointerException/PipePipe)** and its [developer docs](https://priveetee.github.io/Docs-PipePipe/): SABR and InnerTube playback reference that guided Flow's YouTube streaming pipeline.
*   **[MetroList](https://github.com/MetrolistGroup/Metrolist)** and **[LibreTube](https://github.com/LibreTube/LibreTube)**: Design and feature inspiration credited by upstream Flow.
*   **[ExoPlayer](https://github.com/google/ExoPlayer)**, **[Jetpack Compose](https://developer.android.com/jetpack/compose)**, **[Material Design 3](https://m3.material.io/)**.

---

## 📄 License & Copyright

Like upstream Flow, this fork is Free Software distributed under the **GNU General Public License v3 (GPLv3)**.

Copyright © 2025-2026 A-EDev (original Flow).
Modifications and Bilibili integration Copyright © 2026 Chiehx0220.

> 🚨 **For Developers:**
> This license requires that any project using this code (including the `FlowNeuroEngine` algorithm) must also be **Open Source** under the GPLv3 license. You may not use this code in a proprietary or closed-source application.

---

<div align="center">
  <sub>A personal fork of <a href="https://github.com/A-EDev/Flow">Flow</a> — most of the credit belongs there.</sub>
</div>
