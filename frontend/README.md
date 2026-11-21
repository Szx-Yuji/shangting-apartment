# 尚庭公寓前端

React 19 + TypeScript + Vite，包含租客端和运营工作台。使用 HashRouter，因此生产构建可以直接放入 Spring Boot 的静态资源目录，不需要额外配置 SPA 路由回退。

## 本地开发

```bash
npm ci --ignore-scripts
npm run dev
```

浏览器打开 `http://127.0.0.1:5174`。Vite 将 `/api`、`/actuator`、`/uploads` 转发到 `http://127.0.0.1:8082`。后端启动与演示数据初始化见项目根目录 README。

```bash
npm run typecheck
npm run build
npm audit --omit=dev
```

构建输出在 `dist/`。安装脚本默认禁用，当前依赖使用 npm 分发的预编译构建工具，不需要打开全部依赖的安装脚本。

## 页面

- `/#/`：城市、关键词、租金筛选和分页找房。
- `/#/rooms/:id`：房间详情、可预约时段、联系人信息与幂等预约。
- `/#/login`、`/#/register`：租客登录、注册。
- `/#/me/appointments`、`favorites`、`leases`、`bills`、`notifications`：个人租住中心。
- `/#/admin/login`：管理员登录。
- `/#/admin`：运营概览。
- `/#/admin/apartments`、`rooms`、`slots`、`appointments`、`leases`、`bills`、`users`、`accounts`：管理员业务页面。

## 数据和安全边界

`src/api.ts` 是唯一通用 JSON 请求入口，Cookie 随请求发送。首次写操作从 `/api/csrf` 取得当前会话的令牌，随后在当前页面的内存中复用；首次并发写操作共享一次获取令牌的请求，避免新会话同时生成多个令牌。不在 localStorage/sessionStorage 保存会话、密码或 CSRF Token。

预约表单按“时段 + 联系人 + 电话”保存一次意图的 Idempotency-Key。同一内容失败后重试沿用原 Key，内容改变后产生新 Key。注册、退出或登录改变 Session 后会清理令牌缓存，下一次写请求重新取得 CSRF Token。收到 401/403 也清理缓存，并向用户显示错误，不擅自重放写操作。

页面上的角色保护用于用户体验；实际权限和数据归属仍由后端检查。服务端返回的错误通过 ProblemDetail 展示，包含请求编号时可据此定位日志。房源图片上传仅接受 JPG/PNG，实际大小、内容和访问路径由后端再次验证。

## 代码入口

| 文件                       | 职责                               |
| -------------------------- | ---------------------------------- |
| `src/App.tsx`              | 路由、前台导航、页面懒加载         |
| `src/api.ts`               | Cookie、CSRF、错误解析与 HTTP 请求 |
| `src/hooks.tsx`            | 服务端会话、查询状态、全局提示     |
| `src/components.tsx`       | 对话框、分页、表格、表单及图片上传 |
| `src/pages/Explore.tsx`    | 房源筛选列表                       |
| `src/pages/RoomDetail.tsx` | 房源详情与预约提交                 |
| `src/pages/Personal.tsx`   | 租客个人业务                       |
| `src/pages/Admin*.tsx`     | 管理员工作台及管理业务             |

## 页面怎样调用后端

列表页面通过项目自定义的 `useResource()` 调用 `api()`；表单和按钮事件调用项目自定义的 `post()`、`put()`、`patch()` 或 `remove()`，最后仍进入 `src/api.ts` 的 `api()`。其中真正发送 HTTP 请求的 `fetch()` 是浏览器提供的函数。React Router 的 `Link` 负责切换页面，页面需要数据时再发送对应请求。

