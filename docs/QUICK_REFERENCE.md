# 🚀 Telegram 导航 - 快速参考

## ✅ 已完成集成

```kotlin
// ✅ Topic 页面 - 已添加手势返回
composable("topic/{topicId}") { ... }

// ✅ User 页面 - 已添加手势返回  
composable("user/{username}") { ... }

// ✅ Login 页面 - 已添加手势返回
composable("login") { ... }

// ❌ Browser 页面 - 不需要（有自己的处理）
composable(APP_BROWSER_ROUTE) { ... }

// ❌ 根容器 - 不需要（不能返回）
composable(RootDestination.rootContainerRoute) { ... }
```

## 🎯 核心动画参数

| 参数 | 值 | 说明 |
|------|-----|------|
| 位移距离 | 48dp | 页面滑入/滑出距离 |
| 动画时长 | 150ms | 标准转场时长 |
| 返回最大位移 | 56dp | 手势返回最大拖动距离 |
| 返回取消时长 | 320ms | 手势取消回弹时长 |
| 返回完成时长 | 380ms | 手势完成滑出时长 |
| 完成阈值 | 30% | 超过此进度完成返回 |

## 🧪 快速测试

### 基础测试（1 分钟）
1. 打开任意话题 → 观察从右侧滑入
2. 点击返回 → 观察向右滑出
3. ✅ 动画流畅，约 150ms

### 手势测试（Android 13+）
1. 从左边缘向右滑动
2. 滑动 < 30% 松手 → 回弹
3. 滑动 > 30% 松手 → 完成返回
4. ✅ 完全跟手，无延迟

## 📁 文件位置

```
apps/android/app/src/main/java/io/github/baba983/nativedo/
├── core/navigation/
│   ├── TelegramStyleNavigation.kt      # 核心组件
│   ├── TelegramStyleScaffold.kt        # 页面包装器
│   └── TelegramNavExamples.kt          # 使用示例
├── ui/theme/
│   └── TelegramAnimationSpec.kt        # 动画参数
├── navigation/
│   └── NativeDoNavHost.kt              # ✅ 已集成
└── debug/
    └── TelegramAnimationDebugger.kt    # 调试工具

docs/
├── TELEGRAM_NAVIGATION.md              # 完整文档
├── QUICK_START_TELEGRAM_NAV.md         # 快速指南
├── TELEGRAM_NAVIGATION_SUMMARY.md      # 实现总结
└── INTEGRATION_TEST_CHECKLIST.md       # 测试清单
```

## 🔧 常用代码片段

### 添加新页面
```kotlin
composable("your-route") {
    TelegramStyleScaffold(
        canNavigateBack = navController.previousBackStackEntry != null,
        onNavigateBack = { navController.popBackStack() }
    ) {
        YourContent()
    }
}
```

### 禁用手势返回
```kotlin
TelegramStyleScaffold(
    canNavigateBack = false,  // 禁用
    onNavigateBack = {}
) { YourContent() }
```

### 自定义返回逻辑
```kotlin
TelegramStyleScaffold(
    canNavigateBack = true,
    onNavigateBack = {
        if (hasUnsavedChanges) {
            showConfirmDialog()
        } else {
            navController.popBackStack()
        }
    }
) { YourContent() }
```

## 🐛 故障排查

| 问题 | 解决方案 |
|------|---------|
| 编译错误 | 检查 import 语句 |
| 预测性返回不工作 | 检查 Android 版本 ≥ 13 |
| 动画卡顿 | 在真机上测试 |
| 手势太灵敏 | 调整阈值（默认 30%） |

## 📊 性能目标

- 帧率：60 FPS (< 16.67ms/帧)
- 掉帧率：< 5%
- 手势延迟：< 16ms

## 🎉 下一步

- [ ] 运行应用测试效果
- [ ] 在 Android 13+ 设备上测试手势返回
- [ ] 查看完整文档了解更多功能
- [ ] （可选）添加触觉反馈
- [ ] （可选）添加状态栏颜色过渡

---

**快速链接**：
- [完整文档](./TELEGRAM_NAVIGATION.md)
- [测试清单](./INTEGRATION_TEST_CHECKLIST.md)
- [Telegram 源码参考](https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/ui/ActionBar/ActionBarLayout.java)
