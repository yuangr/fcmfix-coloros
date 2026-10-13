# 一加 Ace 6 夜间深睡网络适配

此说明针对 `13.0-rc6-plq110.5` 的新增分支。它基于用户手机的一加 Ace 6（PLQ110）、`PLQ110_16.0.10.500(CN01)`、Android 16/API 36；上游一加 15 的分析保留为历史参考，不能替代本机核对。

## 已核对的调用链

`com.oplus.deepsleep.ControllerCenter.isUseNetworkDisableWhiteList()` 根据机型配置、云控值及地震/查找手机开关选择模式。本机实际夜间记录为 false。随后 `disableNetWork(boolean)` 调用 `setMobileDataEnable(false, String)` / `setWifiEnable(false, String)`：旧分支直接关闭移动数据或 Wi-Fi，GMS 白名单不参与这条路径。

本机同时存在原生 UID 白名单分支：`determineNetWorkWhiteUidList(): List<String>` → `networkDisableWhiteListInner(List,int,int,boolean): int` → `android.nwpower.OAppNetControlManager.networkDisableWhiteList(List,int): int` → `com.android.server.nwpower.OAppNetControlService`。`enable=0` 启动限制，`enable=1` 撤销限制，原生成功码为 0。服务校验调用身份、功能与云控配置，再调用 netd/kernel；非零返回时原厂可能回退到直接关闭网络。

通过只读实机查询确认 `oplus.software.radio.nwpower_network_disable_white_list` 已开启，`dumpsys oappnetcontrol feature` 报告 `NetDisableWhiteListFeatureEnable`。VPN 查询返回当前的应用 VPN，与始终开启 VPN 设置一致。这些查询没有启动网络限制，不能证明底层规则执行成功。

本地提取文件的 SHA-256（仓库不包含固件、反编译源码或私人设备日志）：

| 文件 | SHA-256 |
| --- | --- |
| `/system_ext/app/Battery/Battery.apk` | `752597dd6a739ddd8bbfd9991a31465790e9b846004a96458223948d5ee32ebc` |
| `/system/framework/oplus-services.jar` | `c08e1ce53afcc17857471ea03f6dd6bf08e80943dd616caa34eb12e179fb89cf` |
| `/system/framework/oplus-framework.jar` | `3b47a02bec547b3218f8a9ff94e11bb443e985b9b980314a9e0bedd82df8af78` |

## 实现范围

- 仅准确固件和 API 36 安装新的路由 Hook，方法签名和功能检查失败会报告失配。第一次修改选择器前还需读取 GMS/VPN UID 并确认原生服务对象存在。
- 反优化六个相关入口，包括电池的 WorkHandler，避免短选择器已内联而绕过 Hook；反优化本身不计作夜间保护动作。
- 原生结果观察 Hook 先安装，路由 Hook 最后安装。首次成功选择后保持该模式到电池进程退出，恢复阶段不依赖 VPN 是否正在重连。
- 电池白名单和系统服务入口都保留 OEM 项（包括 `uid:pid`），追加 GMS、当前应用 VPN 与已配置始终开启 VPN。仅接受主用户应用 UID，复制名单，不修改共享列表。
- 不扩展整夜名单到所有推送应用。可信 FCM 到达后的短时解冻和联网窗口继续使用原有实现。
- VPN 读取失败会记录异常并保留既有 GMS 增补；原生接口失败记录异常，原厂回退保留。没有修改用户睡眠开关、SIM 设置、路由、SELinux、内核参数或常驻唤醒锁，也没有新增轮询、挂载及后台线程。

## 实机验收仍需完成

已有 `.4` 的锁屏及自然 Doze 推送测试不等于此新分支通过。安装 `.5` 并重启后，应在两个作用域确认版本与新 Hook，检查真实深睡记录的选择模式、名单与原生返回码，同时核对系统网络连接和 FCM 实际接收。

测试需覆盖：睡眠优化保持开启；夜间移动数据及 Wi-Fi；GMS/VPN 连接；无推送的普通应用仍受限；可信推送到来后的短时访问及重新限制；亮屏完整恢复；用户手动关闭移动数据/飞行模式不被强行打开；VPN 重连/切换；异常回退与资源状态。最终需整夜观察消息时间与耗电，不能用强制 Doze、单次接口调用或编译成功代替。

边界：`mIsNetAbnormalActive` 仍能走原厂直接断网；原生服务失败也可能回退断网。原生限制启用后再次提交名单可能不刷新，夜间更换 VPN/重装导致 UID 改变未承诺支持。VPN 被 OEM 冻结，以及非白名单客户端经 VPN 是否仍被正确限制，要在实际规则下验证。未知 OTA 需重新提取核对，不自动放开。
