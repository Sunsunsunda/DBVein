# DBVein

**看清数据库结构，发现隐藏关系。**

DBVein（库脉）是一个数据库结构探索与关系分析工具，通过交互式画布和数据验证，帮助开发者理解缺少文档或外键约束的数据库。

Explore database schemas, discover hidden relationships, and validate them against real data.

适用于旧系统接手、数据迁移前分析、业务梳理和跨表数据排查。通过查看结构和样本、建立并验证关联，逐步整理出可理解的数据库关系地图。

当前为开源化准备版，默认本地、单用户使用。支持 Oracle / MySQL 适配；本次整理尚未完成真实数据库集成验证。

## 功能

- 数据源管理：新建、编辑、测试连接、删除及关联项目检查。
- 项目管理与数据源绑定。
- 选择 Schema，采集表、字段、注释、主键及外键到本地快照。
- 表关系画布、字段搜索、关键字段筛选、字段连线、布局保存与拖动撤销。
- 侧栏边缘折叠、底部数据预览、人工确认或忽略关系。
- 关联验证：单侧抽样后到对方完整数据列查找，提供匹配率与未匹配示例；抽样验证不能替代全量完整性检查。
- 分页数据预览、表及字段抽样统计。
- 根据抽样值包含率推荐关系，可选业务规则加权。
- 表和字段的本地含义备注，不回写源库。

默认不启用行业规则。房屋层级规则是历史使用场景的可选模板。

## Docker 启动

需要 Docker Compose。容器构建需要联网下载依赖。

1. 将 `.env.example` 复制为 `.env`。
2. 生成随机密钥并填入 `DBVEIN_CRYPTO_KEY`，例如使用 `node -e "console.log(require('crypto').randomBytes(16).toString('hex'))"`。不要提交 `.env`；密钥应与数据卷一起妥善保存。
3. 执行 `docker compose up --build -d`。
4. 打开 http://localhost:5173，创建数据源和项目。

后端不映射到宿主机，前端只绑定回环地址。SQLite 数据持久化到 `dbvein-data` 卷。应用尚无登录及多用户隔离，不适合直接暴露到公网。

### 示例数据库

执行 `docker compose --profile demo up --build -d`，等待 MySQL 就绪。在页面新建 MySQL 数据源：

| 字段 | 值 |
|---|---|
| 主机 | demo-db |
| 端口 | 3306 |
| 数据库 | dbvein_demo |
| 用户 | dbvein_reader |
| 密码 | demo-reader-local-only |

选择 `dbvein_demo` 拉取元数据，将三张表添加到画布，运行关系发现。

预期：`order_items.order_id → orders.id` 是数据库外键；`orders.customer_ref → customers.id` 是待发现关联；`status` 是共享状态码，不应被当作外键。推荐结果仍需人工验证。示例账号仅有 SELECT 权限，数据全部为合成数据。

## 本地开发

需要 JDK 17、Maven、Node.js 24 和 npm。

后端（在 `backend` 下；PowerShell 示例）：

```powershell
$env:DBVEIN_CRYPTO_KEY='替换为随机生成并稳定保存的32位ASCII字符串'
mvn spring-boot:run
```

前端（在 `frontend` 下）：

```sh
npm ci
npm run dev
```

前端为 5173，代理到后端 8081。后端默认只监听 127.0.0.1。本地 SQLite 位于 `backend/data/dbvein.db`。

| 环境变量 | 用途 |
|---|---|
| DBVEIN_CRYPTO_KEY | 必填，数据源密码加密密钥；现有算法使用前16个UTF-8字节 |
| DBVEIN_DATA_DIR | SQLite 目录，默认 ./data |
| SERVER_PORT | 后端端口，默认 8081；修改后需同步前端代理 |
| SERVER_ADDRESS | 监听地址，默认 127.0.0.1 |

## 已知限制

- **快照刷新尚未继承旧画布、备注和人工关系。已有分析建议新建项目重新采集，操作前备份。**
- 仅支持画布范围关系发现，每对表最多比较 80 组候选字段；当前按单列比较，未支持复合键推断。
- 抽样包含率是调查线索，不是全量完整性证明；Oracle ROWNUM 抽样可能存在分布偏差。
- 关系发现是同步请求，尚未提供任务进度和取消。
- 目前没有应用内项目导入导出。停止后端后备份完整数据目录，并保存同一密钥；Docker 使用数据卷备份方式。数据目录含连接信息，不能提交到公开仓库。
- 密码加密仍沿用原项目 AES/ECB，认证加密升级列入发布前工作；请使用只读、最小权限的源库账号。

路线见 [ROADMAP.md](docs/ROADMAP.md)，贡献方式见 [CONTRIBUTING.md](CONTRIBUTING.md)。许可证尚待项目所有者确认，此准备版暂未授予开源许可。
