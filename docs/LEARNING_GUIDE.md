# Java 后端求职学习路线

项目已经实现，学习时按依赖顺序看代码；无需先逐行读完前端。

## 第一轮：沿业务走通

1. `ShangtingApartmentApplication`：启动、组件扫描和配置属性绑定。
2. `CatalogController → CatalogService`：HTTP参数、DTO校验、分页、结果映射。
3. `UserAuthController / AdminAuthController`：密码验证、Session创建、登录更换Session、退出。
4. `AdminSessionAuthenticationFilter → SecurityConfig`：认证信息、ROLE_USER/ADMIN、401和403。
5. `MyAppointmentController → AppointmentBookingService → AppointmentRequestService → AppointmentService`：预约主线。
6. `AppointmentReviewService`：管理员审核及操作留痕。
7. `LeasingService`：租约状态机、金额、账期与支付幂等。
8. `frontend/src/api.ts`、`hooks.tsx` 的会话Context与 `pages/`：页面动作如何对应HTTP请求及后端返回。

## 第二轮：关键知识对应位置

|知识|项目中的实际位置|需要能解释的问题|
|---|---|---|
|类、接口、record、泛型|entity/dto/BaseMapper/Page|实体和DTO为什么分开？|
|集合与循环|分页DTO组装、查询参数集合|List用于哪里？为什么不共享请求可变字段？|
|依赖注入/Bean|构造方法、配置类|谁创建Service并传参数？|
|Spring MVC|Controller、ModelAttribute、RequestBody|GET参数和JSON怎样转对象？|
|校验与异常|DTO注解、GlobalExceptionHandler|哪些错误发生在Controller之前？|
|Spring AOP|TimedOperation、MethodTimingAspect|代理何时生效？为什么this调用绕过？|
|JDBC与MyBatis|mapper、JdbcClient|占位符为什么用参数绑定？|
|事务传播|AppointmentRequestService.bookOnce、AppointmentReviewService|哪些方法共用事务？哪里必须退出事务再读？|
|隔离级别与当前读|租约写入、FOR UPDATE/FOR SHARE|快照旧视图如何影响幂等响应？|
|行锁与CAS|预约容量、取消/审核、签约|两个并发请求谁成功？名额如何恢复？|
|唯一索引与幂等|appointment_request、bill|为什么先查再插不能保证不重复？|
|联合索引/执行计划|Flyway索引、预约筛选|索引列顺序和WHERE顺序有何区别？|
|连接池|Hikari配置|等连接和SQL慢怎么区分？|
|Redis缓存|PublicApartmentService、ApartmentListCache|命中、回源、TTL、提交后失效与故障降级|
|Lua限流|RedisFixedWindowRateLimiter|计数与过期怎样连续执行？固定窗口的边界是什么？|
|共享Session|Spring Session配置、Session辅助类|Cookie/Session/认证上下文各存什么？|
|Web安全|CSRF、角色、owner查询|为什么用户不能靠传userId访问别人账单？|
|受控异步|AsyncWebConfig、CSV导出|为什么线程池和队列要有上限？MDC怎样传播？|
|定时任务|LeaseMaintenanceJob|宕机后怎样补账？重复执行为何不重复开账？|
|数据库迁移|db/migration|建表脚本如何跟随版本？为何不手改旧库？|
|自动化测试|src/test、scripts/*test*|单元、MVC、真实DB、浏览器各能证明什么？|
|前后端协作|API客户端、会话Context、各页面|页面→HTTP→Controller→返回值怎样连起来？|

## 第三轮：用实际代码回答面试题

- 预约数据库已经有行锁，为什么还需要幂等Key？两者分别解决什么？
- 同一Key不同用户或不同请求体，为何必须拒绝？Hash中包含了哪些信息？
- 取消和管理员拒绝并发时，为何不会释放两次名额？
- 审核记录插入失败，为什么状态也必须回滚？
- 两个管理员同时签同一房间，如何防止租期重叠？
- 第二次支付请求拿到PAID后，返回DTO为什么仍可能显示UNPAID？代码如何避免旧快照？
- Redis宕机，哪些能力降级，哪些返回503？Session与缓存的故障策略为何不同？
- 为什么健康检查UP不代表所有业务正确？
- CSV为什么要防公式注入？图片为什么不保留客户端文件名和SVG？
- 为什么不把所有查询条件都建成一条巨大的联合索引？
- 当前“演示支付”和真实第三方支付还差哪些协议、回调验证、对账与资金处理？

## 建议练习

先读 `src/main/resources/db/migration/V1__rental_platform.sql` 的表关系：`app_user` 对应租客，`apartment → room → appointment_slot` 对应房源与时段，`appointment_request → appointment` 对应幂等登记与预约，`appointment_review_log` 记录管理员审核；`lease_contract → bill` 对应租约和账单，`favorite`、`notification` 按用户归属。外键负责关联和完整性，用户隔离仍要由权限与带用户条件的查询实现。

先自己画一次“找房→预约→审核→签约→支付”的调用链，再对照源码。接着修改一个小需求，例如增加房源面积筛选：先写DTO和契约，再改查询、前端与测试。最后讲出失败路径和并发路径，而不只讲顺利流程。

阅读测试时区分证据：`LeasingServiceTest` 等替身测试验证规则与分支，`TenantLeasingControllerTest` 等MVC测试验证HTTP绑定及归属传递，`ConcurrentPaymentIntegrationTest` 验证H2事务中的重复支付响应；H2不等同于MySQL的锁行为。`scripts/smoke-test.mjs` 与 `scripts/concurrency-test.mjs` 才会调用本机运行的完整应用，验证真实MySQL/Redis链路。浏览器验证另外检查页面交互，不代替后端授权与并发验证。

掌握本项目能建立完整的Java后端基础；并不意味着必须把消息队列、搜索引擎、微服务等工具全塞进同一个项目。后续按岗位需求选择一个有真实问题支撑的方向深入。
