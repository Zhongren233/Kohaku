# Kohaku

_✨ QQ 机器人开放平台的 Java 客户端 + Spring Boot Starter：引入依赖 + 两行配置即可连上网关 ✨_

> 要求 `JDK 21+` 与 `Spring Boot 4.0+`（Jackson 3）

# QuickStart

## 依赖引入

### Maven

```xml
<dependency>
  <groupId>love.aira</groupId>
  <artifactId>kohaku-spring-boot-starter</artifactId>
  <version>0.2.0</version>
</dependency>
```

### Gradle Kotlin DSL

```kotlin
implementation("love.aira:kohaku-spring-boot-starter:0.2.0")
```

### Gradle Groovy DSL

```groovy
implementation 'love.aira:kohaku-spring-boot-starter:0.2.0'
```

## 配置文件

```yaml
kohaku:
  qq:
    app-id: ${QQBOT_APP_ID}        # 也可用 KOHAKU_QQ_APPID / KOHAKU_QQ_APPSECRET 环境变量
    app-secret: ${QQBOT_APP_SECRET}
    intents:
      - PUBLIC_GUILD_MESSAGES      # 频道内 @机器人（基础权限）
      - GROUP_AND_C2C_EVENT        # 群/单聊事件（需在开放平台申请）
      - INTERACTION                # 按钮回调（需申请；有按钮回调却没订阅时启动即失败）
```

引入即连接。`BotReplies`、`QqMessageApi`、`QqMediaApi`、`QqChannelMessageApi`、`QqGatewayClient` 等 Bean
可直接注入，也可声明同类型 Bean 覆写（`kohaku.qq.enabled=false` 整体关闭）。

## Feature 开发流程

Feature = 「消息入口 + 自己的按钮回调」的可路由单元：按钮 data 以功能 id 作命名空间
（`featureId:action:k=v;…`），点击后由框架投回产生该按钮的功能。状态直接编码在按钮 data 里，
机器人侧无需保存会话，重复点击天然幂等。

### 示例一：最小完整 Feature（/ping）

`/ping` 回复一条带按钮的 markdown，按钮点击与发命令共用同一条渲染路径：

```java
package com.example.bot;

import java.util.Map;
import love.aira.kohaku.api.model.Keyboard;
import love.aira.kohaku.feature.BotFeature;
import love.aira.kohaku.interaction.FeatureKeyboards;
import love.aira.kohaku.gateway.event.BotEvent;
import love.aira.kohaku.gateway.event.C2cMessageCreateEvent;
import love.aira.kohaku.gateway.event.GroupAtMessageCreateEvent;
import love.aira.kohaku.gateway.event.GroupMessageCreateEvent;
import love.aira.kohaku.gateway.event.model.GroupMessage;
import love.aira.kohaku.gateway.handler.HandlerResult;
import love.aira.kohaku.reply.BotReplies;
import love.aira.kohaku.support.CommandArgs;
import love.aira.kohaku.support.Mentions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 最小 Feature：/ping 回复一条带按钮的 markdown，点按钮与发命令共用同一条渲染路径。 */
@Configuration
public class PingFeature {

    static final String ID = "ping";
    static final String ACTION_PING = "ping";
    static final String COMMAND = "/ping";

    @Bean
    BotFeature ping(BotReplies replies) {
        return BotFeature.of(ID)
                .message(C2cMessageCreateEvent.class, event -> onMessage(event, c2cText(event), replies))
                .message(GroupAtMessageCreateEvent.class, event -> onMessage(event, groupText(event.payload()), replies))
                .message(GroupMessageCreateEvent.class, event -> onMessage(event, groupText(event.payload()), replies))
                .button(ACTION_PING, context -> {
                    // 按钮点击：交互事件同样能被 BotReplies 被动回复（按 chat_type 自动选单聊/群聊）
                    replies.markdown(context.interaction(), render(), keyboard());
                    return HandlerResult.CONSUMED;
                })
                .build();
    }

    /** 命令入口：命令词必须完全相等，否则 IGNORED，把消息让给链上其它处理器。 */
    private HandlerResult onMessage(BotEvent event, String content, BotReplies replies) {
        CommandArgs args = CommandArgs.parse(content);
        if (args == null || !COMMAND.equals(args.command())) {
            return HandlerResult.IGNORED;
        }
        replies.markdown(event, render(), keyboard());
        return HandlerResult.CONSUMED;
    }

    private String render() {
        return "**pong**";
    }

    /** 回调按钮：data = ping:ping；点击后由 InteractionRouter 投回本功能的 ping 动作。 */
    private Keyboard keyboard() {
        return Keyboard.of(Keyboard.Row.of(
                FeatureKeyboards.button("ping", ID, ACTION_PING, "再 ping 一次", Map.of())));
    }

    private static String c2cText(C2cMessageCreateEvent event) {
        return event.payload() == null ? null : event.payload().content();
    }

    /** 群全量消息的 content 保留行首 {@code <@机器人id>}，匹配命令词前先剥掉（群 @ 消息平台已剥好）。 */
    private static String groupText(GroupMessage message) {
        return message == null ? null : Mentions.stripLeading(message.content(), message.mentions());
    }
}
```

