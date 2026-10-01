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
        messages.sendToUser(event.data().path("author").path("user_openid").stringValue(),
                SendMessageRequest.text("你好"));   // 原始报文路径：手写目标与被动标记
    }
}
```

**回复用 `BotReplies`**（统一入口，推荐）：

```java
replies.text(event, "pong");                                     // 纯文本
replies.markdown(event, "# 标题", keyboard);                      // 带按钮（键盘必须 markdown 承载）
replies.send(event, SendMessageRequest.image(url));               // 自定义请求，仍自动补目标与被动标记
replies.sendToChannel(event, ChannelMessageRequest.text("hi"));   // 频道 / 频道私信
```

自动处理：单聊/群聊的目标 openid、`msg_id + msg_seq`（**自动递增**，避免"相同 msg_id+msg_seq"报错）、
互动事件的**最外层** `event_id`；调用方已指定被动标记时不覆盖；不支持回复的事件直接抛 `IllegalArgumentException`。

**撞限制之前先告警**：按 `msg_id` 记录「首次回复时间 + 已回复次数」，在**用满**（单聊第 4 次 / 群聊第 5 次）、
**超限**、**窗口过期**（单聊 60 分钟 / 群聊 5 分钟 / 频道 5 分钟）时打 WARN——平台只会直接拒绝、
且报错不会告诉你还差几次。告警**不阻断**发送（平台仍是唯一裁定者）。

引入即连接；可直接注入 `QqMessageApi`、`QqMediaApi`、`QqChannelMessageApi`、`QqGatewayClient`、
`QqOpenApiClient`、`AccessTokenProvider`、`BotReplies`。这些 Bean 都带 `@ConditionalOnMissingBean`，可自行覆写。

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
    String idx = event.payload().messageScene().messageIndex();   // msg_idx，平台要求据此去重
    replies.text(event, "你好");        // 目标 openid、msg_id、msg_seq 全自动
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

## 事件处理器链

实现 `BotEventHandler<E>` 并注册为 Bean（或自行构造 `EventDispatcher`），即可按声明顺序处理事件：

- 返回 `HandlerResult.CONSUMED` → **终止**，后续处理器与兜底消费者都不再执行；
- 返回 `HandlerResult.IGNORED` → 继续下一个处理器；
- 处理器抛异常 → 记 error 日志并按 `IGNORED` 继续（不打断长连接与后续处理器）；
- 全部忽略 → 事件落到兜底消费者，即**发布为容器事件**给 `@EventListener`（没有处理器时行为与之前完全一致）。

```java
@Component
@Order(10)      // 顺序也可用 kohaku.qq.handler-order 按 Bean 名称显式声明
public class EchoHandler implements BotEventHandler<C2cMessageCreateEvent> {

    public Class<C2cMessageCreateEvent> eventType() { return C2cMessageCreateEvent.class; }

    public HandlerResult handle(C2cMessageCreateEvent event) {
        replies.text(event, "echo: " + event.payload().content());
        return HandlerResult.CONSUMED;
    }
}
```

```yaml
kohaku:
  qq:
    intents:                 # 按钮回调属于 INTERACTION(1<<26) 意图，必须订阅，
      - PUBLIC_GUILD_MESSAGES  # 否则平台不下发按钮点击事件，客户端只会一直提示「请求超时」
      - GROUP_AND_C2C_EVENT
      - INTERACTION          # 需在开放平台申请该权限；框架检测到按钮回调却未订阅时启动即失败
    handler-order:           # Bean 名称列表，优先于 @Order；未列出的处理器排在其后
      - echoC2cHandler
      - echoGroupHandler
```

纯 Java（无 Spring）：`new EventDispatcher(List.of(handler1, handler2), unconsumed -> …)`，执行顺序即列表顺序。
`kohaku-example` 内含两个可运行示例处理器（`EchoC2cHandler`、`EchoGroupHandler`）。

> ⚠️ **同一 bot 只跑一个实例**：平台会把事件推给每一个在线连接，多实例并存会导致同一条消息被处理多次
> （实测：出现两条重复回复）。本地同时开了 IDE 实例与命令行实例时尤其注意。

## 功能模块与按钮回调（方案 A：自描述 data）

一个**功能**承担「消息入口 + 自己的按钮」，按钮点击按 data 里的命名空间投递回该功能 —— 例如 `/card` 列表翻页：

```java
@Component
public class CardFeature {

