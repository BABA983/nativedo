# Phase 3 Cells / Media / Rendering Substrate

## Scope

- `RecyclerListView.kt`
- `BaseCell.kt`
- `TopicCell.kt`
- `UserCell.kt`
- `DividerCell.kt`
- `LoadingCell.kt`
- `BottomSheet.kt`
- `AvatarDrawable.kt`
- `ImageReceiver.kt`
- `CookedHtmlRenderer.kt`

## Upstream References

- `org.telegram.ui.Components.RecyclerListView`
- `org.telegram.ui.Cells.*`
- `org.telegram.ui.Components.BottomSheet`
- `org.telegram.ui.Components.AvatarDrawable`
- `org.telegram.messenger.ImageReceiver`
- Telegram `ArticleViewer` / Instant View 渲染思路

## Prerequisites

- `Phase 1` complete
- `Phase 2` navigation gate complete
- `Phase 4` 的 fixture / contract 输入已可供 HTML renderer 使用

## Exit Gates

- 列表、图片、渲染 substrate 用 mock host / smoke screen 验证，不强行补 standalone unit test
- `CookedHtmlRenderer` 必须达到并不低于旧 parser 的 block coverage
- `RawHtml` fallback 只保留给 genuinely unknown HTML，不允许把正常结构偷懒回退到 WebView
- `ImageReceiver` / WebView / renderer host 的 attach-detach-release 边界必须明确定义

## Carried Backlog

- `full-rewrite-telegram-parity` `8.1-8.6`
- `full-rewrite-telegram-parity` `9.1-9.7`
- `full-rewrite-telegram-parity` `10.1-10.5`
- `full-rewrite-telegram-parity` `11.1-11.5`
- `full-rewrite-telegram-parity` `12.1-12.6`
- `full-rewrite-telegram-parity` `19.1-19.14`