要点：

- 声明为 `@Bean` 即完成注册；执行顺序按 `kohaku.qq.handler-order` 的 Bean 名称，其余按 `@Order`，
  各 Feature 的消息入口排在链尾。
- 不属于自己的输入必须返回 `HandlerResult.IGNORED`（继续走链），命中返回 `CONSUMED`（终止链内后续处理器）。
- 按钮用 `FeatureKeyboards.button(buttonId, featureId, action, label, state)` 生成（回调按钮，需 `INTERACTION` 意图）；
  未开通互动权限时改用 `FeatureKeyboards.commandButton(buttonId, label, command)`，点击即发一条普通消息，只依赖消息事件。
- 键盘只在 markdown 消息上渲染，发送带按钮的消息用 `replies.markdown(...)`。

### 示例二：命令 + 状态驱动按钮（/page，ButtonCommands）

分页、菜单、列表这类「命令进入 + 状态驱动按钮」的功能，只需提供「命令参数 → 状态」与「上下文 → 消息」
两个纯函数，入口（单聊 / 群内 @机器人 / 群内全量消息）、命令匹配、按钮回调、被动回复都由框架补齐：

```java
package com.example.bot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import love.aira.kohaku.api.model.Keyboard;
import love.aira.kohaku.api.model.SendMessageRequest;
import love.aira.kohaku.feature.BotFeature;
import love.aira.kohaku.feature.ButtonCommands;
import love.aira.kohaku.feature.FeatureContext;
import love.aira.kohaku.interaction.FeatureKeyboards;
import love.aira.kohaku.reply.BotReplies;
import love.aira.kohaku.support.CommandArgs;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 最小 Feature：/page [页] 显示一页内容，按钮翻页与命令走同一条渲染路径。 */
@Configuration
public class PageFeature {

    private static final String ID = "page";
    private static final String COMMAND = "/page";
    /** 按钮 data 里表示页码的状态键。 */
    private static final String STATE_PAGE = "p";
    private static final int PAGE_SIZE = 3;
    private static final List<String> ITEMS = List.of("A1", "A2", "A3", "A4", "A5", "A6", "A7");

    @Bean
    BotFeature page(BotReplies replies) {
        return ButtonCommands.of(ID)
                .commands(COMMAND)          // 只认 /page，其余文本 IGNORED
                .state(PageFeature::state)  // 命令参数 → 状态
                .render(PageFeature::render) // 上下文 → 消息（入口与按钮共用）
                .build(replies);            // 默认按钮动作 next/prev/page
    }

    /** 命令参数 → 状态（页码）；参数不成立时返回 {@code null}，框架按 IGNORED 处理。 */
    static Map<String, String> state(CommandArgs args) {
        int page = args.isEmpty() ? 1 : args.intAt(0, 0);
        return page < 1 ? null : Map.of(STATE_PAGE, Integer.toString(page));
    }

    /** 上下文 → 消息（入口与按钮共用）；页码越界时收敛到有效范围。 */
    static SendMessageRequest render(FeatureContext context) {
        int page = clamp(context.intState(STATE_PAGE, 1));
        int start = (page - 1) * PAGE_SIZE;
        StringBuilder markdown = new StringBuilder("**第 ").append(page).append(" 页**");
        for (String item : ITEMS.subList(start, Math.min(start + PAGE_SIZE, ITEMS.size()))) {
            markdown.append("\n- ").append(item);
        }
        // 触发者：单聊下发 user_openid、群聊下发 group_member_openid，同一个用户是同一个值，
        // 所以统一读 userOpenid()，不必按场景分支（触发事件本身是 context.event()）
        markdown.append("\n\n> 请求来自 ").append(context.userOpenid());
        return SendMessageRequest.markdown(markdown.toString()).withKeyboard(keyboard(page));
    }

    /** 回调按钮：data 形如 {@code page:next:p=2}，点击后投回本功能的 next 动作并携带目标页码。 */
    private static Keyboard keyboard(int page) {
        List<Keyboard.Button> buttons = new ArrayList<>();
        if (page > 1) {
            buttons.add(FeatureKeyboards.button("prev", ID, "prev", "上一页", pageState(page - 1)));
        }
        buttons.add(FeatureKeyboards.button("page", ID, "page", page + "/" + pages(), pageState(page)));
        if (page < pages()) {
            buttons.add(FeatureKeyboards.button("next", ID, "next", "下一页", pageState(page + 1)));
        }
        return Keyboard.of(Keyboard.Row.of(buttons.toArray(Keyboard.Button[]::new)));
    }

    private static Map<String, String> pageState(int page) {
        return Map.of(STATE_PAGE, Integer.toString(page));
    }

    private static int pages() {
        return (ITEMS.size() + PAGE_SIZE - 1) / PAGE_SIZE;
    }

    private static int clamp(int page) {
        return Math.min(Math.max(page, 1), pages());
    }
}
```

