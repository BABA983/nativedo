# Phase 5 Topics Scene

## Scope

- `TopicsFragment.kt`
- topic list adapter / cells binding
- refresh / pagination / category badge / offline banner

## Prerequisites

- `Phase 3` rendering substrate complete
- `Phase 4` runtime / topics data / cache contract complete

## Exit Gates

- Topics scene 只在自己的 batch 里推进，不再和其他 scene 混写
- 使用既有 `RecyclerListView` / cells / theme / session substrate，不在页面内反向定义基础设施
- smoke 覆盖最新列表、分页、下拉刷新、离线回退

## Carried Backlog

- `full-rewrite-telegram-parity` `23.1-23.9`
