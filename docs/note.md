确认了：Telegram 的 fragmentView 用 LayoutHelper.createFrame(MATCH_PARENT, MATCH_PARENT) — 没有 topMargin。fragmentView 是全高的，ActionBar 作为**子 View** 覆盖在 fragmentView 里面（setAddToContainer(false)）

不是"兄弟覆盖在上面"，而是 ActionBar 在 fragmentView 内部！这样 PhotoViewer 的 updateParentScale 缩放 fragmentView 时，ActionBar 跟着一起缩放。

验证：ChatActivity.java:3690 `actionBar.setAddToContainer(false)` + ChatActivity.java:7651 `contentView.addView(actionBar)` → ActionBar 在 fragmentView 内部。

https://linux.do/t/topic/2193859
Any 路由清障器
https://linux.do/t/topic/1779614
公益站列表
https://linux.do/t/topic/2140307
冰
https://linux.do/t/topic/2214637
list nested pic
https://linux.do/t/topic/2214964
spoiler
https://linux.do/t/topic/2215753
topic list 首页标题bug换行
https://linux.do/t/topic/2310531