框架还会做这些事：不匹配的命令一律 `IGNORED`；按钮回调从 data 里取状态再 `render`，
与发命令走同一条渲染路径；回复经 `BotReplies` 自动补 `msg_id`/`msg_seq` 或互动 `event_id`。
`render` 收到的是 `FeatureContext`：`state()` 是按钮状态，`event()`/`userOpenid()`/`groupOpenid()` 是触发事件与身份。

功能有状态、要复用字段与方法时，直接实现 `BotFeature` 接口（见示例 `TodoFeature`：它用 `FeatureContext.userOpenid()` 把待办按触发者隔离 —— 同一个人在群里与单聊共享一份列表，不同人互不可见，别人的按钮点不动我的数据）；
只观察事件（日志、落库）用 `@EventListener` 监听 `BotEvent`，与处理链互不影响。

完整示例见 [`kohaku-example`](kohaku-example)：`card` 分页卡片、`todo` 子命令 + 按钮、`EventLogger` 观察者。

### 获取发送者 / 群标识

触发者只有一个标识：单聊下发 `user_openid`、群聊下发 `group_member_openid`，**同一个用户是同一个值**，
所以统一读 `userOpenid()`，不要按场景分支；群标识 `groupOpenid()` 只在群聊场景非空。
消息入口从事件体取；按钮回调不用再挖 payload —— 上下文 `FeatureContext` 已解析好：

