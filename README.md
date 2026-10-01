# kohaku

QQ 机器人开放平台（QQ Bot）的 **Spring Boot 自动配置**：引入 starter + 两个配置项即可连上网关、
收发 QQ 单聊/群聊消息，并实现消息收发章节的全部服务端接口（16 个）。

## 模块

| 模块 | 说明 |
| --- | --- |
| `kohaku-client` | **纯 Java 核心**：网关长连接、开放平台接口客户端、消息模型、事件；仅依赖 `jackson-databind` 与 `slf4j-api`，无任何 Spring |
| `kohaku-spring-boot-autoconfigure` | Spring 装配：属性绑定、生命周期适配（`SmartLifecycle`）、事件转发、自动配置 |
| `kohaku-spring-boot-starter` | 依赖描述，Spring 业务方只引这一个 |
| `kohaku-example` | 可运行的示例机器人（私有凭据放在被 gitignore 的 `secrets/qq-bot.yaml`） |

## 快速开始

```bash
./mvnw -DskipTests install        # 安装到本地仓库（或用私服坐标）
```

```xml
<dependency>
  <groupId>love.aira</groupId>
  <artifactId>kohaku-spring-boot-starter</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

```yaml
kohaku:
  qq:
    app-id: ${QQBOT_APP_ID}
    app-secret: ${QQBOT_APP_SECRET}
    intents:
      - PUBLIC_GUILD_MESSAGES     # 频道内 @机器人 消息（基础权限）
      - GROUP_AND_C2C_EVENT       # 群/单聊事件，需在开放平台申请
    # sandbox: true               # 切换沙箱环境
    # auto-start: false           # 只装配 Bean，不随容器连接网关
    # enabled: false              # 整体关闭本 starter（连凭据校验一并跳过）
```

```java
@EventListener
void onMessage(BotDispatchEvent event) {
    if ("C2C_MESSAGE_CREATE".equals(event.type())) {
        String openid = event.data().path("author").path("user_openid").stringValue();
        String msgId = event.data().path("id").stringValue();
        messages.sendToUser(openid, SendMessageRequest.text("你好").replyingTo(msgId));   // 被动回复
    }
}
```

引入即连接；可直接注入 `QqMessageApi`、`QqMediaApi`、`QqChannelMessageApi`、`QqGatewayClient`、
`QqOpenApiClient`、`AccessTokenProvider`。这些 Bean 都带 `@ConditionalOnMissingBean`，可自行覆写。

## 实现范围

- **网关**：Hello 握手 → Identify/Resume → 按 `heartbeat_interval` 心跳（未收到 ACK 判僵尸连接重连）→
  处理 OpCode 7/9 与关闭码策略 → 指数退避重连 → 优雅停机；事件以 Spring 事件发布
  （`BotReadyEvent`、`BotResumedEvent`、`BotDispatchEvent`，父类 `BotEvent`）。
- **接口**：`POST /v2/users|groups/{openid}/messages`（文本/Markdown/富媒体/输入中状态）、
  `POST /v2/users/{openid}/stream_messages` 流式、`DELETE …/messages/{id}` 撤回、
  `POST /v2/{users|groups}/{openid}/files` + `upload_prepare` + `upload_part_finish` 富媒体上传（含分片流程）、
  频道与频道私信（`/channels/...`、`/users/@me/dms`、`/dms/...`，需私域机器人）。
- **鉴权与错误**：401/403 自动刷新 AccessToken 重试一次；失败统一抛 `QqApiException`
  （带 `httpStatus()` / `code()` / `responseBody()`）。

## 配置项

| 键 | 默认值 | 说明 |
| --- | --- | --- |
| `kohaku.qq.app-id` / `app-secret` | 无（必填） | 机器人凭据，可用环境变量 `KOHAKU_QQ_APPID` / `KOHAKU_QQ_APPSECRET` |
| `kohaku.qq.intents` | `[PUBLIC_GUILD_MESSAGES]` | 多选枚举 `QqIntent`，位掩码自动合成 |
| `kohaku.qq.api-base-url` | `https://api.sgroup.qq.com` | 开放平台地址 |
| `kohaku.qq.token-url` | `https://bots.qq.com/app/getAppAccessToken` | AccessToken 接口 |
| `kohaku.qq.sandbox` | `false` | 为真时强制使用沙箱地址 |
| `kohaku.qq.enabled` | `true` | 置 false 整体关闭 |
| `kohaku.qq.auto-start` | `true` | 是否随容器连接/断开网关 |
| `kohaku.qq.shard-index` / `shard-total` | `0` / `1` | 分片参数 |
| `kohaku.qq.client-name` | `kohaku` | 上报的 `$browser`/`$device` |
| `kohaku.qq.reconnect-initial-delay` / `reconnect-max-delay` | `1s` / `60s` | 重连退避区间 |

