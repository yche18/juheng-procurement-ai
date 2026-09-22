# 据衡后端

据衡是一个企业采购证据决策与授权平台。本仓库当前按 User Story 逐步交付；US-001 建立 PostgreSQL 本地环境、Flyway 基线迁移和真实数据库集成测试，尚不包含业务表。

## 本地要求

- JDK 17
- Docker Desktop（或兼容的 Docker Engine 与 Compose）
- 可访问 Maven Central，首次运行 Maven Wrapper 时需要下载 Maven 和项目依赖

先确认终端实际使用的是 Java 17：

```shell
java -version
```

如果电脑安装了多个 JDK，请先在 IDE 或终端中把 `JAVA_HOME` 指向 JDK 17。

## 启动 PostgreSQL

项目提供本地开发用的 `compose.yaml`。其中的账号和密码只用于本机开发，均可通过应用环境变量覆盖。

```shell
docker compose up -d postgres
docker compose ps
```

`postgres` 显示为 `healthy` 后即可启动应用。数据库数据保存在 Docker volume 中，执行 `docker compose down` 只停止并移除容器，不会删除该 volume。

默认连接参数：

| 配置 | 默认值 | 环境变量 |
| --- | --- | --- |
| JDBC URL | `jdbc:postgresql://localhost:5432/juheng` | `JUHENG_DB_URL` |
| 用户名 | `juheng` | `JUHENG_DB_USERNAME` |
| 密码 | `juheng_local` | `JUHENG_DB_PASSWORD` |

## 启动应用

Windows PowerShell：

```powershell
.\mvnw.cmd spring-boot:run
```

macOS / Linux：

```shell
./mvnw spring-boot:run
```

应用启动时 Flyway 会先校验并应用 `src/main/resources/db/migration` 下尚未执行的迁移。已成功执行的版本记录在 `flyway_schema_history` 中，重复启动不会重复执行。

应用默认监听 `http://localhost:8080`。服务启动后可检查：

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

也可以使用：

```shell
curl http://localhost:8080/actuator/health
```

正常响应为：

```json
{"groups":["liveness","readiness"],"status":"UP"}
```

`groups` 表示 Spring Boot 内置的存活与就绪检查分组。健康端点只返回分组名称和汇总状态，不公开组件详情、配置或凭据。

## 运行测试

Windows PowerShell：

```powershell
.\mvnw.cmd clean test
```

macOS / Linux：

```shell
./mvnw clean test
```

完整测试包含基于 Testcontainers 的 PostgreSQL 集成测试，因此运行前需要启动 Docker。测试会自行创建和销毁临时 PostgreSQL 容器，不会使用或修改 `compose.yaml` 创建的本地数据库。

应用上下文和健康端点测试使用 `no-database` profile，继续保持为不依赖 PostgreSQL 的快速测试。Flyway 集成测试会验证空库迁移、迁移校验以及重复执行不会再次应用基线。
