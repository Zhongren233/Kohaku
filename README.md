# kohaku

QQ 机器人开放平台（QQ Bot）的 **Spring Boot 自动配置**：引入 starter + 两个配置项即可连上网关、
收发 QQ 单聊/群聊消息，并实现消息收发章节的全部服务端接口（16 个）。

## 模块

| 模块 | 说明 |
| --- | --- |
| `kohaku-spring-boot-autoconfigure` | 全部实现：网关长连接、开放平台 REST 客户端、自动配置 |
| `kohaku-spring-boot-starter` | 依赖描述，业务方只引这一个 |
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

## 构建与验证

```bash
./mvnw clean test                              # 61 例：自动配置条件、属性绑定、假网关端到端、报文断言、错误映射
./mvnw -DskipTests install                     # 安装本地坐标
./mvnw -pl kohaku-example spring-boot:run      # 用示例机器人真机连网关
```

真机已验证：网关 READY、心跳与 ACK、异常断开后 Resume；单聊与群聊的文本回复、Markdown+按钮、流式消息、
富媒体分片上传并发送、撤回；以及一个包名无关的消费者项目（仅声明 starter）注入即连。

## 环境要求

Java 21+、Spring Boot 4.x（依赖 Jackson 3 与 Boot 4 的 `spring-boot-starter-jackson`）。
