# 沉澱手記（InkJournal）

給自己用的手寫日記 / 心情紀錄 / 臨時實驗紀錄 App。只做一件事：**用 S Pen 在深色的紙上寫字**。
以 Samsung Galaxy Tab S7 FE + S Pen 為主要裝置調整。

## 功能

- **主頁**：每本筆記顯示第一頁的預覽，點一下直接進去寫；第一格「＋ 新筆記」建立新的一本（標題預設是今天日期）。
  長按筆記可以重新命名或刪除。右上角齒輪是設定。
- **書寫**：實心原子筆的感覺（固定粗細、圓頭）。工具列有三種粗細、三個顏色、橡皮擦、復原、重做。
  - 一次只開一本筆記；一本筆記可以有很多頁（A4 比例，上下捲動）。
  - **長按**工具列上的顏色或粗細，可以直接調整那一格。
  - 按住 **S Pen 側鍵**寫 = 橡皮擦（整筆擦掉）。
  - 一指捲動、兩指縮放（0.35×～5×），點兩下回到符合頁寬。
  - S Pen 靠近螢幕時，手掌、手指碰到螢幕都不會有反應。
- **每本筆記的設定**（工具列最右邊的調整圖示）：背景（空白 / 橫線 / 方格）與行距、格子大小；
  這本筆記的三色三粗細；刪除目前這一頁。整本每一頁都用同一組設定，不同筆記可以不同。
- **齒輪設定**：新筆記預設的三色三粗細與背景、手指能不能寫字、匯出/匯入備份檔。
  「自動上傳到雲端」先保留位置，之後再開發。
- **向量格式**：筆跡存的是座標點（公釐），放大不會糊。格式規格見 [docs/FORMAT.md](docs/FORMAT.md)。

預設配色（深色紙面 `#1C1F24`）：米白 `#ECE6DC`、霧藍 `#8DB8E8`、琥珀 `#F0B46E`；粗細 0.4 / 0.7 / 1.1 mm。

## 技術架構

原生 Android（Kotlin + Jetpack Compose），不是網頁殼。手寫最在意延遲，所以用 Google 的 **Jetpack Ink**：

- 正在寫的筆跡：`InProgressStrokesView` 用**前緩衝**直接畫到螢幕（跳過一般的畫面排程），
  再加上 `androidx.input:motionprediction` 預測筆尖位置，盡量讓筆跡貼著筆尖。
- 寫完的筆跡：每頁每 64 筆錄成一個 `RenderNode`（GPU 顯示清單），捲動、縮放只要重播；
  縮放結束後分幾個畫格用新倍率重錄，維持邊緣清晰。離畫面 3 頁以上的頁面會釋放記憶體。
- 存檔：每次停筆約 1.2 秒後自動存；離開筆記、App 進背景時立刻存。所有寫檔排在同一條執行緒，依序完成。

```
app/src/main/java/com/haohao/inkjournal/
├── MainActivity.kt          三個畫面切換（主頁 / 筆記 / 設定）
├── InkJournalApp.kt         共用的資料庫物件、寫檔執行緒
├── data/
│   ├── Models.kt            頁面尺寸、背景、三色三粗細、筆劃原始資料
│   ├── NoteFormat.kt        .hwik 頁面檔與 note.json 的讀寫
│   ├── NoteRepository.kt    筆記的建立/讀寫/刪除、備份 zip 匯出匯入
│   └── SettingsRepository.kt 全域設定
├── editor/
│   ├── NoteCanvasView.kt    觸控分派：S Pen 寫字/橡皮擦、手掌忽略、手指捲動縮放
│   ├── DryInkView.kt        紙面、背景線、已完成筆跡（RenderNode 快取）
│   ├── NoteSession.kt       打開中的筆記：復原/重做、頁面、自動存檔
│   ├── Viewport.kt          公釐 ↔ 螢幕像素換算（純計算，有測試）
│   ├── EraserHit.kt         筆劃橡皮擦的碰撞判斷（純計算，有測試）
│   ├── InkConvert.kt        自訂格式 ↔ Jetpack Ink 筆劃
│   └── PagePainter.kt       顏色、橫線/方格、主頁預覽圖
└── ui/                      Compose 畫面：主頁、筆記工具列、設定、選色/粗細對話框
```

## 建置

環境沿用 Grad Daily 那台電腦的設定：JDK 21（Android Studio 內建）、Android SDK 36、Gradle 8.14.3、AGP 8.13、Kotlin 2.2.20。

```powershell
$env:JAVA_HOME = "E:\Android\AndroidStudio\jbr"
cd Z:\VScode\writing_app
.\gradlew.bat testDebugUnitTest      # 單元測試
.\gradlew.bat assembleRelease        # 打包
```

產出：`app\build\outputs\apk\release\app-release.apk`（最新版也會複製到根目錄 `inkjournal.apk`）。

- 平常安裝用 **release** 版：有 R8 最佳化，Compose 介面和書寫都比 debug 版順很多。
  release 用跟 Grad Daily 同一把 debug 金鑰（`E:/Android/.android/debug.keystore`）簽章，之後覆蓋安裝不會跳「套件衝突」。
- debug 版的套件名稱是 `com.haohao.inkjournal.debug`，可以跟正式版同時裝在平板上測試。

### 裝到平板

1. 把 `inkjournal.apk` 傳到平板（USB、Quick Share 或雲端硬碟都可以），點開安裝（第一次要允許「安裝未知應用程式」）。
2. 或開 USB 偵錯後：`E:\Android\Sdk\platform-tools\adb.exe install -r inkjournal.apk`

### 模擬器

`E:\Android\.android\avd\tab_test`：2560×1600、Android 14（跟 S7 FE 一樣），用來看畫面。
模擬器沒有 S Pen，也沒有真的前緩衝硬體，**筆的手感一定要在平板上試**。

```powershell
$env:ANDROID_AVD_HOME = "E:\Android\.android\avd"
E:\Android\Sdk\emulator\emulator.exe -avd tab_test
```

## 資料與備份

- 筆記存在 App 私有資料夾，解除安裝會一起刪掉。**重灌或換平板前，先到設定 →「匯出備份檔」**。
- 已開啟 Android 的自動備份（`allowBackup`），Google 帳號的裝置備份也會包含筆記。