IDE 内补全由 `META-INF/spring-configuration-metadata.json` 提供。

## 非 Spring 宿主（纯 Java）用法

只引 `kohaku-client` 即可，classpath 里不需要任何 Spring：

```xml
<dependency>
  <groupId>love.aira</groupId>
  <artifactId>kohaku-client</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

```java
KohakuConfig config = KohakuConfig.of(appId, appSecret, List.of(QqIntent.GROUP_AND_C2C_EVENT));
JsonMapper mapper = new JsonMapper();
HttpClient http = HttpClient.newHttpClient();
var tokens = new AccessTokenProvider(http, mapper, config);
var api = new QqOpenApiClient(http, mapper, config, tokens);
QqGatewayClient client = new QqGatewayClient(config, new QqGatewayApi(api, config), tokens, mapper,
        event -> { if (event instanceof BotReadyEvent ready) { ... } });   // 事件回调
client.start();      // 连接 + 心跳 + 自动重连/Resume
...
client.stop();       // 优雅停机
```

消息接口同样可直接使用：`new QqMessageApi(api)`、`new QqMediaApi(api, http)`、`new QqChannelMessageApi(api)`。

## 事件（强类型反序列化）

官方文档定义的事件会被反序列化为**强类型事件**（继承自 `BotDispatchEvent`，原始报文仍可用 `data()` 取到），
可直接按类型监听；未收录的事件仍以原始 `BotDispatchEvent` 回调。

```java
@EventListener
void onMessage(C2cMessageCreateEvent event) {
    String openid = event.payload().author().userOpenid();
    String msgId  = event.payload().id();
    String idx    = event.payload().messageScene().messageIndex();   // msg_idx，平台要求据此去重
    messages.sendToUser(openid, SendMessageRequest.text("你好").replyingTo(msgId));
}
```

| 分组 | 事件（23 个） |
| --- | --- |
| 单聊/群聊消息 | `C2C_MESSAGE_CREATE`、`GROUP_AT_MESSAGE_CREATE`、`GROUP_MESSAGE_CREATE` |
| 好友与群生命周期 | `FRIEND_ADD`、`FRIEND_DEL`、`C2C_MSG_REJECT`、`C2C_MSG_RECEIVE`、`GROUP_ADD_ROBOT`、`GROUP_DEL_ROBOT`、`GROUP_MSG_REJECT`、`GROUP_MSG_RECEIVE` |
| 频道/子频道 | `GUILD_CREATE/UPDATE/DELETE`、`CHANNEL_CREATE/UPDATE/DELETE` |
| 频道消息与私信 | `AT_MESSAGE_CREATE`、`MESSAGE_CREATE`、`DIRECT_MESSAGE_CREATE` |
| 消息审核 | `MESSAGE_AUDIT_PASS`、`MESSAGE_AUDIT_REJECT` |
| 互动 | `INTERACTION_CREATE` |

事件体模型位于 `love.aira.kohaku.gateway.event.model`：`MessageAuthor`、`MessageScene`、`MessageAttachment`、
`ArkData`、`MsgElement`、`Member`、`MessageEmbed*`、`MessageArk*`、`InteractionResolved`、`AuthorizeData` 等。
事件体与文档不符（字段类型异常）时会回落到原始事件并记 warning，不会打断长连接；
未收录的事件类型可用 `GatewayEventDecoder#payload(JsonNode, Class)` 手动解析。

## 构建与验证

```bash
./mvnw clean test                              # 76 例：核心(事件反序列化/配置/编码/报文/关闭码) + 自动配置 + 假网关端到端 + 示例
./mvnw -DskipTests install                     # 安装本地坐标
./mvnw -pl kohaku-example spring-boot:run      # 用示例机器人真机连网关
```

真机已验证：网关 READY、心跳与 ACK、异常断开后 Resume；单聊与群聊的文本回复、Markdown+按钮、流式消息、
富媒体分片上传并发送、撤回；Spring 消费者（仅声明 starter）注入即连；**纯 Java 消费者**
（classpath 里 0 个 spring jar）同样连上网关。

## 环境要求

Java 21+、Spring Boot 4.x（依赖 Jackson 3 与 Boot 4 的 `spring-boot-starter-jackson`）。