    /** 装配成一个功能单元：id + 若干 lambda，连类都不用写（详见 `BotFeature.of`）。 */
    @Bean
    BotFeature card() {                       // 按钮 data 的命名空间 = id
        return BotFeature.of("card")
                .message(C2cMessageCreateEvent.class, event -> entry(event))     // 入口：/card → 第 1 页
                .message(GroupAtMessageCreateEvent.class, event -> entry(event))
                .button(CardKeyboards.ACTION_NEXT, this::step)                  // 翻页：next / prev / page
                .button(CardKeyboards.ACTION_PREV, this::step)
                .button(CardKeyboards.ACTION_PAGE, this::step)
                .build();
    }
}
```

功能主体逻辑长（需要字段、多个私有方法）时，也可以照旧 `implements BotFeature` 手写三个方法：
两种写法产出同一种东西，`InteractionRouter` 一视同仁。装配期就会校验 id 与 action 的合法性（写错启动即失败）。

按钮 data 由 `FeatureKeyboards` 生成，格式 `featureId:action[:k=v;k=v]`（值 URL 编码），如 `card:next:p=2`；
点击后 `data.resolved.button_data` **原样回传**，`InteractionRouter` 解析后投递给 `card` 功能的 `next` 处理器，
因此翻页不会落到别的 handler。

- **状态放在 data 里（方案 A）**：机器人侧无状态、跨重启可用；状态过大时应改用「token + 服务端会话表」。
- 客户端库只提供**协议级**原语：`FeatureKeyboards.button(...)`（回调按钮，data 编码 `featureId:action:状态`）、
  `FeatureKeyboards.commandButton(...)`（指令按钮）、`ButtonData`（路由键编解码）、
  `InteractionRouter`（路由 + 应答）；**分页这类业务形态属于应用层**，见 `kohaku-example` 的
  `CardKeyboards`（翻页键盘）与 `support.Pagination`（页码夹取与切片），可作为你自己功能的模板。
两种按钮写法，按机器人是否开通「互动事件」权限选择：

| 模式 | 写法 | 点击后的链路 | 前置条件 |
| --- | --- | --- | --- |
| **指令按钮**（默认，免权限） | `FeatureKeyboards.commandButton(id, label, "/card next 2")` | 客户端把 data 当**普通消息**发出 → 走消息事件 + 命令解析 | 无 |
| 回调按钮 | `FeatureKeyboards.pagination(...)` / `button(...)`（data = `card:next:p=2`） | 平台下发 `INTERACTION_CREATE(type=11)` → `InteractionRouter` 投递给所属功能 | 需订阅 `INTERACTION(1<<26)` **且已在开放平台开通「互动事件」权限**；未开通时订阅不报错但收不到事件，客户端会一直提示「请求超时」 |

- **键盘只在 markdown 消息上渲染（实测，文档未写）**：`msg_type=0`（纯文本）带 `keyboard` 会被平台静默丢弃，
  所以发送带按钮的消息必须用 `SendMessageRequest.markdown(...)`；`QqMessageApi` 检测到该误用会打 WARN。
- **必须应答互动（实测踩坑）**：收到 `INTERACTION_CREATE` 后要调用 `PUT /interactions/{interaction_id}`
  （`interaction_id` 取事件体 **`d.id`**，**不带** `INTERACTION_CREATE:` 前缀），**否则客户端一直 loading 到超时** ——
  这与「被动回复一条消息」是两件事，缺一不可（回复消息的 `event_id` 则要带前缀的最外层 id）。
  框架默认 `InteractionAckMode.IMMEDIATE`：**命中处理器后先应答 code=0**（客户端立刻结束 loading），
  随后把处理器**丢到独立线程池**执行（`kohaku.qq.handler-threads`，默认 1，保持顺序）——
  耗时逻辑不会顶住网关读循环（心跳与其它事件不受影响）；
  可选 `AFTER_HANDLING` 让 `code` 反映真实结果（成功 0 / 抛异常 1）。未命中处理器时不应答。
- 未命中（未知功能 / 未知动作 / data 非法 / 非按钮互动）→ `IGNORED`，继续走处理链并最终落到 `@EventListener`；
- 回复用统一的 `BotReplies`：`replies.send(ctx.interaction(), request)` —— 自动按场景选单聊/群聊、
  自动用**最外层**事件 id 作 `event_id` 被动回复。
- 平台约束：单聊/群聊**没有编辑消息接口** → 每次翻页是发一条新消息（旧键盘随旧消息失效）；
  被动回复时效 单聊 60 分钟 / 群聊 5 分钟。

`kohaku-example` 内含完整可跑的 `/card`（12 条卡片、每页 5 条、支持上一页/下一页/页码）。

## 新增一个功能 / 指令（清单）

**① 只要命令、不要按钮** —— 直接实现 `BotEventHandler` 并注册为 Bean（无需 `BotFeature`）：

```java
@Component
@Order(50)                                   // 与其它处理器的先后；也可用 kohaku.qq.handler-order 按 Bean 名声明
public class PingHandler implements BotEventHandler<C2cMessageCreateEvent> {
    public Class<C2cMessageCreateEvent> eventType() { return C2cMessageCreateEvent.class; }
    public HandlerResult handle(C2cMessageCreateEvent event) {
        if (!"/ping".equals(event.payload().content())) {
            return HandlerResult.IGNORED;     // 不是自己的命令一定要忽略，交给后面的处理器
        }
        replies.text(event, "pong");      // 目标与被动标记由 BotReplies 解析（含 msg_seq 自增）
        return HandlerResult.CONSUMED;
    }
}
```

**② 命令 + 按钮** —— 用 `BotFeature.of(id)` 函数式装配（`id` 即按钮 data 的命名空间）：

```java
@Component
public class VoteFeature {

