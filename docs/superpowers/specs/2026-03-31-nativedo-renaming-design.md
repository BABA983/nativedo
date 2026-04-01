# NativeDo 命名与标识改造设计

**日期：** 2026-03-31  
**状态：** 已确认方案，待后续实施  
**目标：** 将当前以 `LinuxDo` / `linuxdo-native` 为主的产品命名，收敛为以 `NativeDo` 为主的用户可见品牌，同时统一 Android / iOS 的应用标识与对外文案。

---

## 背景

当前仓库已经形成了一套可运行的 Android 工程骨架，但产品名、技术标识和仓库命名还处在早期占位状态：

- 用户可见名称仍是 `LinuxDo`
- Android `namespace` / `applicationId` 仍是 `io.github.baba.linuxdo`
- README 和设计文档也主要使用 `LinuxDo` / `Linux.do client` 作为产品称呼

现在产品主名已经明确为 `NativeDo`。在尚未发布到应用商店之前，统一产品名和平台标识的成本最低，应在这一阶段完成一次收敛，避免后续出现“桌面显示名、包名、文档名、对外描述”长期不一致的问题。

---

## 目标

- 用户可见主名统一为 `NativeDo`
- 对外描述保留 `Linux.do` 识别，避免用户看不出这是对应社区客户端
- Android 技术标识改为 `io.github.baba983.nativedo`
- iOS 未来的 `Bundle Identifier` 预留为 `io.github.baba983.nativedo`
- README、仓库介绍文案、设计文档中的产品称呼与新命名保持一致

## 非目标

- 本次不要求同步修改 GitHub 仓库 slug 或 remote 地址
- 本次不涉及应用图标、视觉品牌、商店截图等设计资产
- 本次不引入功能改动、架构重构或无关代码清理
- 本次不要求为 iOS 搭建实际 Xcode 工程，只定义后续应使用的标识

---

## 方案比较

### 方案一：只改显示名，不改技术标识

将 App 名和 README 改成 `NativeDo`，但继续保留 `io.github.baba.linuxdo`。

优点：

- 改动最少
- 风险最低

缺点：

- 产品名与包标识长期不一致
- 越往后改，迁移成本越高

### 方案二：产品名与平台标识一起改，仓库 slug 暂不迁移

将用户可见名称、文档称呼、Android 标识和未来 iOS 标识统一改为 `NativeDo` / `io.github.baba983.nativedo`，GitHub 仓库仍保留 `linuxdo-native`。

优点：

- 品牌名与安装包标识一致
- 在未上架阶段完成改名，成本可控
- 不引入 GitHub 仓库迁移和 remote 变更成本

缺点：

- 仓库名与产品名短期内不完全一致

### 方案三：连仓库 slug 一起全量迁移

优点：

- 一致性最强

缺点：

- 需要同步处理 remote、文档链接、外部引用
- 当前收益明显低于额外成本

### 结论

采用**方案二**。

这是当前最合适的平衡点：先把用户会感知到的产品命名和平台标识收敛完成，保留仓库 slug 作为一个可独立安排的后续动作。

---

## 最终命名决策

### 用户可见命名

- App 主名：`NativeDo`
- 对外副标题：`NativeDo for Linux.do`
- README 主标题：`NativeDo`
- README 简介：原生 `Linux.do` 客户端 monorepo，当前 Android 为主，未来预留 iOS

### 平台标识

- Android `namespace`：`io.github.baba983.nativedo`
- Android `applicationId`：`io.github.baba983.nativedo`
- iOS `Bundle Identifier`：`io.github.baba983.nativedo`

### 仓库相关

- GitHub 仓库 slug：暂时保持 `linuxdo-native`
- GitHub 仓库 Description：改为 `NativeDo for Linux.do`

---

## 影响范围

### 1. Android 工程

需要统一以下内容：

- `apps/android/app/build.gradle.kts`
  - `namespace`
  - `defaultConfig.applicationId`
- `apps/android/app/src/main/res/values/strings.xml`
  - `app_name`
- `apps/android/app/src/main/java/io/github/baba/linuxdo/**`
  - 目录路径迁移到 `io/github/baba983/nativedo/**`
  - 所有 `package` 声明改为 `io.github.baba983.nativedo...`
  - 所有相关 `import` 同步更新
- `apps/android/app/src/test/java/io/github/baba/linuxdo/**`
  - 测试代码包路径与 `package` 声明同步迁移

若代码里存在硬编码的旧包名前缀，也需要一并替换。

### 2. Android Manifest 与相对类名

当前 manifest 使用了相对类名：

- `.LinuxDoApplication`
- `.MainActivity`

在包迁移后有两种可接受做法：

- 继续保留相对类名，并确保它们位于新的包名下
- 改成完整限定名，显式写出新包路径

