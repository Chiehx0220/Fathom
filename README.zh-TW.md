<p align="center">
  <img src="Assets/fathom-banner.png" alt="Fathom:YouTube + Bilibili,同一個動態,零追蹤">
</p>

<p align="center">
  <a href="README.md">English</a> · <b>繁體中文</b>
</p>

<p align="center">
  Android 9+ · Kotlin · Jetpack Compose · GPL-3.0<br>
  從 A-EDev 的 <a href="https://github.com/A-EDev/Flow">Flow</a> fork 出來
</p>

---

## ⚡ 10 秒看完

- 📺 **YouTube 和 Bilibili 一個 App 搞定。** 一個搜尋、一個首頁、一個資料庫。
- 🧠 **推薦引擎就在你手機裡。** 由 [FlowNeuro](#-flowneuro) 驅動,資料不出裝置。
- 🔒 **不用帳號、沒有廣告、不追蹤。**
- 🌐 **手機直接變伺服器。** 手機上開 `localhost:8080`,或在同 Wi-Fi 的任何瀏覽器輸入手機的位址。
- 🔗 **Bilibili 連結直接在 App 開**,`b23.tv` 短連結也行。

## 🧠 FlowNeuro

每一則推薦都來自 **FlowNeuro**,A-EDev 為 Flow 打造的裝置端引擎。

- 從觀看、跳過、按讚、搜尋和停留時間學習
- 發現你對某個主題膩了,就混入新鮮內容
- 它知道你什麼,一清二楚,還能編輯、匯出、一鍵清除

**Fathom 加了什麼:** Bilibili 的觀看紀錄會訓練同一份個人檔案,中日文標題會被斷成主題。紀錄混著看,推薦依然一致。

## 🎬 原生 Bilibili

不是 WebView。Kotlin 用戶端直接呼叫 Bilibili 的網頁 API。

- 搜尋、頻道(投稿、系列、合集)、播放、畫質、多分 P
- 留言和 弹幕
- 訂閱、觀看紀錄、按讚、播放清單,和 YouTube 放在一起
- 還沒有:番劇、直播、付費內容
- 訪客最高 1080p,自備 cookie 可解鎖更多

## 🌐 本地網頁伺服器

開啟後,手機會在 8080 連接埠提供一個網頁 App。

- **在手機本機:** `http://localhost:8080`
- **同一個 Wi-Fi 的任何裝置:** `http://<手機IP>:8080`,對方不用裝任何東西

- 搜尋、頻道、播放清單、訂閱、觀看紀錄、稍後觀看
- [Vidstack](https://vidstack.io) 播放器:畫質選單、章節、進度條縮圖、字幕、純音訊、快捷鍵、續播
- YouTube 和 Bilibili 都能播,Bilibili 有彈幕

## 🔁 帶著資料搬家

- **匯入** NewPipe 或 PipePipe 的訂閱 JSON,Bilibili 頻道和頭像一併帶入。
- **匯出**同格式 JSON,可回到這兩個 App。

## 🛠 建置

```bash
git clone https://github.com/Chiehx0220/Flow.git
cd Flow && git checkout Btest
./gradlew assembleGithubDebug
```

目前沒有簽署版發行檔。變體:`foss`(無自動更新)、`nightly`(可與 debug 並存)。

## 🧩 程式碼結構

路徑都在 `app/src/main/java/io/github/aedev/flow/` 底下。

| 位置 | 內容 |
|---|---|
| `bilibili/` | 用戶端本體:API、請求簽章、工作階段、留言、彈幕、id、深層連結 |
| `data/paging/`、`player/stream/`、`ui/screens/playlists/`(`Bilibili*`) | 把它接進搜尋、播放和播放清單的轉換與銜接程式碼 |
| `di/BilibiliModule.kt` | 整個 App 共用一個工作階段 |
| `localserver/` | 網頁伺服器、它的 Bilibili 轉接層,以及 Vidstack 頁面 |
| 約 90 個上游檔案 | 小型掛鉤,多半是傳遞服務代碼 |

原則:新程式碼放新檔案,上游檔案只加一個呼叫。這樣從上游合併時衝突才會少。[FORK-DIFF.md](FORK-DIFF.md) 列出動過的每個上游檔案(`node scripts/fork-diff.js` 產生),`bilibili/PPE-REFERENCE.md` 記錄用戶端移植自 PipePipeExtractor 的哪個版本。

Flow 原有的一切(SponsorBlock、DeArrow、音樂、Shorts、下載、主題)都還在,詳見[上游](https://github.com/A-EDev/Flow#features)。

## 🙏 致謝

[Flow](https://github.com/A-EDev/Flow) 與 FlowNeuro,A-EDev · [PipePipeExtractor](https://github.com/InfinityLoop1308/PipePipeExtractor),InfinityLoop1308(Bilibili 用戶端的來源) · [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) · [localtube](https://github.com/diekaiju/localtube),diekaiju · [Vidstack](https://vidstack.io) 與 [dash.js](https://github.com/Dash-Industry-Forum/dash.js)

## 📄 授權

GPL-3.0。© 2025–2026 A-EDev(Flow),修改部分 © 2026 Chiehx0220。以此為基礎的程式碼,包含 FlowNeuro,必須以相同授權開源。
