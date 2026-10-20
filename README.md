# KeepAccounts · 通知记账

一个**只给自己用**的 Android 记账应用：读取微信 / 支付宝的通知，自动记账。
数据全部存在本机，`AndroidManifest.xml` 里不声明 `INTERNET` 权限 —— 不是"承诺不上传"，是进程在网络层面就没有外传能力。

---

## 当前进度

分 8 步推进，每步都能独立编译运行。

| 步骤 | 内容 | 状态 |
|---|---|---|
| 1 | 工程骨架 + 加密数据层 + 5 Tab 导航 | ✅ |
| 2 | 账户与资产（净资产、信用卡、转账、余额校准） | ✅ |
| 3 | 账单与分类（记账、列表、编辑删除、搜索） | ✅ |
| 4 | 回收站 + 设置 + 备份恢复 | ✅ |
| 5 | 通知采集（白名单、原始事件） | ⬜ |
| 6 | 解析引擎 + 内置规则 + 待确认箱 | ⬜ |
| 7 | 自定义规则向导 + 记账浮悬提示 | ⬜ |
| 8 | 统计、预算、微信 / 支付宝账单导入 | ⬜ |

---

## 技术选型

| 项 | 选择 | 说明 |
|---|---|---|
| 语言 / UI | Kotlin 2.0 + Jetpack Compose (Material 3) | |
| 数据库 | Room + **SQLCipher** | 数据库文件加密，手机被翻看时账本不裸奔；密钥由 Keystore 包裹 |
| 依赖注入 | 手写 `AppContainer` | 依赖只有 DB → Repository → UI 一层，Hilt 会引入两个插件 + 注解处理器，是构建链上风险最高的部分 |
| 构建 | AGP 8.5.2 / Gradle 8.7 / JDK 17 | |
| 后台任务 | 无（启动时补做） | 不用 WorkManager：缓存里没有该依赖且拉不到 Google Maven。回收站清理与自动备份改为「打开 App 时检查」，代价是长期不打开就不会跑 |
| SDK | minSdk 29、targetSdk 34 | minSdk 29 是 `MediaStore.Downloads` 免权限写公共目录的前提 |
| 图标 | 手绘 Canvas + material-icons-core | 导航图标自绘；只依赖 core 里的 Add / Search / Close / ArrowBack。**不引 material-icons-extended**：该 artifact 没有发布 1.7.5（只有 1.6.8 / 1.7.0 / 1.7.2 / 1.7.8），与 BOM 2024.10.01 的 1.7.5 对不上会直接解析失败 |

---

## 构建

```bash
# Android Studio 打开根目录，Sync，Run
# 或命令行
./gradlew assembleDebug
```

环境：JDK 17、Android SDK 34。

---

## 几条贯穿全项目的硬约定

写新代码前先看这几条，它们是刻意的设计选择，不是疏忽。

**1. 不声明 `INTERNET` 权限。**
Manifest 里那行保持注释状态。副作用是备份只能走 SAF 写到本机目录，无法直接传网盘。

**2. 金额一律存「分」的 `Long`，恒为正。**
方向由 `type` 表达，不用负数表示支出 —— 否则每次聚合都要判符号。只在输入和渲染时转换，见 `util/Money.kt`。

**3. 写流水和改余额必须在同一事务里。**
`accounts.current_balance` 是冗余字段，跟流水表有一致性契约。所有增删改走 `db.withTransaction` + `accountDao.applyBalanceDelta()`，禁止单独更新余额。

**4. 所有业务查询都要带 `deleted_at IS NULL`。**
软删除的隐形契约。列表、统计、余额、搜索、导出，漏一个回收站里的记录就会混进统计。

**5. 枚举用小写字符串常量，不用 Kotlin enum。**
Room 会把 enum 存成大写，跟导出 CSV 里期望的小写对不上。见 `data/local/db/Enums.kt`。

**6. 数据库密钥 ≠ 备份口令，两套不能混用。**
本机 DB 用 Keystore 包裹的随机密钥；备份文件必须用「用户口令 + 派生」加密。
原因：Keystore 密钥绑定设备，换机后就不存在了，用它加密的备份等于造出一个永远打不开的文件。

---

## 目录结构

```
app/src/main/java/com/keepaccounts/
├── core/
│   ├── AppContainer.kt        手写依赖容器
│   └── Palette.kt             全项目唯一色值来源
├── data/
│   ├── local/db/
│   │   ├── Enums.kt           小写字符串常量
│   │   ├── Entities.kt        9 张表
│   │   ├── *Dao.kt            Account / Category / Transaction / Rule / Misc / Settings
│   │   ├── AppDatabase.kt
│   │   ├── DatabaseProvider.kt   Room + SQLCipher + Keystore
│   │   └── SeedData.kt        26 个内置分类 + 默认账户
│   └── repo/                  Account / Category / Transaction / Settings
├── ui/
│   ├── AppRoot.kt             5 Tab 导航
│   ├── component/Common.kt    KaCard / ScreenHeader / SectionLabel
│   ├── screen/                Ledger / Assets / Pending / Stats / Settings
│   └── theme/                 Color / Type / Theme（浅色 + 深色）
└── util/                      Money / Time
```

---

## 设计文档

| 文档 | 内容 |
|---|---|
| [`docs/通知记账App_开发提示词.md`](docs/通知记账App_开发提示词.md) | 完整功能规格：解析规则模型、噪音过滤 7 层、回收站、备份、验收用例 |
| [`docs/ui-preview.html`](docs/ui-preview.html) | 14 屏界面设计稿（浅色） |
| [`docs/ui-preview-dark.html`](docs/ui-preview-dark.html) | 14 屏界面设计稿（深色） |

---

## 已知限制

1. **Room 生成的建表不带 CHECK 约束**，枚举合法性由 Repository 层保证。
2. **launcher 图标是占位 XML**，发布前需要替换。
3. **国产 ROM 会杀通知监听服务**（MIUI / OriginOS / ColorOS），保活方案在第 5 步。
4. 每步只保证「能编译 + 逻辑自洽」，真机行为需要实机验证。

---

## License

[Apache License 2.0](LICENSE)
