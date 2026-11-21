# HTTP 接口索引

分页接口返回 `{records,total,page,pageSize}`，页码从1开始，每页最多100条。收藏和通知直接返回数组，通知仅返回最近100条。时间戳使用含UTC偏移的ISO8601格式；租期和账单到期日使用 `yyyy-MM-dd`，业务日期按上海时区判断；金额单位为元。

浏览器通过Cookie携带会话。写请求需要先获取 `GET /api/csrf`，再携带返回的 `headerName` 与 `token`；有请求体时发送JSON，图片上传使用multipart。创建预约额外携带稳定的UUID `Idempotency-Key`。

|模块|接口|权限|
|---|---|---|
|会话|GET /api/session；GET /api/csrf|公开|
|租客身份|POST /api/auth/register；POST /api/auth/login；POST /api/auth/logout|公开但写请求需CSRF|
|管理员身份|POST /api/admin/auth/login；POST /api/admin/auth/logout；GET /api/admin/auth/me|登录/退出公开，me需ADMIN|
|房源|GET /api/public/listings；GET /api/public/listings/{id}|公开|
|时段|GET /api/public/rooms/{id}/appointment-slots|公开|
|个人预约|GET/POST /api/me/appointments；POST /api/me/appointments/{id}/cancel|USER且仅本人|
|收藏|GET /api/me/favorites；PUT/DELETE /api/me/favorites/{roomId}|USER|
|个人租约账单|GET /api/me/leases；GET /api/me/bills；POST /api/me/bills/{id}/pay|USER且仅本人|
|通知|GET /api/me/notifications；PATCH /api/me/notifications/{id}/read|USER且仅本人|
|公寓管理|GET/POST /api/admin/catalog/apartments；PUT/DELETE /api/admin/catalog/apartments/{id}|ADMIN|
|房间管理|GET/POST /api/admin/catalog/rooms；PUT/DELETE /api/admin/catalog/rooms/{id}|ADMIN|
|上架|PATCH /api/rooms/{id}/publication|ADMIN|
|图片|POST /api/admin/catalog/uploads，multipart字段file|ADMIN|
|时段管理|GET/POST /api/admin/appointment-slots|ADMIN|
|审核|GET /api/admin/appointments；PATCH /api/admin/appointments/{id}/status；GET /api/admin/appointments/{id}/review-log|ADMIN|
|导出|GET /api/admin/appointments/export.csv|ADMIN|
|租约管理|GET/POST /api/admin/leases；POST /api/admin/leases/{id}/activate；POST /api/admin/leases/{id}/terminate|ADMIN|
|账单管理|GET /api/admin/bills；POST /api/admin/bills/{id}/mark-paid|ADMIN|
|账户管理|GET/POST /api/admin/accounts；PATCH /api/admin/accounts/{id}/enabled|ADMIN|
|租客管理|GET /api/admin/users；PATCH /api/admin/users/{id}/enabled|ADMIN|
|运营/维护|GET /api/admin/dashboard；POST /api/admin/maintenance/run|ADMIN|
|健康|GET /actuator/health|公开，仅总状态|

## 查询参数

房源：`city,q,minRent,maxRent,sort,page,pageSize`。排序支持`newest,rentAsc,rentDesc,areaDesc`。

管理员预约列表和导出：`status,roomId,startTime,endTime`，列表还支持 `page,pageSize`。时间范围为 `[startTime,endTime)`。URL中的 `+08:00` 必须编码为 `%2B08:00`。个人预约列表只接受 `page,pageSize`，用户身份由服务端读取。

管理员租约：`status,roomId,page,pageSize`；管理员账单：`status,leaseId,page,pageSize`。个人租约、个人账单只接受 `status,page,pageSize`，不能通过传入 `userId` 切换归属。

## 状态

预约：PENDING→CONFIRMED/REJECTED，租客可将未来PENDING/CONFIRMED取消为CANCELLED。

租约：DRAFT→ACTIVE→TERMINATED/EXPIRED；DRAFT也可终止，或由维护任务在租期结束后改为EXPIRED。新建租期不能早于今天，区间为 `[startDate,endDate)`，DRAFT和ACTIVE均参与重叠校验。

账单：UNPAID→PAID/OVERDUE/CANCELLED，OVERDUE→PAID/CANCELLED；PAID重复支付返回原记录。激活生成第一期整月租金加押金，以后按起租日生成整月租金，不按天折算。终止取消未付/逾期账单，保留已付记录，退款需线下处理。到期租约的已有欠款仍可支付。

租客支付为DEMO状态流转，关闭 `DEMO_PAYMENTS_ENABLED` 后拒绝该操作；管理员人工收款登记仍可用。这两个接口都不调用真实支付渠道。

## 调用示例

租客已登录并取得CSRF令牌后，选择时段提交：

```http
POST /api/me/appointments
Content-Type: application/json
Idempotency-Key: 40c727a5-08f5-43a7-a5ef-5bcd6759d8fe
X-CSRF-TOKEN: <GET /api/csrf 返回的 token，以其 headerName 为准>

{"slotId":12,"contactName":"演示租客","contactPhone":"13800000000"}
```

响应为 `201` 和 `{"id":123,"status":"PENDING"}`。同一预约意图重试保留Key；换用户、联系人或时段须使用新的Key。同一Key配不同内容返回409。预约时间和房间由服务端根据时段查询，不信任客户端传入。

## 保留的教学接口

原先的小步骤代码仍可阅读和调用：管理员 `/api/apartments` CRUD、`POST /api/apartments/with-room`、`/api/rooms` CRUD，以及 `GET /api/rooms/count`、`GET /api/rooms/rent-stats`、`GET /api/rooms/{id}/detail`。公开查询包括 `GET /api/public/apartments`、`GET /api/public/rooms` 和 `GET /api/public/rooms/{id}`。它们与catalog接口使用同一组公寓/房间表，前端工作台主要调用catalog接口。

`POST /api/public/appointments` 保留匿名预约教学流程，同样需要CSRF、幂等Key并受限流保护，但不记录租客归属，不能进入个人预约取消流程。当前租客页面使用 `/api/me/appointments`，需要登录。

## 错误

业务及安全错误使用ProblemDetail，含 `status/detail/requestId`，参数校验可能含 `errors` 字段。400参数错误、401未登录、403角色/CSRF拒绝、404资源不存在或不属于本人、409业务冲突、429限流、500内部错误、503基础设施不可用。CSV响应开始传输后若连接或查询失败，不能再承诺完整的ProblemDetail正文，客户端应将下载视为失败。

前端不要把所有403自动重试成写请求；CSRF缓存失效后由用户重新操作。登录、注册、退出更换会话后必须清除旧CSRF缓存。
