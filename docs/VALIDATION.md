# 完整学习版验收记录

本轮验证时间：2026-11-01。验证对象仅为 `shangting-apartment_complete`，不是原教学目录。

## 已执行并通过

| 验证 | 结果与实际范围 |
|---|---|
| `bash scripts/package.sh` | 前端 TypeScript 检查和 Vite 构建、Maven verify、可执行 JAR 打包成功 |
| 后端测试 | 59 项，0 失败、0 错误、0 跳过；包含单元、MVC、H2 SQL/并发和时区映射回归 |
| `npm audit` | 本次安装的前端生产和开发依赖均报告 0 已知漏洞 |
| `node scripts/smoke-test.mjs` | 真实 HTTP：身份/归属隔离、预约幂等/容量/取消/审核、收藏、签约与账单、支付幂等、通知、CSV、安全图片校验、维护任务 |
| `node scripts/concurrency-test.mjs` | 真实 MySQL：容量2的6次并发预约恰好2次成功；重复取消只释放一次；取消/审核仅一次成功；重叠租约仅一份成功；并发激活只有一张首期账单；重复支付两次均返回PAID；已出租房间拒绝预约 |
| 模拟浏览器回归 | 页面渲染、手机布局、同一首次并发写入共享一次CSRF初始化、预约失败重试沿用幂等Key |
| 真实浏览器回归 | 直接访问8082的打包JAR；从新增房源和图片上传开始，完成注册/收藏/预约/审核/CSV/租约激活/演示支付/通知/管理员停用/租约终止 |
| 时间一致性 | Asia/Shanghai浏览器所选10:00，在租客和管理员预约列表均为10:00 |
| 打包运行 | JAR提供前端JS/CSS、图片和API，健康检查UP；不依赖Vite开发服务 |
| 原目录完整性 | 对工作开始前记录的103个原项目文件逐一做SHA-256校验，全部一致 |

真实测试使用独立的 MySQL 数据库 `shangting_apartment_complete`、Redis端口6381和后端端口8082。真实HTTP脚本仅允许本机8082，浏览器脚本仅允许本机5174/8082。测试创建带标记的合成记录，成功结束时会下架自己的测试房源；预约、审核、租约和账单历史保留，以验证关联约束与审计。

## 页面截图

- [桌面租客首页](../frontend/docs/screenshots/public-desktop.png)
- [手机租客首页](../frontend/docs/screenshots/public-mobile.png)
- [管理员工作台](../frontend/docs/screenshots/admin-desktop.png)
- [手机预约中心](../frontend/docs/screenshots/personal-mobile.png)
- [手机管理员页面](../frontend/docs/screenshots/admin-mobile.png)

## 验证的实际边界

这些结果证明本轮环境和所覆盖流程能够正常运行，不代表生产容量、所有商业租赁规则或所有故障情况已经验证。H2测试与真实MySQL验证分别记录，不互相替代。支付为演示状态变化，不连接真实支付渠道；没有短信、电子签章或实际扣款。

复跑真实测试会消耗本机登录/注册/预约的限流额度；遇到429时按限流窗口等待后再运行。请勿为了测试关闭限流或清空整个Redis。
