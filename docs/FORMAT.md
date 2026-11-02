# 沉澱手記的筆記格式（HWIK v1）

筆跡全部以**向量**保存：每一筆記錄的是筆尖經過的座標點（單位：公釐），不是圖片。
畫面上顯示時才依照當下的縮放倍率畫出來，所以放大多少倍都不會糊。

## 資料夾結構

App 私有資料夾（`/data/data/com.haohao.inkjournal/files/`）底下：

```
settings.json                     齒輪裡的全域設定
notes/<筆記id>/note.json          筆記標題、時間、整本設定、頁面順序
notes/<筆記id>/pages/<頁id>.hwik  每一頁的筆跡（本文件下半部）
notes/<筆記id>/thumb.png          主頁預覽圖（快取，可隨時重產生，不進備份）
```

- 筆記 id 例：`n20261102153000_ab12`（建立時間 + 4 碼亂數）
- 頁 id 例：`p3f9a0c1d2e`
- id 只允許英數字、`_`、`-`，最多 64 字。

## note.json

```json
{
  "format": "inkjournal-note",
  "version": 1,
  "id": "n20261102153000_ab12",
  "title": "2026/11/02（一）",
  "createdAt": 1793611800000,
  "updatedAt": 1793612400000,
  "page": { "widthMm": 210, "heightMm": 297 },
  "background": { "type": "lined", "lineSpacingMm": 8, "gridSpacingMm": 6 },
  "pens": {
    "colors": ["#FFECE6DC", "#FF8DB8E8", "#FFF0B46E"],
    "sizesMm": [0.4, 0.7, 1.1],
    "selectedColor": 0,
    "selectedSize": 1
  },
  "pages": ["p3f9a0c1d2e", "p77b1e0aa45"],
  "lastPage": 0
}
```

| 欄位 | 說明 |
|---|---|
| `background.type` | `blank`（空白）、`lined`（橫線）、`grid`（方格） |
| `lineSpacingMm` | 橫線行距，5–16 mm |
| `gridSpacingMm` | 方格邊長，3–12 mm |
| `pens` | 這本筆記的三色三粗細（建立時從全域設定複製，之後可以各自調整） |
| `pages` | 頁面順序 |
| `lastPage` | 上次看到第幾頁（0 起算） |

讀取時遇到超出範圍的數值會自動夾回合理範圍；不認得的欄位會被忽略。

## 頁面檔 `.hwik`

### 檔頭（12 bytes，不壓縮，big-endian）

| 位移 | 型別 | 內容 |
|---|---|---|
| 0 | 4 bytes | ASCII `HWIK` |
| 4 | u16 | 版本，目前 `1` |
| 6 | u16 | 旗標：bit0 = 內容有用 Deflate（zlib）壓縮 |
| 8 | u32 | 保留，填 0 |

### 內容（檔頭之後，旗標 bit0 為 1 時是 zlib 壓縮串流，big-endian）

```
f32  頁寬 mm（210）
f32  頁高 mm（297）
i32  筆劃數 N
N 筆劃，依照畫的先後順序（後面的蓋在前面上面）：
  u8   筆刷種類   0 = 原子筆（固定粗細、圓頭）
  u8   工具       0 未知 / 1 滑鼠 / 2 手指 / 3 觸控筆
  u8   額外通道   bit0 筆壓、bit1 傾斜、bit2 方向
  u8   保留
  i32  顏色 ARGB
  f32  粗細 mm（筆跡直徑）
  f32  epsilon mm（幾何精細度，目前 0.015）
  f32  stroke unit length cm（0 = 未知）
  i32  點數 n
  f32 × n  x（頁面左上角起算，mm）
  f32 × n  y
  i32 × n  時間（從這一筆開始算的毫秒）
  [f32 × n 筆壓 0–1]      ← 有 bit0 才有
  [f32 × n 傾斜 弧度]      ← 有 bit1 才有
  [f32 × n 方向 弧度]      ← 有 bit2 才有
```

同一個欄位的值連續存放（先全部 x、再全部 y…），壓縮效果比較好。
一頁寫滿中文大約幾百 KB。

### 為什麼存時間與筆壓

畫面上的筆跡由 Jetpack Ink 依照「點 + 時間」平滑計算出形狀。存下原始輸入，重新打開時算出來的形狀跟寫的當下完全一樣。
筆壓、傾斜目前的原子筆沒有用到，但存著之後要加有筆壓的筆就不用換格式。

## 備份檔（.zip）

```
backup.json                         {"format":"inkjournal-backup","version":1,"exportedAt":…,"appVersion":"1.0.0"}
settings.json
notes/<筆記id>/note.json
notes/<筆記id>/pages/<頁id>.hwik
```

匯入時同一本筆記（同 id）以 `updatedAt` 比較新的為準；路徑不符合上面格式的檔案一律忽略。
之後的雲端備份會直接上傳這個 zip。
