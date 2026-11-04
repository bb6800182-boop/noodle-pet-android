---
version: alpha
name: noodle-pet-android
description: "拉麵手機版的介面：沿用拉麵（Windows 桌寵）的 Linear 深色系 token，手機版只多一個觸控尺寸。麵條角色、垂降的那條麵條是插畫素材，不受 UI token 約束。"

colors:
  primary: "#5e6ad2"
  on-primary: "#ffffff"
  ink: "#f7f8f8"
  ink-muted: "#d0d6e0"
  ink-subtle: "#8a8f98"
  canvas: "#010102"
  surface-1: "#0f1011"
  surface-2: "#141516"
  surface-3: "#18191a"
  hairline: "#23252a"
  hairline-strong: "#34343a"
  semantic-success: "#27a644"
  semantic-danger: "#eb5757"

typography:
  headline:
    fontFamily: sans-serif
    fontSize: 28px
    fontWeight: 600
    lineHeight: 1.20
  card-title:
    fontFamily: sans-serif
    fontSize: 22px
    fontWeight: 500
    lineHeight: 1.25
  body:
    fontFamily: sans-serif
    fontSize: 16px
    fontWeight: 400
    lineHeight: 1.50
  body-sm:
    fontFamily: sans-serif
    fontSize: 14px
    fontWeight: 400
    lineHeight: 1.50
  caption:
    fontFamily: sans-serif
    fontSize: 12px
    fontWeight: 400
    lineHeight: 1.40
  eyebrow:
    fontFamily: sans-serif
    fontSize: 13px
    fontWeight: 500
    lineHeight: 1.30
  button:
    fontFamily: sans-serif
    fontSize: 14px
    fontWeight: 500
    lineHeight: 1.20

rounded:
  xs: 4px
  sm: 6px
  md: 8px
  lg: 12px
  pill: 9999px

spacing:
  xxs: 4px
  xs: 8px
  sm: 12px
  md: 16px
  lg: 24px
  xl: 32px
  touch: 44px

components:
  bubble:
    backgroundColor: "{colors.surface-1}"
    textColor: "{colors.ink}"
    typography: "{typography.body-sm}"
    rounded: "{rounded.lg}"
    padding: 16px
  settings-group:
    backgroundColor: "{colors.surface-1}"
    textColor: "{colors.ink}"
    typography: "{typography.body-sm}"
    rounded: "{rounded.lg}"
    padding: 12px 16px
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    typography: "{typography.button}"
    rounded: "{rounded.md}"
    padding: 8px 16px
    height: 44px
  button-secondary:
    backgroundColor: "{colors.surface-1}"
    textColor: "{colors.ink}"
    typography: "{typography.button}"
    rounded: "{rounded.md}"
    padding: 8px 16px
    height: 44px
  button-danger:
    backgroundColor: "{colors.surface-1}"
    textColor: "{colors.semantic-danger}"
    typography: "{typography.button}"
    rounded: "{rounded.md}"
    padding: 8px 16px
    height: 44px
  text-input:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.body-sm}"
    rounded: "{rounded.md}"
    padding: 12px
  segment-selected:
    backgroundColor: "{colors.surface-3}"
    textColor: "{colors.ink}"
    typography: "{typography.button}"
    rounded: "{rounded.pill}"
    padding: 6px 14px
  chip-selected:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    typography: "{typography.button}"
    rounded: "{rounded.pill}"
    padding: 4px 12px
  tab-selected:
    backgroundColor: "{colors.surface-3}"
    textColor: "{colors.ink}"
    typography: "{typography.caption}"
    rounded: "{rounded.pill}"
---

## Overview

和拉麵（Windows 桌寵）同一套深色 token：canvas 打底、surface 階梯分層、hairline 框線，lavender `{colors.primary}` 只用在主要按鈕、多選小膠囊與開關的開啟狀態。程式裡的 token 在 `app/src/main/java/com/c520/lamian/ui/Theme.kt`（`T`），系統對話框用的同值顏色在 `res/values/themes.xml`。

## 手機版的差異

- **觸控尺寸** `{spacing.touch}` 44px（Android 上是 44dp）：按鈕、開關、分頁、小膠囊的可點區域至少這麼大。分段選擇本身 44dp 高（外框 2dp ＋ 選項 40dp）。
- **字型**：用系統字型（中文是 Noto Sans CJK TC），字級用 sp，跟著系統的字型大小設定放大；數字（倒數、次數、提醒時間）開 tabular-nums 等寬數字。
- **寬控制項放在標題下面**：設定裡的分段選擇、角色選擇放在標題下一行，放大字體也不會擠在同一列。
- **時間、日期**：用系統原生的選擇器（`Theme.Lamian.Dialog`：surface-1 底、primary 強調色）。

## 懸浮的麵條與泡泡

- 泡泡是 surface-1 卡片配 hairline-strong 框線、圓角 lg，不加陰影，疊在任何 App 上都看得清楚；尖角指向麵條。
- 泡泡用 BOTTOM 對齊麵條頭頂，展開「怎麼做」時往上長；橫向螢幕放不下就在泡泡裡捲動。
- 系統開了「移除動畫」時，麵條不走路、不垂降、不探頭，直接出現；做完的「跳一下」也會變成瞬間完成。

## 補充的 token

- `{colors.semantic-danger}` #eb5757：輸入錯誤、刪除。在 surface-1 上對比 5.4:1。
- 紀錄裡「完成／喝了」的小膠囊：success 加 15% 不透明度當底（success-tint），字仍用 ink。
- 插畫用色（不是介面色）：垂降那條麵條 #edbb5f（`T.illNoodle`），和角色圖同色系；喝水動作的杯子、水在精靈圖裡。

## 對比（WCAG AA）

- 內文 ink、ink-muted、ink-subtle 在 canvas、surface-1～3 上都 ≥ 5.4:1。
- 主要按鈕白字在 primary 上 4.7:1。
- 開關關閉時用 ink-subtle 外框與圓點，元件邊界才有 3:1（hairline-strong 只有 1.5:1）。
