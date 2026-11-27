# 隨身口譯 Pocket Interpreter

<img src="store/icon-512.png" width="96" align="right" alt="icon">

開放原始碼的 Android 翻譯 App：文字、語音、面對面對話翻譯，加上旅遊常用語。
**沒有廣告、沒有分析追蹤、不需要帳號。**

An open-source Android translator for text, voice and face-to-face conversations. No ads, no tracking, no account.

## 功能

| 分頁 | 說明 |
|---|---|
| 文字 | 輸入文字 → 翻譯 → 朗讀、複製 |
| 語音 | 按麥克風說話 → 辨識 → 翻譯 → 自動朗讀 |
| 對話 | 手機平放在兩人中間，上下兩個面板各朝一人；一方說話，翻譯出現在另一方那面並唸出來，可「再唸一次」、翻轉上方面板、交換語言 |
| 常用語 | 28 句旅遊常用語，點一下就翻譯並朗讀 |

支援 41 種語言（中文繁體／簡體、英、日、韓、泰、越、印尼、法、德、西……）。

## 翻譯引擎

1. **Google ML Kit 離線翻譯**（主要）：在手機上翻譯，文字不離開手機。每種語言第一次使用時自動下載約 30MB 模型，之後不需網路。ML Kit 只輸出簡體中文，App 再用 Android 內建的 ICU（`Hans-Hant`）轉成繁體（Android 10 以上）。
2. **[MyMemory](https://mymemory.translated.net/)**（備援）：模型下載中，或 ML Kit 不支援的語言（緬甸文、高棉文、寮文、尼泊爾文、蒙古文）才使用。免金鑰，每個使用者每天約 5,000 字的免費額度。

語音辨識與朗讀使用手機內建的 `SpeechRecognizer` 與 `TextToSpeech`。

本專案**不含任何 API key**。

## 隱私權

權限只有麥克風與網路。詳見 [隱私權政策](https://eddiechen1234.github.io/pocket-interpreter/privacy.html)（原始檔在 [`docs/privacy.html`](docs/privacy.html)）。

## 編譯

需要 JDK 17、Android SDK（platform 36）與 Gradle 8.14 以上：

```bash
gradle :app:assembleRelease   # APK（沒有設定上傳金鑰時用 debug 金鑰簽章）
gradle :app:bundleRelease     # AAB（Google Play 用）
```

每次 push 到 `main`，GitHub Actions 會自動產出 AAB 與 APK（在 Actions 的 Artifacts 下載）。`versionCode` 自動使用 Actions 的執行編號。

### Google Play 上傳金鑰

在 repo 的 **Settings → Secrets and variables → Actions** 設定：

| Secret | 內容 |
|---|---|
| `KEYSTORE_B64` | 上傳金鑰 `.jks` 檔的 base64 |
| `KEYSTORE_PASS` | keystore 密碼 |
| `KEY_ALIAS` | 金鑰別名（未設定時為 `upload`） |
| `KEY_PASS` | 金鑰密碼（未設定時同 `KEYSTORE_PASS`） |

金鑰檔與密碼絕對不要放進 repo（`.gitignore` 已排除 `*.jks`、`*.keystore`）。

## 專案結構

```
app/src/main/java/.../MainActivity.java   全部程式（純 Android framework UI＋ML Kit）
app/src/main/res/                         圖示、字串
docs/                                     隱私權政策（GitHub Pages）
store/                                    Google Play 商店圖片與文案
.github/workflows/build.yml               CI：編譯 AAB / APK
```

## 授權

[Apache License 2.0](LICENSE)
