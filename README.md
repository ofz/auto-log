# auto-log

高性能 Spring Boot 自动日志框架，基于 [LMAX Disruptor](https://lmax-exchange.github.io/disruptor/) 异步管道。

兼容 **Spring Boot 2.x / 3.x** · **JDK 8+**。

## 特性

- **异步非阻塞** — Disruptor RingBuffer 无锁写入，日志 IO 不阻塞业务线程
- **对象池** — LogContext 稳态零分配，低 Young GC
- **零拷贝模板引擎** — 预编译 + 缓存 + 单 pass StringBuilder 渲染
- **SpEL 模板** — `#{#paramName}` 嵌入参数值，`#{#result}` 引用返回值
- **SpEL 条件** — `condition` 表达式控制是否记录
- **慢调用检测** — 超过阈值自动升级为 WARN，无需接 APM
- **双层敏感过滤** — 全局 `SensitiveDataFilter` SPI + 注解级 `sensitiveParams`
- **安全序列化** — `toString()` 异常兜底、数组感知输出、参数/返回值截断
- **可插拔 SPI** — `OperatorProvider` · `TraceIdProvider` · `AttributeProvider` · `SensitiveDataFilter` · `LogFormatter`
- **优雅停机** — 10s 超时 + halt 兜底，无线程泄漏
- **指标监控** — `auto-log-metrics` 模块，Micrometer / SLF4J 双通道
- **Spring Boot 2.x / 3.x 兼容** — 零 `javax`/`jakarta` 依赖

## 快速开始

### 1. 引入依赖

```xml
<dependency>
    <groupId>io.github.ofz</groupId>
    <artifactId>auto-log-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. 添加注解

```java
@Service
public class UserService {

    @AutoLog("用户登录: {method}")
    public User login(String username, String password) {
        return user;
    }
}
```

输出：
```
[AutoLog] system | - | com.example.UserService#login | SUCCESS | 12ms | args=[username=admin, password=***] | result=User{id=1}
```

### 3. 指标监控（可选）

```xml
<dependency>
    <groupId>io.github.ofz</groupId>
    <artifactId>auto-log-metrics</artifactId>
    <version>1.0.0</version>
</dependency>
```

有 Actuator → 自动对接 Prometheus/Grafana。无 Actuator → 每 60s 打一行 INFO 汇总。

---

## 注解属性

| 属性 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `value()` | `String` | `""` | 自定义消息模板 |
| `level()` | `String` | `"INFO"` | 日志级别：TRACE / DEBUG / INFO / WARN / ERROR |
| `logArgs()` | `boolean` | `true` | 是否记录入参 |
| `logResult()` | `boolean` | `true` | 是否记录返回值 |
| `logTime()` | `boolean` | `true` | 是否记录耗时 |
| `logException()` | `boolean` | `true` | 是否记录异常 |
| `excludeTypes()` | `Class<?>[]` | `{}` | 按类型排除参数（如 HttpServletRequest） |
| `condition()` | `String` | `"true"` | SpEL 条件表达式，false 则跳过 |
| `sensitiveParams()` | `String[]` | `{}` | 注解级敏感参数名，值显示为 `***` |
| `slowThresholdMs()` | `long` | `0` | 慢调用阈值（ms），超过升级为 WARN，0=关闭 |

### 注解合并规则

`@AutoLog` 可放在类和方法上，同时存在时：

| 属性 | 规则 |
|---|---|
| `value()` | 方法非空 > 类非空 > `""` |
| `excludeTypes()` | 方法非空 > 类非空 > `{}` |
| `sensitiveParams()` | **类 + 方法合并**（叠加） |
| `condition()` `level()` `slowThresholdMs()` | **方法覆盖类** |
| 布尔型（`logArgs` 等） | **方法覆盖类** |

---

## 模板语法

### 框架占位符

| 占位符 | 说明 |
|---|---|
| `{class}` | 全限定类名 |
| `{method}` | 方法名 |
| `{args}` | 方法入参（`paramName=value, ...`） |
| `{result}` | 返回值 |
| `{time}` | 执行耗时（ms） |
| `{status}` | SUCCESS / FAILURE |
| `{operator}` | 操作人 |
| `{traceId}` | 调用链 ID |
| `{slow}` | 慢调用时输出 `SLOW`，否则空 |

### SpEL 表达式

用 `#{#expression}` 嵌入动态值，预编译 + 缓存，仅在含 `#{...}` 的模板中激活：

```java
@AutoLog("用户 #{#username} 调用 {method}，结果 #{#result != null ? '成功' : '失败'}")
@AutoLog("金额 #{#amount > 1000 ? 'HIGH' : 'NORMAL'}，耗时 {time}ms")
```

可用的 SpEL 变量：`#参数名`（所有方法参数）、`#result`、`#exception`。

---

## 使用示例

### 慢调用检测

```java
@AutoLog(value = "查询订单", slowThresholdMs = 500)
public Order queryOrder(String orderId) { ... }
// 超过 500ms → 自动升级为 WARN，SLF4J 输出 [AutoLog] SLOW ...
```

### SpEL 条件

```java
// 仅记录有返回值的情况
@AutoLog(condition = "#result != null")

// 跳过 admin 操作
@AutoLog(condition = "#username != 'admin'")

// 仅记录异常
@AutoLog(condition = "#exception != null")
```

### 敏感数据过滤

```java
// 注解级精确匹配
@AutoLog(sensitiveParams = {"creditCard", "ssn"})
public void pay(String creditCard, String ssn, BigDecimal amount) { ... }
// 输出: creditCard=***, ssn=***, amount=100.00
```

```yaml
# 全局关键词匹配（同 auto.log.sensitive-keywords）
auto:
  log:
    sensitive-keywords: phone,idCard,address
```

### 排除框架对象

```java
@AutoLog(excludeTypes = {HttpServletRequest.class, HttpServletResponse.class})
public Result save(HttpServletRequest req, HttpServletResponse res, @RequestBody UserDto dto) {
    // 日志仅输出 dto=UserDto{...}，req/res 被跳过
}
```

### before 钩子（查旧值快照）

```java
@Component
public class AuditSnapshotProvider implements AttributeProvider {
    @Autowired private UserMapper userMapper;

    @Override
    public Map<String, Object> getAttributes(Method m, Object[] args, Class<?> cls) {
        Map<String, Object> attrs = new HashMap<>();
        if (args.length > 0 && args[0] instanceof Long) {
            User old = userMapper.findById((Long) args[0]);
            if (old != null) attrs.put("before", old);
        }
        return attrs;
    }
}
```

---

## SPI 扩展点

| 接口 | 作用 | 默认实现 |
|---|---|---|
| `OperatorProvider` | 获取当前操作人 | `"system"` |
| `TraceIdProvider` | 获取调用链 ID | 读 MDC 的 `traceId` 键 |
| `AttributeProvider` | 注入自定义属性（含 method/args 上下文） | 空 Map |
| `SensitiveDataFilter` | 判断参数是否敏感 | 16 个内置关键词 |
| `LogFormatter` | 完全控制日志格式 | `DefaultLogFormatter` |

全部是 `@FunctionalInterface`，注册一个 Spring Bean 即可替换。

```java
@Component
public class MyOperatorProvider implements OperatorProvider {
    public String getOperator() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
```

---

## 配置

```yaml
auto:
  log:
    enabled: true
    ring-buffer-size: 2048          # RingBuffer 大小，自动取 2 的幂
    wait-strategy: BLOCKING         # BLOCKING | SLEEPING | YIELDING | BUSY_SPIN
    producer-type: MULTI            # SINGLE | MULTI
    thread-name-prefix: log-        # 消费者线程名前缀
    level: INFO                     # 默认日志级别
    sensitive-keywords: phone,ssn   # 全局敏感关键词（逗号分隔）
    max-arg-length: 200             # 单个参数值最大长度，超出截断（0=不限制）
    max-result-length: 500          # 返回值最大长度，超出截断（0=不限制）
```

### Wait Strategy

| 策略 | 延迟 | CPU | 场景 |
|---|---|---|---|
| `BLOCKING` | 较高 | 低 | 通用（默认） |
| `SLEEPING` | 中 | 中 | 平衡 |
| `YIELDING` | 较低 | 高 | 低延迟 |
| `BUSY_SPIN` | 最低 | 最高 | 极致低延迟 |

---

## 指标监控

```yaml
auto:
  log:
    metrics:
      enabled: true               # 默认 true
      report-interval-ms: 60000   # SLF4J 报告间隔，0=关闭
```

| 指标 | 类型 | 说明 |
|---|---|---|
| `autolog.pool.available` | Gauge | 池中可用对象数 |
| `autolog.pool.miss` | Counter | 池耗尽新建次数 |
| `autolog.pool.drop` | Counter | 归还时池满丢弃次数 |
| `autolog.slow` | Counter | 慢调用次数 |
| `autolog.ringbuffer.utilization` | Gauge | RingBuffer 利用率 |

---

## 模块结构

```
auto-log/
├── auto-log-core/           # 核心：注解、AOP、Disruptor、格式化、SPI
├── auto-log-metrics/        # 指标：Micrometer + SLF4J 采集器
└── auto-log-starter/        # 自动装配：AutoConfiguration、配置属性
```

---

## License

Apache License 2.0
