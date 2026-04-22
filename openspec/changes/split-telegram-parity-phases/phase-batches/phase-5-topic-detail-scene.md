# Phase 5 Topic Detail Scene

## Scope

- `TopicDetailFragment.kt`
- post list binding
- stream pagination
- reply-to scroll / browser entry

## Prerequisites

- `Phase 3` `CookedHtmlRenderer` / cells / image substrate complete
- `Phase 4` `TopicStreamPager` / topic contracts complete

## Exit Gates

- 页面只消费已有 renderer / pager / theme substrate，不在 scene 内重造 HTML 或 pagination helper
- smoke 覆盖帖子流分页、引用跳转、Open in Browser

## Carried Backlog

- `full-rewrite-telegram-parity` `24.1-24.7`
