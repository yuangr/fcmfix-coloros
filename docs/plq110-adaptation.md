# PLQ110 / ColorOS 16 适配

基线：Artifical0/fcmfix-coloros v13.0-rc6，dee734faccdc6d479e1668b8351319106ec583f5。
验证固件：PLQ110_16.0.10.500(CN01)，Android 16/API 36，Vector 2.2/API 101。

本文下方为初版适配与既有测试记录。当前 `.5` 新增的一加 Ace 6 夜间路由修复及未完成的验收见[夜间网络适配](ace6-night-network-routing.md)；初版测试不能作为新分支已通过的证据。

## 改动

- 电池深睡联网白名单：旧接口不存在时，适配 determineNetWorkWhiteUidList() 返回值，保留原名单并追加缺少的 GMS UID，不修改断网目标列表。
- Osense：保留同步接口，增加 void requestSceneAction(Bundle)。异步入口仅在 GMS 包名、DeepSleepLogicDisNetRestore 原因、SCENE_COMMON_REQUEST 动作、CommonExternalClean 子动作和 strategy=2 全匹配时跳过。
- 弱信号旧接口：所列准确固件中未找到原接口，明确标为“不适用（未安装）”，不计入生效数；未知固件仍报告失配。
- 自查区分 active、failed、skipped，独立包名保留官方版供回退。

## 验证

- 18 个测试类、74 项单元测试通过；Debug 构建及签名验证通过。
- 重启：系统框架31项生效、1项不适用，电池4项生效，无失配。
- 微信、Telegram 未运行时，锁屏下由可信 FCM 拉起并产生声音、震动和锁屏通知，用户反馈不到20秒。
- 自然深度 Doze 下，两款应用均收到提醒，用户估计约5秒；该轮主进程已经运行。

## 边界

整夜稳定性及深度 Doze 冷启动尚未验证。未观察到实际触发 DeepSleepLogicDisNetRestore 清理，Hook 安装不等于防护分支已执行。原弱信号接口缺失不代表已拦截所有其他弱信号限制。体验反馈不是严格时钟测量。

手机固件仅在本地只读提取分析，没有包含在公开仓库。其他固件需重新核对方法及调用逻辑。
