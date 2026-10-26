# 参与开发

项目目前处于公开发布前整理阶段，许可证与贡献授权条款尚待确定。

## 开发检查

- 前端：`npm ci`、`npm run build`、`npm run lint`。
- 后端：JDK 17 下执行 `mvn verify`。
- 集成：启动示例 MySQL，验证 Schema 查询、结构采集、外键与推断关系、分页与统计。
- 修改快照或持久化逻辑时，必须覆盖更新继承、对象删除、跨 Schema 外键和失败回滚。

## 代码结构

- `backend/.../datasource`：连接配置与密码保存。
- `backend/.../metadata`：数据库适配器、快照和数据预览统计。
- `backend/.../analysis`：画布、关系验证、关系发现及可选规则。
- `frontend/src/pages`：项目页与分析页。
- `frontend/src/canvas`：React Flow 画布。

新增数据库：实现 DatabaseAdapter，注册 DatabaseType、DatabaseAdapterRegistry 和 JdbcConnectionFactory，补充前端连接字段与测试。不要加入真实账号、数据库文件、业务样本或本机路径。

问题报告请包含数据库类型/版本、复现步骤、合成结构和预期结果；移除凭据和真实业务数据。