| 页面功能             | 主要接口                                                                        | 返回或变化                              |
| -------------------- | ------------------------------------------------------------------------------- | --------------------------------------- |
| 搜索房源             | `GET /api/public/listings`                                                      | 房源列表与分页信息                      |
| 房源详情、看房时段   | `GET /api/public/listings/{id}`、`GET /api/public/rooms/{id}/appointment-slots` | 房源信息、剩余名额                      |
| 提交预约             | `POST /api/me/appointments`                                                     | 预约编号，附带幂等 Key                  |
| 登录与会话恢复       | `POST /api/auth/login`、`GET /api/session`                                      | 浏览器保存会话 Cookie，页面读取当前角色 |
| 我的预约、取消       | `GET /api/me/appointments`、`POST /api/me/appointments/{id}/cancel`             | 本人的预约与状态变化                    |
| 收藏房源             | `GET /api/me/favorites`、`PUT/DELETE /api/me/favorites/{roomId}`                | 本人的收藏列表                          |
| 公寓、房间管理       | `/api/admin/catalog/apartments`、`/api/admin/catalog/rooms`                     | 新建、编辑、分页查询、删除              |
| 房间上下架           | `PATCH /api/rooms/{id}/publication`                                             | 是否允许公开展示及预约                  |
| 预约审核、记录、导出 | `/api/admin/appointments`、`/{id}/status`、`/{id}/review-log`、`/export.csv`    | 状态变化、审核记录、CSV 文件            |
| 创建、激活、终止租约 | `/api/admin/leases`、`/{id}/activate`、`/{id}/terminate`                        | 租约状态与账单生成                      |
| 我的账单、演示支付   | `GET /api/me/bills`、`POST /api/me/bills/{id}/pay`                              | 本人的账单和支付状态                    |
| 通知、标记已读       | `GET /api/me/notifications`、`PATCH /api/me/notifications/{id}/read`            | 本人的业务通知                          |

账单中的“演示支付”不连接真实支付平台，不会扣款。房间图为站内 SVG 示意图，界面已标注以实地看房为准。

## 浏览器验证

先在独立的 Python 环境安装 `playwright` 并执行 `playwright install chromium`，保持前端开发服务运行，然后在本目录执行：

```bash
python tests/browser-smoke.py
```

这份脚本拦截 API 并返回固定数据，检查页面、移动端布局、首次并发写请求共享一次 CSRF 初始化及后续缓存复用，以及失败后预约重试的幂等 Key，不写入数据库。此脚本应对开发服务 `5174` 运行。

另有真实前后端联调脚本，需要先按根目录说明启动独立演示后端和初始化演示账号：

```bash
python tests/live-browser-smoke.py
```

真实联调会新建带时间标记的演示账号、公寓、房间、预约和租约，并执行演示支付。覆盖图片上传、房间上下架、预约审核及日志、CSV 导出、租约激活与终止、通知标读、管理员创建停用，并核对所选时段与预约列表的显示时间一致。结束时会将本次创建的房间下架，保留全部历史记录。

脚本仅接受 `127.0.0.1` 或 `localhost` 的 `5174`、`8082` 端口；默认演示账号可通过 `DEMO_ADMIN_USERNAME` 和 `DEMO_ADMIN_PASSWORD` 环境变量覆盖。如需直接验证打包后的 Spring Boot 页面：

```bash
FRONTEND_URL=http://127.0.0.1:8082 python tests/live-browser-smoke.py
```

模拟回归截图默认保存在 `/tmp/shangting-frontend-screenshots/`，包含 `public-desktop.png`、`public-mobile.png`、`admin-desktop.png` 和 `admin-mobile.png`。真实联调截图默认保存在 `/tmp/shangting-live-screenshots/`，包含 `public-desktop.png`、`public-mobile.png`、`admin-desktop.png`、`personal-mobile.png` 和 `admin-mobile.png`，失败时生成 `failure.png`。可用 `FRONTEND_SCREENSHOTS` 环境变量指定保存目录。

本次验证通过的截图已保存在项目内的 [docs/screenshots](docs/screenshots)，可查看[找房首页](docs/screenshots/public-desktop.png)、[移动端首页](docs/screenshots/public-mobile.png)、[运营工作台](docs/screenshots/admin-desktop.png)、[移动端预约审核](docs/screenshots/admin-mobile.png)和[租客预约页](docs/screenshots/personal-mobile.png)。
