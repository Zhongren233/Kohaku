# Kohaku

QQ 机器人开放平台的 Java 客户端 + Spring Boot 自动配置：引 starter + 两行配置即可连上网关，
收发单聊/群聊/频道消息并处理按钮回调。

| 模块 | 说明 |
| --- | --- |
| `kohaku-client` | 纯 Java 核心：网关长连接、OpenAPI 客户端、事件模型。只依赖 `jackson-databind` + `slf4j-api` |
| `kohaku-spring-boot-autoconfigure` | Spring 装配：属性绑定、生命周期、事件转发 |
| `kohaku-spring-boot-starter` | 业务方只引这一个 |
| `kohaku-example` | 可运行示例（私有凭据放 gitignore 的 `secrets/qq-bot.yaml`） |

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

## 回复：`BotReplies`

```java
replies.text(event, "pong");                                     // 纯文本
replies.markdown(event, "# 标题", keyboard);                      // 带按钮（键盘必须 markdown 承载）
replies.send(event, SendMessageRequest.image(url));               // 自定义请求
replies.sendToChannel(event, ChannelMessageRequest.text("hi"));   // 频道 / 频道私信
```

自动完成：目标选择（单聊 `author.user_openid`、群聊 `group_openid`、频道 `channel_id`、私信 `guild_id`）、
被动标记（消息用 `msg_id + 自动递增 msg_seq`，互动用**最外层** `event_id`）；调用方已指定被动标记时不覆盖；
不支持回复的事件抛 `IllegalArgumentException`。撞平台限制前打 WARN（单聊 4 次/60 分钟、群聊 5 次/5 分钟、
频道 5 分钟）但不阻断发送。

## 配置项

| 键 | 默认值 | 说明 |
| --- | --- | --- |
| `kohaku.qq.app-id` / `app-secret` | 无（必填） | 机器人凭据 |
| `kohaku.qq.intents` | `[PUBLIC_GUILD_MESSAGES]` | 多选枚举 `QqIntent`，位掩码自动合成 |
| `kohaku.qq.api-base-url` | `https://api.bot.qq.com` | 开放平台地址（文档「统一请求地址」） |
| `kohaku.qq.token-url` | `https://api.bot.qq.com/app/getAppAccessToken` | AccessToken 接口 |
| `kohaku.qq.sandbox` | `false` | 为真时强制用沙箱地址 |
| `kohaku.qq.enabled` | `true` | 置 false 整体关闭（含凭据校验） |
| `kohaku.qq.auto-start` | `true` | 是否随容器连接/断开网关 |
| `kohaku.qq.handler-order` | 空 | 处理器顺序（Bean 名列表，优先于 `@Order`） |
| `kohaku.qq.handler-threads` | `1` | 互动处理器线程数；1 = 保序 |
| `kohaku.qq.shard-index` / `shard-total` | `0` / `1` | 分片参数 |
| `kohaku.qq.client-name` | `kohaku` | 上报的 `$browser`/`$device` |
| `kohaku.qq.reconnect-initial-delay` / `reconnect-max-delay` | `1s` / `60s` | 重连退避区间 |

IDE 补全由 `META-INF/spring-configuration-metadata.json` 提供。

## 事件与处理器链

官方事件反序列化为强类型（`C2cMessageCreateEvent` 等，继承 `BotDispatchEvent`，原始报文仍可用 `data()`）；
未收录的类型回落为 `BotDispatchEvent`，字段异常时记 WARN、不打断长连接。覆盖单聊/群聊消息、好友与群生命周期、
频道/子频道、频道消息与私信、消息审核、互动共 23 个类型（见 `love.aira.kohaku.gateway.event`）。

实现 `BotEventHandler<E>` 并注册为 Bean，按序处理：`CONSUMED` 终止、`IGNORED` 继续、抛异常记 error 后继续；
全部忽略的事件发布为容器事件，交给 `@EventListener`。

```java
@EventListener
void onMessage(C2cMessageCreateEvent event) {
    String idx = event.payload().messageScene().messageIndex();   // msg_idx，平台要求据此去重
    replies.text(event, "你好");
}
```

```java
@Component
@Order(10)   // 顺序：kohaku.qq.handler-order（Bean 名）→ @Order → 注册顺序
public class EchoHandler implements BotEventHandler<C2cMessageCreateEvent> {
    public Class<C2cMessageCreateEvent> eventType() { return C2cMessageCreateEvent.class; }
    public HandlerResult handle(C2cMessageCreateEvent event) {
        replies.text(event, "echo: " + event.payload().content());
        return HandlerResult.CONSUMED;
    }
}
```

