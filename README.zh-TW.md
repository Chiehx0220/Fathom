<p align="center">
  <img src="Assets/fathom-icon.png" alt="Fathom" width="112" height="112">
</p>

<h1 align="center">Fathom</h1>

<p align="center">
  從 <a href="https://github.com/A-EDev/Flow">Flow</a> fork 出來的 Android YouTube 與 Bilibili 播放器,<br>
  同一個資料庫,推薦資料不會離開手機。
</p>

<p align="center">
  <b>由 FlowNeuro 驅動</b>,來自 Flow 的裝置端推薦引擎
</p>

<p align="center">
  Android 9+ · Kotlin · Jetpack Compose · GPL-3.0 · 不需帳號、沒有廣告、不做分析追蹤
</p>

<p align="center">
  <a href="README.md">English</a> · <b>繁體中文</b>
</p>

---

## 簡介

Fathom 是 Flow 的 fork。Flow 是一個帶有裝置端推薦引擎(FlowNeuro)的 YouTube 用戶端,這個 fork 為它加上第二個服務。Bilibili 的影片、頻道、留言和彈幕,和 YouTube 一起出現在搜尋、首頁、訂閱、觀看紀錄和播放清單裡,兩邊也餵給同一個本機推薦器。

在上游之上新增兩樣東西(推薦器本身是 [FlowNeuro](#powered-by-flowneuro),原理不變):

1. **原生 Bilibili 用戶端**:用 Kotlin 直接呼叫 Bilibili 的網頁 API,沒有 WebView,也不依賴擷取器函式庫。
2. **本地網頁伺服器**:手機上開一個伺服器,同網路的任何瀏覽器都能搜尋並播放你的資料庫內容。

上游原本的功能照舊。YouTube 仍然用 NewPipeExtractor,fork 的差異維持很小,方便持續合併。

<a id="powered-by-flowneuro"></a>
## 由 FlowNeuro 驅動

Fathom 的每一則推薦都來自 **FlowNeuro**,這是 A-EDev 為 Flow 打造的引擎。它完全在裝置上執行:沒有伺服器、沒有帳號、沒有遙測。

- 從你看什麼、跳過什麼、按讚、倒讚、搜尋什麼,以及看了多久來學習
- 區分平日與週末、早上與夜晚的習慣
- 察覺你對某個主題膩了,就混入新內容,避免首頁縮到只剩兩三個主題
- 把最近看過的影片轉成相關影片的銜接,並用互動比例過濾低品質影片
- 顯示它知道什麼、為什麼推薦某支影片,並讓你編輯、匯出、匯入或清除個人檔案

這個 fork 加的是涵蓋範圍:Bilibili 的觀看、按讚和搜尋會訓練同一份個人檔案,中文與日文標題會被斷成主題,所以 YouTube 與 Bilibili 混合的紀錄,依然能產出一致的推薦。

## 兩邊支援的功能

| | YouTube | Bilibili |
|---|:---:|:---:|
| 搜尋(影片、頻道、播放清單) | ✓ | ✓ |
| 首頁與相關影片 | ✓ | ✓ |
| 播放、畫質、續播 | ✓ | ✓ |
| 留言 | ✓ | ✓ |
| 彈幕 | | ✓ |
| 訂閱與動態更新 | ✓ | ✓ |
| 觀看紀錄、按讚、播放清單 | ✓ | ✓ |
| 頻道頁 | ✓ | ✓(投稿、系列、合集) |
| 從其他 App 開啟連結 | ✓ | ✓ |
| 本地網頁伺服器播放 | ✓ | ✓ |
| 音樂、Shorts | ✓ | |
| 番劇、直播、付費內容 | | 尚未支援 |

沒有登入 cookie 時,Bilibili 最高提供 1080p。你可以另外提供自己的 cookie 解除限制,其他功能都不需要帳號。

## 開啟 Bilibili 連結

在 Android 的應用程式設定裡讓 Flow 開啟支援的連結,下列連結會直接在 App 中開啟:

- `bilibili.com/video/BV…`(多分 P 影片可帶 `?p=`)
- `space.bilibili.com/<uid>`
- `b23.tv/…` 短連結,包含 Bilibili 分享頁面貼出的整段分享文字

## 本地網頁伺服器

開啟後,手機會在 8080 連接埠提供一個網頁 App。在同一個 Wi-Fi 的筆電、電視或另一支手機開啟 `http://<手機IP>:8080` 即可。

- 搜尋、頻道、播放清單、訂閱、觀看紀錄和稍後觀看,全部使用 App 自己的資料
- 以 [Vidstack](https://vidstack.io) 播放 DASH:畫質選單、進度條章節、縮圖預覽、字幕、純音訊版面、鍵盤與觸控快捷鍵、續播
- YouTube 與 Bilibili 都能播放,Bilibili 有彈幕疊層
- 串流由手機轉送,另一台裝置不用安裝任何東西

這個伺服器起點是 [localtube](https://github.com/diekaiju/localtube),之後圍繞原生 Bilibili 用戶端重新打造。

## 資料匯入與匯出

- **匯入** NewPipe 或 PipePipe 的訂閱 JSON。Bilibili 頻道保留服務代碼 5,並自動取得頭像。
- **匯出**寫出相同格式的 JSON,可以再匯回這兩個 App。
- 觀看紀錄與播放清單可從 NewPipe 備份匯入。PipePipe 的完整備份 ZIP 目前還不能讀取訂閱,請改用它的訂閱匯出。

## 繼承自 Flow 的功能

上游提供的功能全部保留:ExoPlayer 播放搭配 SponsorBlock、DeArrow 和 Return YouTube Dislike;背景播放、子母畫面、投放;含歌詞的音樂播放器;Shorts;下載;十一種主題;以及 FlowNeuro 的透明度儀表板和可匯出的個人檔案。完整清單請見[上游 README](https://github.com/A-EDev/Flow#features)。

## 建置

不提供簽署過的發行版本。請自行建置 debug APK:

```bash
git clone https://github.com/Chiehx0220/Flow.git
cd Flow
git checkout Btest
./gradlew assembleGithubDebug
```

變體:`foss` flavor 不含自動更新,`nightly` build type 會與 debug 版並存安裝(套件後綴 `.nightly`)。執行需要 Android 9(API 28)以上。

## fork 的程式碼結構

| 位置 | 內容 |
|---|---|
| `bilibili/` | Bilibili 用戶端:API、簽章、工作階段、留言、彈幕、id、連結解析 |
| `org/schabi/newpipe/localserver/` | 本地網頁伺服器與 Vidstack 頁面 |
| 上游檔案 | 只放小型掛鉤,多半是傳遞服務代碼 |

[FORK-DIFF.md](FORK-DIFF.md) 由 `git diff upstream/main` 產生(`node scripts/fork-diff.js`),列出 fork 動過的每一個上游檔案,合併衝突就出在這些檔案。`bilibili/PPE-REFERENCE.md` 記錄 Bilibili 用戶端移植自 PipePipeExtractor 的哪個版本,Bilibili 風控變動時可以對照後重新移植。

## 致謝

- [**Flow**](https://github.com/A-EDev/Flow),A-EDev:App、播放器和 FlowNeuro。這個專案大部分是他們的成果。想贊助的話,請支持上游。
- [**PipePipeExtractor**](https://github.com/InfinityLoop1308/PipePipeExtractor) 與 [PipePipe](https://codeberg.org/NullPointerException/PipePipe),InfinityLoop1308:Bilibili 用戶端的請求簽章與工作階段處理源自於此,服務代碼 5 也來自這裡。
- [**NewPipeExtractor**](https://github.com/TeamNewPipe/NewPipeExtractor):YouTube 擷取。
- [**localtube**](https://github.com/diekaiju/localtube),diekaiju:本地伺服器的起點。
- [**Vidstack**](https://vidstack.io) 與 [dash.js](https://github.com/Dash-Industry-Forum/dash.js):網頁播放器。

## 授權

與上游相同,採用 GPL-3.0。Flow 的版權為 © 2025–2026 A-EDev,修改部分為 © 2026 Chiehx0220。任何以此程式碼為基礎的專案,包含 FlowNeuro 引擎,都必須以相同授權開源。
