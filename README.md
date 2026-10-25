# 通知记账（NotiLedger）

一个通知自动记账 Android 应用。读取白名单应用的通知，先用可编辑的交易关键词初筛，再交给 AI 判断是否为真实交易，自动生成账单或进入待确认箱。

## 技术栈

- Kotlin 2.0.21，协程 + Flow
- Jetpack Compose + Material 3，单 Activity + Navigation Compose
- Room（8 张表，version 2）
- DataStore Preferences + Android Keystore（API Key 加密）
- WorkManager（自动备份、AI 解析队列）
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
2. 60 秒内同通知 key 去重，只更新原文
3. 交易关键词初筛（可增删改，未命中不保存原文）
4. 命中后落库，交给 WorkManager 调用 AI
5. AI 返回是否交易、收支方向、金额、商户、分类、账户与置信度
6. 校验金额、置信度、账户、去重后直接入账或进入待确认
7. AI 判定非交易则只记识别日志；AI 失败/未配置则用本地金额提取转待确认
8. 写库、余额重算、横幅提示

设置页可选择：

- **直接入账**（默认）：AI 置信度达标、金额合法、账户匹配时直接记账
- **全部待确认**：AI 确认的交易也先进入待确认箱

退款按收入处理；转账必须识别转出与转入账户，否则进入待确认。内置默认关键词包括支付、付款、消费、扣款、收款、到账、转账、退款、交易、入账、支付成功、退款成功等，不包含「元 / ¥ / 红包 / 优惠」等泛词。

## 备份格式

手动备份生成 zip：

```
manifest.json                 # schemaVersion、appVersion、createdAt、各表行数、每文件 SHA-256
data/accounts.csv
data/categories.csv
data/transactions.csv
data/budgets.csv
data/notification_raw.csv     # 含 AI 返回原文、模型与错误信息
data/keywords.json
data/settings.json            # 白名单、回收站保留期、主题、提示模式、静音时段、AI 配置（不含 API Key）
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
│   ├── ai/             AI 客户端、API Key 加密、AI 解析执行器
│   ├── backup/         zip 备份、恢复、自动备份 Worker
│   └── importer/       CSV 导入导出
├── domain/             纯 Kotlin 解析、关键词匹配与 AI 数据模型
├── service/            通知监听、关键词初筛、AI Worker、反馈通知
└── ui/                 主题、组件、页面、导航
```

## 单元测试

```bash
./gradlew testDebugUnitTest
```

当前保留 CSV 编解码测试。规则引擎相关测试已随本地规则功能一并移除。

## 已知限制

- 退款目前按「退款」收入记账，尚未自动冲销 60 天内的原支出。
- 转账通知若无法识别双账户，会进入待确认，需人工确认。
- 恢复仅提供覆盖模式，未实现合并追加与密码加密（加密为规格中的可选项）。
- 静音时段固定 23:00–07:00，暂未提供时间自定义。
- 自动备份由 WorkManager 调度，系统省电策略可能延后执行。
- 小米等 ROM 可能限制后台广播与进程，调试注入通知在后台长时间后可能收不到；真实通知监听服务在开启自启动后正常工作。
- AI 判定非交易时不会入账，只保留识别日志。
- AI 请求失败或未配置 Key 时，命中关键词的通知会尽量转待确认，不会静默丢弃。
- 通知内容被系统隐藏时无法解析。

## 隐私

- `AndroidManifest.xml` 声明了 `INTERNET` 权限，仅用于调用用户配置的 AI 接口。
- 未开启 AI 解析时不会发起任何网络请求；开启后，命中关键词的通知标题与正文会发送给所配置的服务商。
- API Key 使用 Android Keystore 加密保存在本机，不写入备份。
- 账单、关键词与设置保存在本机 Room / DataStore；备份由用户主动选择保存位置。
- 无埋点、崩溃上报或广告 SDK。

版本：1.0