> **同一 bot 只跑一个实例**：平台把事件推给每个在线连接，多实例会让同一条消息被处理多次。

## 功能与按钮

常用形态（命令进入 + 按钮翻页/选择）有骨架，业务只写两个纯函数：

```java
@Bean
BotFeature card(BotReplies replies) {
    return ButtonFeature.of("card")        // 默认入口命令 /card；.commands(...) 可换前缀
            .state(CardFeature::state)     // 文本 → 状态；null = 不是我的命令（IGNORED）
            .render(CardFeature::page)     // 状态 → 消息：入口与按钮共用
            .build(replies);               // 默认动作 next/prev/page；.actions(...) 可换
}
```

状态即按钮 data 的 state 段（如 `card:next;p=2`），于是「点下一页」与「发 `/card next 2`」走同一条渲染路径，
无会话、幂等。需要自定义入口语义时用 `BotFeature.of(id).message(...).button(...)`，也可照旧 `implements BotFeature`。

| 按钮形态 | 构造 | 点击链路 | 前置条件 |
| --- | --- | --- | --- |
| 指令按钮 | `FeatureKeyboards.commandButton(id, label, "/card next 2")` | data 作为普通消息发出 → 回到命令入口 | 无 |
| 回调按钮 | `FeatureKeyboards.button(id, featureId, action, label, state)` | 平台下发 `INTERACTION_CREATE(type=11)` → `InteractionRouter` 按 data 的命名空间投递给所属功能 | `INTERACTION` 意图**且**平台已开通「互动事件」权限 |

实测坑（文档未写）：

1. **键盘只在 markdown 消息上渲染**：`msg_type=0` 带 `keyboard` 会被平台静默丢弃（库会打 WARN）。
2. **必须应答互动**：`PUT /interactions/{d.id}`（**不带** `INTERACTION_CREATE:` 前缀），否则客户端 loading 到超时；
   它与「被动回复一条消息」是两件事，后者的 `event_id` 反而要带前缀。框架默认**先应答再异步执行**处理器
   （`handler-threads` 控制并发），可用 `AFTER_HANDLING` 让应答码反映真实结果（成功 0 / 异常 1）。
3. 单聊/群聊**没有编辑消息接口**：翻页是发新消息，旧键盘随旧消息失效。

库只提供协议级原语（`ButtonData` 编解码、`FeatureKeyboards`、`InteractionRouter`）；分页这类业务形态在示例里
（`CardKeyboards` + `support.Pagination`），可整包复制成你自己的功能模板。

## 新增一个功能 / 指令

- **只要命令**：实现 `BotEventHandler` 注册为 Bean（或 `BotEventHandler.of(类型, lambda)`），不是自己的输入返回 `IGNORED`。
- **带按钮**：优先 `ButtonFeature` 骨架；入口语义特殊时用 `BotFeature.of(id)`。
- 要点：id/action 在装配期校验（字母数字与 `_ . -`）；按钮必须 markdown 承载；回调按钮需订阅意图 + 平台权限；
  互动由框架自动应答；未命中一律 `IGNORED`；测试直接调处理器、断言发出的 `SendMessageRequest`
  （参考 `CardFeatureTest`、`ButtonFeatureTest`）。

## 纯 Java（非 Spring）

只引 `kohaku-client`：

```java
KohakuConfig config = KohakuConfig.of(appId, appSecret, List.of(QqIntent.GROUP_AND_C2C_EVENT));
JsonMapper mapper = new JsonMapper();
HttpClient http = HttpClient.newHttpClient();
var tokens = new AccessTokenProvider(http, mapper, config);
var api = new QqOpenApiClient(http, mapper, config, tokens);
var client = new QqGatewayClient(config, new QqGatewayApi(api, config), tokens, mapper, event -> { ... });
client.start();   // 心跳、重连、Resume 全自动
```

消息接口同样直接可用：`new QqMessageApi(api)`、`new QqMediaApi(api, http)`、`new QqChannelMessageApi(api)`。

## 构建与验证

```bash
./mvnw clean test                              # 159 例
./mvnw -DskipTests install                     # 安装本地坐标
./mvnw -pl kohaku-example spring-boot:run      # 用示例机器人真机连网关
```

真机已验证：网关 READY、心跳与 ACK、断线 Resume、关闭码策略；单聊/群聊文本与 markdown+按钮、流式消息、
富媒体分片上传与撤回；互动事件应答与按钮路由；Spring 与纯 Java（classpath 无 Spring）两种宿主。

## 环境要求

Java 21+、Spring Boot 4.x（Jackson 3）。
