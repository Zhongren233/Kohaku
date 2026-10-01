# Kohaku

QQ 机器人开放平台的 Java 客户端 + Spring Boot 自动配置：引 starter + 两行配置即可连上网关，
收发单聊/群聊/频道消息并处理按钮回调。

| 模块 | 说明 |
| --- | --- |
| `kohaku-client` | 纯 Java 核心：网关长连接、OpenAPI 客户端、事件模型。只依赖 `jackson-databind` + `slf4j-api` |
| `kohaku-spring-boot-autoconfigure` | Spring 装配：属性绑定、生命周期、事件转发 |
| `kohaku-spring-boot-starter` | 业务方只引这一个 |
| `kohaku-example` | 可运行示例 |

## 环境要求

Java 21+、Spring Boot 4.x（Jackson 3）。

## 快速开始

```bash
./mvnw -DskipTests install
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
    app-id: ${QQBOT_APP_ID}        # 也可用 KOHAKU_QQ_APPID / KOHAKU_QQ_APPSECRET 环境变量
    app-secret: ${QQBOT_APP_SECRET}
    intents:
      - PUBLIC_GUILD_MESSAGES      # 频道内 @机器人（基础权限）
      - GROUP_AND_C2C_EVENT        # 群/单聊事件（需在开放平台申请）
      - INTERACTION                # 按钮回调（需申请；检测到按钮回调却没订阅时启动即失败）
```

引入即连接。`QqMessageApi`、`QqMediaApi`、`QqChannelMessageApi`、`QqGatewayClient`、`QqOpenApiClient`、
`AccessTokenProvider`、`BotReplies` 均可直接注入（都带 `@ConditionalOnMissingBean`，可自行覆写）。