| 事件 / 上下文 | 发送者 | 会话标识 |
| --- | --- | --- |
| 单聊 `C2cMessageCreateEvent` | `payload().author().userOpenid()`（`user_openid`） | —— |
| 群 `GroupAtMessageCreateEvent`、`GroupMessageCreateEvent` | `payload().author().memberOpenid()`（`group_member_openid`，与单聊的 `user_openid` 同值；角色见 `memberRole()`） | `payload().groupOpenid()` |
| 频道 `AtMessageCreateEvent`、`MessageCreateEvent` | `payload().author().id()` | `payload().guildId()`、`payload().channelId()` |
| 频道私信 `DirectMessageCreateEvent` | `payload().author().id()` | `payload().guildId()` |
| 按钮回调 `ButtonHandler` 的 `FeatureContext` | `userOpenid()`（各场景都有值） | `groupOpenid()`（仅群聊非空），按钮信息见 `button()`（`featureId()`/`action()`/`buttonId()`），场景看 `scene()`、`chatType()`、`isC2c()/isGroup()/isGuild()` |
| `ButtonCommands` 的 `render`（同一个 `FeatureContext`） | `userOpenid()`（入口路径取自消息事件，按钮路径取自互动事件，同一个值） | `groupOpenid()`（仅群聊非空），触发事件是 `event()`、被动回复用 `eventId()` |

```java
// 消息入口：群消息
GroupMessage message = event.payload();
String groupOpenid = message.groupOpenid();       // 群
String sender = message.author().memberOpenid();  // 触发者（群聊下的字段名；单聊是 user_openid，同值）
String role = message.author().memberRole();      // member / admin / owner

// 按钮回调：context 已带触发者与群，userOpenid() 各场景都有值
.button("ban", context -> {
    String sender = context.userOpenid();      // 单聊/群聊都是这一个标识，不必分支
    String group = context.groupOpenid();      // 仅群聊非空
    return HandlerResult.CONSUMED;
})
```

好友 / 群生命周期事件（`FriendAddEvent`、`GroupAddRobotEvent` 等）的标识在各 payload 上：
`FriendAdd.openid()`、`GroupAddRobot.groupOpenid()` 与 `opMemberOpenid()`、`C2cMsgReceive.openid()`、
`GroupMsgReceive.groupOpenid()` 与 `opMemberOpenid()`。

用 `ButtonCommands` 骨架时无需手写入口：`render` 收到的 `FeatureContext` 里 `event()` 就是触发事件
（入口路径是那条消息事件，按钮路径是本次互动事件），身份字段已解析好 —— 示例二的 `/page` 就把触发者写进了卡片。

只为被动回复时不必自己取目标：`BotReplies` 会按事件类型自动解析（目标、`msg_id`/`msg_seq`、互动 `event_id`）。

## 代码结构

| 包 | 职责 |
| --- | --- |
| `love.aira.kohaku.api`（+ `.model`） | 上行 REST：AccessToken、消息/富媒体/频道/互动接口与请求响应 DTO |
| `love.aira.kohaku.gateway`（+ `.event`、`.event.model`、`.handler`） | 下行长连接：连接时序/心跳/退避、事件类型与事件体、处理器接口与派发器 |
| `love.aira.kohaku.feature` | 扩展点：`BotFeature`、`ButtonHandler`、统一上下文 `FeatureContext`、命令骨架 `ButtonCommands` |
| `love.aira.kohaku.interaction` | 互动协议与路由：按钮 data 编解码、键盘构造、`InteractionRouter` 与应答策略 |
| `love.aira.kohaku.reply` | 被动回复：目标解析、`msg_id`/`msg_seq`/`event_id` 自动补齐、限额告警 |
| `love.aira.kohaku.support` / `.internal` / `.config` | 命令分词与提及剥离 / 内部实现细节（不属于公开 API） / 框架无关的运行时配置 |
| `love.aira.kohaku.autoconfigure` | Spring 装配入口 `KohakuAutoConfiguration`（HTTP / 功能 / 派发 / 网关四块） |

### 本地运行

```bash
./mvnw -DskipTests install
cd kohaku-example
# 凭据放 secrets/qq-bot.yaml（已 gitignore），或设 KOHAKU_QQ_APPID / KOHAKU_QQ_APPSECRET
../mvnw spring-boot:run
```
