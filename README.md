# 尚庭公寓 · 完整学习版

一个可在本机独立运行的租赁平台：**找房 → 收藏 → 看房预约 → 管理员审核 → 签约 → 账单 → 站内通知**。包含租客前台与管理工作台，使用真实后端 API 和 MySQL 数据；无需 Docker。

本目录独立于 `../shangting-apartment`。默认服务端口 **8082**、数据库 **shangting_apartment_complete**、专用 Redis **6381**；不会复用原项目的数据库、Cookie 或 Redis 会话。

## 技术栈

- Java 25+、Spring Boot 4.1、Spring MVC / Security / Session Redis、MyBatis-Plus / JdbcClient。
- MySQL 8.4、Flyway、Redis、HikariCP；事务、行锁、CAS、接口幂等、固定窗口限流。
- React 19、TypeScript、Vite、React Router；Cookie 会话与 CSRF，前后端同源。
- JUnit / Mockito / MockMvc、H2 隔离测试、真实 HTTP 与 MySQL 并发验证、Playwright 浏览器验证。

## 本机启动

准备 JDK 25 或更高版本、MySQL 8.4、Redis、Node.js 24 与 npm。MySQL 使用 Homebrew 时先启动：

```bash
brew services start mysql@8.4
git clone https://github.com/Szx-Yuji/shangting-apartment.git
cd shangting-apartment
bash scripts/setup-local.sh
bash scripts/start.sh
```

`setup-local.sh` 会创建独立数据库和账户，随机生成数据库密码并保存到权限受限的 `.env`，在 6381 启动本目录专用 Redis。脚本默认用本机 MySQL root 连接；如已设密码，请通过环境变量 `MYSQL_ADMIN_PASSWORD` 提供，不要写到源码里。数据库结构由 Flyway 自动迁移。

首次启动会在 `DEMO_ENABLED=true` 时生成合成房源和账号。打开 **http://localhost:8082**。

| 用途 | 用户名 | 学习演示密码 |
|---|---|---|
| 管理员工作台 | `admin` | `Admin123!2026` |
| 租客个人中心 | `tenant` | `Tenant123!2026` |

演示账号仅供本地学习。正常运行配置默认关闭演示初始化；本地 setup 生成的 `.env` 显式开启。请勿将此密码用于真实服务。

前端源代码开发可另开终端运行：

```bash
cd frontend
npm ci --ignore-scripts
npm run dev
```

访问 http://localhost:5174，Vite 会代理 API 到 8082。生产构建使用相同后端端口提供前端静态资源，不依赖开发服务器。

## 已实现功能

### 租客端

- 按城市、关键字、租金筛选公开房源，查看详情、收藏、选择预约时段。
- 注册登录、个人预约列表、取消预约和名额释放；重复提交携带同一请求标识。
- 仅本人可见的租约、账单、通知；站内通知已读状态。
- 演示支付：数据库状态流转与幂等验证，不连接真实支付渠道、不扣款。

### 管理员工作台

- 运营概览、公寓和房间管理、房源上下架、PNG/JPEG 图片上传。
- 创建预约时段、按房间/状态/时间筛选预约、确认或拒绝、查询审核记录。
- 异步导出 UTF-8 CSV，最多 5000 行，包含公式注入防护。
- 租约创建、重叠租期检查、生效/终止；账单生成、人工收款登记。
- 用户启停、管理员创建和启停；禁止禁用自己及最后一个管理员。
- 有界定时维护：租约到期、账单逾期、停机期间历史补账。

### 后端基础能力

- 角色与对象归属授权；会话更换防固定会话攻击；CSRF、HttpOnly、SameSite。
- Redis 共享 Session、缓存、Lua 限流；登录与预约独立计数。
- 数据库唯一约束幂等、时段容量条件更新、取消/审核并发控制、审核留痕。
- ProblemDetail 错误格式、请求编号、MDC、AOP 耗时、健康检查与连接池。
- 数据库迁移、独立环境、自动化测试、前端打包进可执行 JAR。

## 验证与打包

```bash
./mvnw test
cd frontend && npm run build && npm audit && cd ..
# 以下会在独立完整库中创建明确标记的合成测试数据；需要先启动本应用。
node scripts/smoke-test.mjs
node scripts/concurrency-test.mjs
bash scripts/package.sh
```

打包后的 `target/shangting-apartment-0.0.1-SNAPSHOT.jar` 包含前端。加载 `.env` 后可用 `java -jar` 启动。测试不连接原项目的数据库；单元/Controller 测试使用替身与 H2，真实 HTTP 验证只允许本机端口。

```bash
set -a
source .env
set +a
java -jar target/shangting-apartment-0.0.1-SNAPSHOT.jar
```

后端在终端运行时按 `Ctrl+C` 停止。再次运行 `bash scripts/start.sh` 会重建前端，并在需要时启动本项目的专用 Redis。需要停止专用 Redis 时执行 `redis-cli -h 127.0.0.1 -p 6381 shutdown`，不要停止其他项目的 Redis。

健康检查：`GET /actuator/health`。`UP` 表示本次健康指标正常，不代替业务测试。错误响应中的 `requestId` 与服务端日志相同。

## 目录与学习入口

```text
frontend/                       React 页面、API/CSRF 客户端、浏览器验证
src/main/java/com/yuji/apartment/
  controller/                   HTTP 接口与参数绑定
  service/                      身份、预约、审核、事务与个人业务
  catalog/                      丰富房源、上传、CSV 导出
  leasing/                      租约、账单、通知、统计和维护
  mapper/ entity/ dto/          MyBatis 映射、数据库对象与接口数据
  security/ filter/ ratelimit/  权限、请求跟踪、故障处理与限流
  config/ cache/ monitor/      配置绑定、缓存、AOP
src/main/resources/db/migration/ 唯一正式建表入口
src/test/                       单元、MVC、SQL 与并发边界测试
scripts/                        安装、启动、打包、真实 HTTP 验证
docs/                           架构、API、学习路线、租赁业务规则
```

建议从 [学习路线](docs/LEARNING_GUIDE.md)、[架构说明](docs/ARCHITECTURE.md) 和 [租赁规则](docs/LEASING.md) 开始。本轮实际执行的检查和页面截图见 [验收记录](docs/VALIDATION.md)。

## 学习版的边界

- 演示支付是状态机练习，不是第三方支付或资金系统。终止租约不会自动退款，已付记录保留。
- 图片保存在本机目录；多机器部署需共享文件系统或对象存储，Redis 共享 Session 不会共享磁盘文件。
- 未接入短信、电子签章或外部消息服务。站内通知已持久化，维护任务可重复执行。
- 上线时应关闭 `DEMO_ENABLED`、关闭演示支付，配置 HTTPS 与 Secure Cookie、持久化和备份策略、真实凭据及容量参数。
- 本目录为完整学习项目，不宣称覆盖所有商业租赁或财务合规场景。