    @Bean
    BotFeature vote(BotReplies replies) {
        return BotFeature.of("vote")                                        // 命名空间，仅字母数字与 _ . -
                .message(C2cMessageCreateEvent.class, event -> voteEntry(event, replies))   // /vote 入口
                .button("yes", context -> {                                  // 按钮动作：与 data 的 action 段一致
                    replies.send(context.interaction(), SendMessageRequest.markdown("已赞成 ✅"));
                    return HandlerResult.CONSUMED;
                })
                .button("no", context -> { ... })
                .build();
    }
}
// 发按钮：markdown 消息 + FeatureKeyboards.button("yes", "vote", "yes", "赞成", Map.of("q", "1"))
```
> 单独一个处理器不想包成功能时，也可用 `BotEventHandler.of(类型, lambda)` —— 不用再写 `eventType()`。

清单与注意点：

1. **id / action 在装配期校验**（只允许字母数字与 `_ . -`，见 `ButtonData`），写错是启动失败而不是首次点击才失败。
2. **按钮必须用 markdown 消息承载**（`SendMessageRequest.markdown(...)`），纯文本带键盘会被平台丢弃。
3. **回调按钮需要 `INTERACTION` 意图 + 平台开通「互动事件」权限**；没有权限时可用
   `FeatureKeyboards.commandButton(id, label, "/vote yes 1")` 走指令按钮（点完以普通消息回到你的命令入口）。
4. **框架自动应答互动**（`PUT /interactions/{id}`，默认先应答再异步处理），业务不用管；处理器耗时再久也不会顶住网关读循环。
5. **状态自描述在按钮 data 里**（`Map<String,String>` → `ButtonData` 编码），机器人侧无状态；状态过大再考虑 token + 会话表。
6. 未命中自己的输入一律 `IGNORED`；全部忽略的事件最终落到 `@EventListener`（不影响既有监听）。
7. 队列顺序：`kohaku.qq.handler-order`（Bean 名，优先）→ `@Order` → 注册顺序；功能自带的 `messageHandlers()` 排在所有 Bean 之后。
8. 测试：直接调处理器（参考 `CardFeatureTest`：构造强类型事件 → 断言发出的 `SendMessageRequest`），或用 Mockito 断言 API 调用。

## 构建与验证

```bash
./mvnw clean test                              # 115 例：核心(事件反序列化/配置/编码/报文/关闭码) + 自动配置 + 假网关端到端 + 示例
./mvnw -DskipTests install                     # 安装本地坐标
./mvnw -pl kohaku-example spring-boot:run      # 用示例机器人真机连网关
```

真机已验证：网关 READY、心跳与 ACK、异常断开后 Resume；单聊与群聊的文本回复、Markdown+按钮、流式消息、
富媒体分片上传并发送、撤回；Spring 消费者（仅声明 starter）注入即连；**纯 Java 消费者**
（classpath 里 0 个 spring jar）同样连上网关。

## 环境要求

Java 21+、Spring Boot 4.x（依赖 Jackson 3 与 Boot 4 的 `spring-boot-starter-jackson`）。
