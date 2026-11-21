# 数据库入口

完整版本使用独立数据库 `shangting_apartment_complete`。

1. `bash scripts/setup-local.sh` 创建数据库与专用账户。
2. 应用启动时 Flyway 自动执行 `src/main/resources/db/migration/` 的版本化 SQL。
3. `DEMO_ENABLED=true` 时仅在空库初始化合成房源和学习账号。

不需要手动执行旧教学阶段的零散建表脚本，也不需要 Docker。已经执行过的 Flyway 文件应保持不变；结构变更新增 `V2__说明.sql` 等后续迁移。请勿对原项目数据库执行这些文件。
