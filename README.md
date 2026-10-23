# 通知记账（NotiLedger）

一个纯本地、离线运行的通知自动记账 Android 应用。读取白名单应用的通知，解析金额、商户与收支方向，自动生成账单或进入待确认箱。

## 技术栈

- Kotlin 2.0.21，协程 + Flow
- Jetpack Compose + Material 3，单 Activity + Navigation Compose
- Room（8 张表，version 1）
- DataStore Preferences
- WorkManager（自动备份）
- Hilt + KSP
- minSdk 26 / targetSdk 35，包名 `com.local.notebook`

## 构建与运行

环境要求：JDK 17、Android SDK Platform 35。

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

Debug APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。release 构建默认开启 R8 混淆与资源压缩。

Gradle 已配置阿里云与腾讯云镜像；`gradle.properties` 中的代理绕过与 `in-process` 编译设置仅用于本机受限环境，可按需删除。

## 权限与国产 ROM

1. **通知使用权**：首次启动引导页会打开系统「通知使用权」列表，找到「通知记账」并开启。未开启时首页顶部常驻红色提示条。
2. **发送通知**：Android 13+ 需要 `POST_NOTIFICATIONS`。设置页会显示系统通知权限状态，并提供跳转入口。
3. **国产 ROM 保活**：小米 / 华为 / 荣耀 / OPPO / vivo 建议在系统设置中开启「自启动」「后台运行白名单」「电池优化豁免」，否则通知监听服务可能被后台限制。
4. 部分系统在锁屏或通知栏隐藏通知内容，需允许显示通知内容，否则无法解析。

## 解析引擎

处理流水线：

1. 白名单校验（不在白名单的通知不落库）
2. L0 通知类型过滤（promo / recommendation / social / system）
3. L1 全局噪音词（出现「支付成功」「到账」等明确语义时豁免）
4. 内置/自定义规则匹配（有序片段模板）
5. L2 规则排除词
6. L3 金额、长度、上限校验
7. L4 方向判定（收入优先，退款视为收入）
8. 特殊场景（退款按收入、转账未识别时进入待确认）
9. 60 秒去重
10. 写库、余额重算、规则统计与反馈提示

规则模板编码（用户不可见）：

```
literal:微信支付,支付成功|slot:amount|fuzzy:true|direction:EXPENSE
```

- `literal`：任意一个文案命中即可；多个 `literal` 片段需按顺序出现，片段之间允许无关内容。
- `fuzzy`：忽略空白、标点、全半角与大小写。
- `slot`：金额/商户槽位由抽取器全局识别。
- `direction`：可选，缺省按方向词自动判断。

内置规则位于 `app/src/main/assets/default_rules.json`（可读副本），运行时由仓库层以同一模板写入数据库，并按 `rule_seed_version` 做版本同步。

新规则保存后默认 `OBSERVING`：命中只进入待确认，确认 3 条且无噪音后可启用自动记账。有专属规则的应用，未命中的通知不会再走通用兜底，避免聊天消息涌入待确认。

## 备份格式

手动备份生成 zip：

```
manifest.json                 # schemaVersion、appVersion、createdAt、各表行数、每文件 SHA-256
data/accounts.csv
data/categories.csv
data/transactions.csv
data/budgets.csv
data/notification_raw.csv
data/rules.json
data/settings.json            # 白名单、兜底开关、回收站保留期、主题、提示模式、静音时段
```

恢复当前为「覆盖全部」模式，在数据库事务中完成；schemaVersion 不匹配时不会静默清空数据。

自动备份：默认关闭，可选 1 / 3 / 7 / 30 天周期，文件保存在应用私有目录 `files/auto_backup/`，保留最近 5 份，约束为电量不低。

## CSV 导入与导出

- 导入支持微信 / 支付宝 CSV，自动识别 UTF-8 / GBK。
- 自动跳过支付宝前导说明行与 `----` 分隔线，扫描包含「交易时间」「金额」的表头。
- 金额清洗支持 `¥ ￥ ,`、负号与括号负数；时间支持 `yyyy-MM-dd HH:mm:ss`、`yyyy/M/d HH:mm`、中文日期等格式。
- 交易关闭 / 已退款 / 等待付款等状态默认跳过。
- 去重优先使用交易单号，否则使用「时间分钟 + 金额 + 交易对方」，重复导入同一文件不会新增账单。
- `收/支` 为 `/` 的流水导入为不计收支的转账记录，不影响余额与收支统计。
- 导出为 UTF-8 BOM CSV，Excel 可直接打开。

## 代码结构

```
app/src/main/java/com/local/notebook/
├── data/
│   ├── local/          Room 实体、DAO、数据库
│   ├── repository/     仓库、设置
│   ├── parser/         （domain/parser）纯 Kotlin 解析引擎
│   ├── backup/         zip 备份、恢复、自动备份 Worker
│   └── importer/       CSV 导入导出
├── domain/             纯 Kotlin 解析与金额算式
├── service/            通知监听、处理器、反馈通知与动作
└── ui/                 主题、组件、页面、导航
```

## 单元测试

```bash
./gradlew testDebugUnitTest
```

已覆盖：金额抽取与营销降权、方向词优先、L0/L1/L3 拦截、规则匹配、通用兜底、退款与红包、商用文案豁免、算式键盘、CSV 编解码。

## 已知限制

- 退款目前按「退款」收入记账，尚未自动冲销 60 天内的原支出。
- 转账通知若无法识别双账户，会进入待确认，需人工确认。
- 恢复仅提供覆盖模式，未实现合并追加与密码加密（加密为规格中的可选项）。
- 静音时段固定 23:00–07:00，暂未提供时间自定义。
- 自动备份由 WorkManager 调度，系统省电策略可能延后执行。
- 小米等 ROM 可能限制后台广播与进程，调试注入通知在后台长时间后可能收不到；真实通知监听服务在开启自启动后正常工作。
- 通用规则命中一律进入待确认，不直接入账。
- 通知内容被系统隐藏时无法解析。

## 隐私

- `AndroidManifest.xml` 未声明 `INTERNET` 权限。
- 无网络库、埋点、崩溃上报或广告 SDK。
- 所有通知原文、账单与设置只保存在本机 Room / DataStore。
- 备份由用户主动选择保存位置。

版本：1.0
