# 据衡后端

据衡是一个企业采购证据决策与授权平台。本仓库当前按 User Story 逐步交付；US-000 只建立可启动的 Spring Boot 服务和非敏感健康检查，不依赖数据库、模型或其他外部基础设施。

## 本地要求

- JDK 17
- 可访问 Maven Central，首次运行 Maven Wrapper 时需要下载 Maven 和项目依赖

先确认终端实际使用的是 Java 17：

```shell
java -version
```

如果电脑安装了多个 JDK，请先在 IDE 或终端中把 `JAVA_HOME` 指向 JDK 17。

## 启动应用

Windows PowerShell：

```powershell
.\mvnw.cmd spring-boot:run
```

macOS / Linux：

```shell
./mvnw spring-boot:run
```

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

US-000 的测试覆盖应用上下文和健康端点，不需要启动 PostgreSQL、真实模型或其他基础设施。
