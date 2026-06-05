# auto-log

[![Maven Central](https://img.shields.io/maven-central/v/io.github.ofz/auto-log.svg)](https://search.maven.org/search?q=g:io.github.ofz%20AND%20a:auto-log)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

Auto-logging framework for Spring Boot powered by [LMAX Disruptor](https://lmax-exchange.github.io/disruptor/). 

Compatible with **Spring Boot 2.x / 3.x** and **JDK 8+**.

## 特性 / Features

- 🏷️ **声明式注解** `@AutoLog` —— 一行注解即可记录方法调用日志，支持类级别和方法级别
- ⚡ **异步高性能** —— 基于 LMAX Disruptor 无锁环形缓冲区，零 GC 压力
- 🎨 **日志格式可定制** —— 支持占位符模板 `{class}`, `{method}`, `{args}`, `{result}`, `{time}`, `{status}`, `{operator}`, `{traceId}`
- 📋 **参数名自动映射** —— 日志中 `参数名=值` 一一对应（基于 `-parameters` 编译标志）
- 🚫 **类型排除** —— 通过 `excludeTypes` 排除 `HttpServletRequest` 等框架对象
- 👤 **操作人追踪** —— 可插拔 `OperatorProvider`，默认输出 `system`，支持对接 Spring Security 等
- 🔗 **调用链追踪** —— 可插拔 `TraceIdProvider`，默认从 MDC 读取，无缝对接 Sleuth/SkyWalking
- 🔌 **Spring Boot 自动装配** —— 兼容 Spring Boot 2.x / 3.x，引入 starter 即可使用
- 🛡️ **优雅降级** —— Ring Buffer 满时自动 fallback 到同步日志
- 📦 **模块化设计** —— `auto-log-core` 核心库 + `auto-log-starter` 自动装配

## 快速开始 / Quick Start

### 1. Maven 依赖

```xml
<dependency>
    <groupId>io.github.ofz</groupId>
    <artifactId>auto-log-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. 使用注解

```java
@Service
public class UserService {

    // 基础用法：自动拼接参数名和值
    @AutoLog(value = "用户登录: {method} | 耗时 {time}ms")
    public User login(String username, String password) {
        // 日志输出: username=admin, password=***
        return user;
    }

    // 不记录参数和返回值
    @AutoLog(logArgs = false, logResult = false)
    public String generateToken(User user) {
        return token;
    }

    // 排除框架对象
    @AutoLog(excludeTypes = {HttpServletRequest.class, HttpServletResponse.class})
    public Result handle(HttpServletRequest req, HttpServletResponse res, String userId) {
        return result;
    }
}
```

### 3. 输出示例

```
[AutoLog] system | - | com.example.UserService#login | SUCCESS | 12ms | args=[username=admin, password=***] | result=User{id=1, name='admin'}
[AutoLog] admin | abc123 | com.example.UserService#handle | SUCCESS | 5ms | args=[userId=xxx] | result=Result{code=200}
[AutoLog] system | - | com.example.UserService#validate | FAILURE | 2ms | args=[input=invalid_input] | result=ValidationException
```

## 注解属性 / Annotation Attributes

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `value()` | `String` | `""` | 自定义模板，支持 8 种占位符：`{class}` `{method}` `{args}` `{result}` `{time}` `{status}` `{operator}` `{traceId}` |
| `logArgs()` | `boolean` | `true` | 是否记录参数，关闭后显示 `[filtered]` |
| `logResult()` | `boolean` | `true` | 是否记录返回值，关闭后显示 `[filtered]` |
| `logTime()` | `boolean` | `true` | 是否记录执行耗时 |
| `logException()` | `boolean` | `true` | 是否在发生异常时记录异常详情 |
| `level()` | `String` | `"INFO"` | 日志级别：`TRACE` / `DEBUG` / `INFO` / `WARN` / `ERROR` |
| `excludeTypes()` | `Class<?>[]` | `{}` | 排除的参数类型数组，不参与日志拼接 |

### 参数名映射

默认使用 `-parameters` 编译标志从字节码中提取真实参数名，输出格式为 `参数名=值, 参数名=值`。如果未开启 `-parameters`，回退为 `arg0=值, arg1=值`。

### `excludeTypes` 类型排除

适用于 Controller 层排除 `HttpServletRequest`、`HttpServletResponse`、`MultipartFile` 等框架注入对象，避免日志中出现无意义的 `toString()` 输出。

```java
@AutoLog(excludeTypes = {HttpServletRequest.class, HttpServletResponse.class})
public Result save(HttpServletRequest req, HttpServletResponse res,
                   @RequestBody UserDto dto) {
    // 日志仅输出 dto=UserDto{name=...}，req/res 被排除
}
```

类型匹配使用 `Class.isInstance()`（支持子类），即排除 `excludeTypes = {InputStream.class}` 时，`FileInputStream`、`ByteArrayInputStream` 等也会被排除。

## 配置 / Configuration

在 `application.yml` 中配置：

```yaml
auto:
  log:
    enabled: true              # 是否启用，默认 true
    ring-buffer-size: 2048     # Ring Buffer 大小（自动向上取整为 2 的幂）
    wait-strategy: BLOCKING    # 等待策略: BLOCKING | SLEEPING | YIELDING | BUSY_SPIN
    producer-type: MULTI       # 生产者类型: SINGLE | MULTI
    thread-name-prefix: log-   # 消费者线程名前缀
    level: INFO                # 默认日志级别
```

### Wait Strategy 选择

| 策略 | 延迟 | CPU 占用 | 适用场景 |
|------|------|---------|---------|
| `BLOCKING` | 较高 | 低 | 通用场景（默认） |
| `SLEEPING` | 中等 | 中等 | 平衡延迟与 CPU |
| `YIELDING` | 较低 | 高 | 低延迟要求 |
| `BUSY_SPIN` | 最低 | 最高 | 极致低延迟 |

## 自定义日志格式 / Custom Formatter

实现 `LogFormatter` 接口并注册为 Spring Bean：

```java
@Component
public class JsonLogFormatter implements LogFormatter {
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String format(LogContext ctx) {
        // 返回 JSON 格式日志
        Map<String, Object> log = Map.of(
            "class", ctx.getClassName(),
            "method", ctx.getMethodName(),
            "status", ctx.getStatus().name(),
            "time_ms", ctx.getExecutionTimeMs()
        );
        return mapper.writeValueAsString(log);
    }
}
```

## 操作人 / Operator

默认返回 `"system"`。实现 `OperatorProvider` 接口对接认证系统：

```java
@Component
public class SecurityOperatorProvider implements OperatorProvider {
    public String getOperator() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "anonymous";
    }
}
```

## 调用链追踪 / Trace ID

默认从 SLF4J [MDC](https://www.slf4j.org/manual.html#mdc) 中读取 `"traceId"` 键，没有则日志中显示 `-`。与常见追踪框架的集成方式：

**Sleuth / SkyWalking / OpenTelemetry** — 自动填充 MDC，无需额外配置。

**手动设置**（Filter / Interceptor）：

```java
MDC.put("traceId", request.getHeader("X-Trace-Id"));
try {
    chain.doFilter(req, res);
} finally {
    MDC.remove("traceId");
}
```

**自定义实现**（覆盖默认行为）：

```java
@Component
public class SkyWalkingTraceIdProvider implements TraceIdProvider {
    public String getTraceId() {
        return TraceContext.traceId();  // 直接读 SkyWalking 上下文
    }
}
```

## 模块结构 / Module Structure

```
auto-log/
├── pom.xml                         # 父 POM（依赖管理）
├── auto-log-core/                  # 核心模块
│   └── src/main/java/io/github/ofz/autolog/
│       ├── annotation/AutoLog.java         # @AutoLog 注解
│       ├── aspect/AutoLogAspect.java       # AOP 切面
│       ├── context/LogContext.java         # 日志上下文
│       ├── disruptor/                      # Disruptor 基础设施
│       │   ├── DisruptorConfig.java
│       │   ├── LogEvent.java
│       │   ├── LogEventFactory.java
│       │   ├── LogEventHandler.java
│       │   └── LogEventProducer.java
│       ├── formatter/                      # 格式化器
│       │   ├── LogFormatter.java
│       │   └── DefaultLogFormatter.java
│       └── provider/                        # 可插拔策略
│           ├── OperatorProvider.java
│           ├── DefaultOperatorProvider.java
│           ├── TraceIdProvider.java
│           └── DefaultTraceIdProvider.java
└── auto-log-starter/               # Spring Boot Starter
    └── src/main/
        ├── java/io/github/ofz/autolog/autoconfigure/config/
        │   ├── AutoLogAutoConfiguration.java   # 自动装配
        │   └── AutoLogProperties.java          # 配置属性
        └── resources/META-INF/
            ├── spring.factories
            ├── spring-configuration-metadata.json
            └── spring/
                └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

## License

Apache License 2.0 — see [LICENSE](LICENSE) for details.