为了保持工程简洁，优先继续使用相对类名。

### 3. 文档与 README

需要更新以下内容：

- `README.md`
  - 标题从 `linuxdo-native` 调整为 `NativeDo`
  - 简介改为 `NativeDo for Linux.do`
  - 若示例命令、测试名、包路径中引用旧标识，按新标识更新
- `docs/superpowers/specs/2026-03-31-linuxdo-native-client-design.md`
  - 将作为产品名的 `LinuxDo` 调整为 `NativeDo`
  - 保留 `linux.do` 作为目标社区站点名称
- `docs/superpowers/plans/2026-03-31-linuxdo-android-v1.md`
  - 将作为产品名的 `LinuxDo` 调整为 `NativeDo`
  - 文件路径、类名、包名示例与新标识保持一致

原则是区分两类语义：

- 产品名：用 `NativeDo`
- 目标社区名：继续用 `linux.do` 或 `Linux.do`

### 4. iOS 预留信息

当前仓库尚未存在实际 iOS 工程，因此本次只需要：

- 在 iOS 相关说明文档中明确未来使用 `io.github.baba983.nativedo`
- 不强行创建新的 iOS 工程文件

---

## 命名替换规则

为了避免替换过度，本次应遵循以下规则：

### 应替换

- 用户可见应用名 `LinuxDo` -> `NativeDo`
- 产品语义下的 `LinuxDoApp`、`LinuxDoTheme`、`LinuxDoApplication`、`LinuxDoNavHost` 等命名
- 文档中“这是这个产品的名字”的 `LinuxDo`
- 包名与路径 `io.github.baba.linuxdo` -> `io.github.baba983.nativedo`

### 不应替换

- `linux.do` 站点本身的名称
- 与 Discourse / Linux.do 社区接口相关的协议说明
- 仓库 slug `linuxdo-native`，除非后续单独发起仓库迁移

---

## 实施顺序建议

1. 先改 Android `namespace` / `applicationId`
2. 再迁移源码与测试目录路径、`package` 声明、相关类名
3. 更新 `strings.xml` 中的 `app_name`
4. 更新 README 与设计 / 计划文档中的产品称呼
5. 最后检查是否还存在旧包名前缀或旧产品名残留

这个顺序的目的是先锁定平台标识，再处理源码和文档，避免来回修改。

---

## 验证方式

完成改名后，至少应执行以下验证：

### Android

- `./apps/android/gradlew -p apps/android :app:compileDebugKotlin`
- `./apps/android/gradlew -p apps/android :app:testDebugUnitTest`
- `./apps/android/gradlew -p apps/android :app:assembleDebug`

### 人工检查

- 启动后桌面名称显示为 `NativeDo`
- 构建产生的新安装包标识为 `io.github.baba983.nativedo`
- README 和主要设计文档不再把 `LinuxDo` 当作产品主名
- 仍保留 `Linux.do` 作为社区名称，而不是被误替换成 `NativeDo`

---

## 风险与约束

### 安装包标识变更

一旦 `applicationId` 从 `io.github.baba.linuxdo` 改为 `io.github.baba983.nativedo`，Android 会把它当作一个新应用：

- 旧安装不会被覆盖
- 旧本地数据不会自动继承
- 调试设备上可能会同时存在两个安装记录

在尚未正式发布前，这是可接受的。

### 包路径迁移

源码目录和测试目录的迁移必须一起做，否则容易出现：

- `package` 声明与目录结构不一致
- 导包错误
- IDE 索引异常

### 文档误替换

`LinuxDo` 作为产品名需要替换，但 `linux.do` 作为目标站点名不应被抹掉。文档修订时必须按语义检查，而不是机械全局替换。

---

## 完成标准

满足以下条件即可视为本次改名完成：

- App 显示名已经变为 `NativeDo`
- Android `namespace` 与 `applicationId` 都变为 `io.github.baba983.nativedo`
- 未来 iOS `Bundle Identifier` 已在文档中明确为 `io.github.baba983.nativedo`
- README 和主要设计 / 计划文档已切换到 `NativeDo` 作为产品名
- GitHub 仓库 slug 仍保持 `linuxdo-native`，但仓库说明文案可更新为 `NativeDo for Linux.do`
- Android 编译、测试、打包验证通过

---

## 结论

本次命名改造的核心决策是：

- 产品名定为 `NativeDo`
- 对外描述使用 `NativeDo for Linux.do`
- Android 与未来 iOS 的应用标识统一为 `io.github.baba983.nativedo`
- 本轮只改产品名、平台标识、README 与文档，不迁移 GitHub 仓库 slug

这能在当前阶段以最低的额外成本，完成一次长期有效的命名收敛。
