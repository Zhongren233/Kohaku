package love.aira.kohaku.interaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import love.aira.kohaku.api.model.Keyboard;
import love.aira.kohaku.gateway.event.BotEvent;
import love.aira.kohaku.feature.FeatureContext;
import love.aira.kohaku.feature.ButtonHandler;
import love.aira.kohaku.feature.BotFeature;
import love.aira.kohaku.gateway.event.InteractionCreateEvent;
import love.aira.kohaku.gateway.event.model.InteractionCreate;
import love.aira.kohaku.gateway.event.model.InteractionData;
import love.aira.kohaku.gateway.event.model.InteractionResolved;
import love.aira.kohaku.gateway.handler.EventDispatcher;
import love.aira.kohaku.gateway.handler.HandlerResult;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** 按钮点击按功能命名空间路由：命中投递、未命中回落、以及键盘生成的 data 与路由能对上。 */
class InteractionRouterTest {

    private final CardFeature card = new CardFeature();
    private final InteractionRouter router = new InteractionRouter(List.of(card));

    @Test
    void routesClickToOwningFeature() {
        HandlerResult result = router.handle(click("card:next:p=2", 11, 2, "USER_OPENID", null));

        assertThat(result).isEqualTo(HandlerResult.CONSUMED);
        assertThat(card.handled).hasSize(1);
        FeatureContext context = card.handled.getFirst();
        assertThat(context.button().featureId()).isEqualTo("card");
        assertThat(context.button().action()).isEqualTo("next");
        assertThat(context.intState("p", 1)).isEqualTo(2);
        assertThat(context.eventId()).isEqualTo("INTERACTION_CREATE:EVENT_ID");   // 用最外层事件 id
        assertThat(context.button().buttonId()).isEqualTo("btn-1");
        assertThat(context.isC2c()).isTrue();
        assertThat(context.userOpenid()).isEqualTo("USER_OPENID");
    }

    @Test
    void routesGroupSceneClick() {
        // 群聊下发 group_member_openid，单聊下发 user_openid —— 同一个用户是同一个值，
        // 因此统一由 userOpenid() 读出，业务侧不必按场景分支
        router.handle(click("card:next:p=3", 11, 1, null, "GROUP_OPENID", "PERSON_OPENID"));

        FeatureContext context = card.handled.getFirst();
        assertThat(context.isGroup()).isTrue();
        assertThat(context.groupOpenid()).isEqualTo("GROUP_OPENID");
        assertThat(context.userOpenid()).isEqualTo("PERSON_OPENID");
    }

    @Test
    void ignoresUnknownFeatureOrAction() {
        assertThat(router.handle(click("unknown:next:p=2", 11, 2, "USER", null))).isEqualTo(HandlerResult.IGNORED);
        assertThat(router.handle(click("card:missing:p=2", 11, 2, "USER", null))).isEqualTo(HandlerResult.IGNORED);
        assertThat(router.handle(click("garbage-data", 11, 2, "USER", null))).isEqualTo(HandlerResult.IGNORED);
        assertThat(card.handled).isEmpty();
    }

    @Test
    void ignoresNonButtonInteractionTypes() {
        assertThat(router.handle(click("card:next:p=2", 13, 2, "USER", null))).isEqualTo(HandlerResult.IGNORED);
        assertThat(card.handled).isEmpty();
    }

    @Test
    void propagatesIgnoredResultFromButtonHandler() {
        card.result = HandlerResult.IGNORED;

        assertThat(router.handle(click("card:next:p=2", 11, 2, "USER", null))).isEqualTo(HandlerResult.IGNORED);
    }

    @Test
    void generatedKeyboardDataRoutesBackToTheSameHandler() {
        Keyboard.Button next = FeatureKeyboards.button("next", "card", "next", "下一页", Map.of("p", "2"));

        assertThat(next.action().data()).isEqualTo("card:next:p=2");
        assertThat(next.renderData().label()).isEqualTo("下一页");
        // 直接拿生成的 data 去路由，应命中同一个功能（编码与路由保持闭环）
        assertThat(router.handle(click(next.action().data(), 11, 2, "USER", null)))
                .isEqualTo(HandlerResult.CONSUMED);
        assertThat(card.handled.getFirst().intState("p", 1)).isEqualTo(2);
    }

    @Test
    void rejectedDuplicateFeatureIdOrAction() {
        assertThatThrownBy(() -> new InteractionRouter(List.of(new CardFeature(), new CardFeature())))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("功能 id 重复");

        BotFeature duplicatedAction = new BotFeature() {
            @Override
            public String id() {
                return "dup";
            }

            @Override
            public List<ButtonHandler> buttonHandlers() {
                return List.of(actionHandler("next"), actionHandler("next"));
            }
        };
        assertThatThrownBy(() -> new InteractionRouter(List.of(duplicatedAction)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("按钮动作重复");
    }

    @Test
    void immediateModeAcksBeforeHandling() {
        List<String> order = new CopyOnWriteArrayList<>();
        card.onHandle = () -> order.add("handle");
        InteractionRouter acking = new InteractionRouter(List.of(card),
                (id, code) -> order.add("ack:" + id + ":" + code));

        assertThat(acking.handle(click("card:next:p=2", 11, 2, "USER", null))).isEqualTo(HandlerResult.CONSUMED);

        // 先通知平台（客户端立刻结束 loading），再执行处理器；应答用 d.id，不带 INTERACTION_CREATE: 前缀
        assertThat(order).containsExactly("ack:EVENT_ID:0", "handle");
    }

    @Test
    void immediateModeAcksOnlyOnceEvenIfHandlingFails() {
        List<String> acks = new CopyOnWriteArrayList<>();
        InteractionRouter acking = new InteractionRouter(List.of(card), (id, code) -> acks.add(id + ":" + code));

        card.failure = new IllegalStateException("boom");
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> acking.handle(click("card:next:p=2", 11, 2, "USER", null)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(acks).containsExactly("EVENT_ID:0");   // 同一 interaction_id 只能应答一次
    }

    @Test
    void afterHandlingModeReflectsResultInCode() {
        List<String> acks = new CopyOnWriteArrayList<>();
        InteractionRouter acking = new InteractionRouter(List.of(card), (id, code) -> acks.add(id + ":" + code),
                InteractionAckMode.AFTER_HANDLING);

        assertThat(acking.handle(click("card:next:p=2", 11, 2, "USER", null))).isEqualTo(HandlerResult.CONSUMED);
        assertThat(acks).containsExactly("EVENT_ID:0");

        card.result = HandlerResult.IGNORED;                       // 交给后续处理器 → 不应答
        assertThat(acking.handle(click("card:next:p=2", 11, 2, "USER", null))).isEqualTo(HandlerResult.IGNORED);
        assertThat(acks).containsExactly("EVENT_ID:0");

        card.result = HandlerResult.CONSUMED;
        card.failure = new IllegalStateException("boom");           // 处理失败 → 应答 1
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> acking.handle(click("card:next:p=2", 11, 2, "USER", null)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(acks).containsExactly("EVENT_ID:0", "EVENT_ID:1");
    }

    @Test
    void immediateModeWithExecutorAcksThenHandlesOffThread() throws Exception {
        List<String> order = new CopyOnWriteArrayList<>();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            card.onHandle = () -> {
                started.countDown();
                try {
                    release.await(5, java.util.concurrent.TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                order.add("handled");
            };
            InteractionRouter acking = new InteractionRouter(List.of(card),
                    (id, code) -> order.add("ack:" + id + ":" + code), InteractionAckMode.IMMEDIATE, executor);

            // 调用方立刻返回，不等耗时逻辑；应答已在返回前完成
            assertThat(acking.handle(click("card:next:p=2", 11, 2, "USER", null)))
                    .isEqualTo(HandlerResult.CONSUMED);
            assertThat(order).containsExactly("ack:EVENT_ID:0");
            assertThat(started.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(order).doesNotContain("handled");   // 处理器仍在耗时逻辑中

            release.countDown();
            assertThat(waitFor(() -> order.contains("handled"))).isTrue();
        } finally {
            executor.shutdownNow();
        }
    }

    private static boolean waitFor(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(20);
        }
        return condition.getAsBoolean();
    }

    @Test
    void unmatchedActionIsNotAcked() {
        List<String> acks = new CopyOnWriteArrayList<>();
        InteractionRouter acking = new InteractionRouter(List.of(card), (id, code) -> acks.add(id + ":" + code));

        assertThat(acking.handle(click("card:missing:p=2", 11, 2, "USER", null))).isEqualTo(HandlerResult.IGNORED);
        assertThat(acking.handle(click("unknown:next:p=2", 11, 2, "USER", null))).isEqualTo(HandlerResult.IGNORED);

        assertThat(acks).isEmpty();
    }

    @Test
    void playsWellWithDispatcherChain() {
        List<BotEvent> observed = new ArrayList<>();
        EventDispatcher dispatcher = new EventDispatcher(List.of(router), observed::add);

        dispatcher.dispatch(click("card:next:p=2", 11, 2, "USER", null));   // 命中 → 链在此终止
        dispatcher.dispatch(click("other:next:p=2", 11, 2, "USER", null));  // 未命中 → 继续走链

        assertThat(card.handled).hasSize(1);
        assertThat(observed).hasSize(2);   // 无论是否被消费，观察者都收到
    }

    private static ButtonHandler actionHandler(String action) {
        return new ButtonHandler() {
            @Override
            public String action() {
                return action;
            }

            @Override
            public HandlerResult onButton(FeatureContext context) {
                return HandlerResult.CONSUMED;
            }
        };
    }

    private static InteractionCreateEvent click(String buttonData, int type, Integer chatType, String userOpenid,
                                                String groupOpenid) {
        return click(buttonData, type, chatType, userOpenid, groupOpenid, null);
    }

    private static InteractionCreateEvent click(String buttonData, int type, Integer chatType, String userOpenid,
                                                String groupOpenid, String groupMemberOpenid) {
        InteractionResolved resolved = new InteractionResolved(buttonData, "btn-1", null, null, null, null, null, null,
                null, null);
        InteractionCreate payload = new InteractionCreate("EVENT_ID", type, chatType == 2 ? "c2c" : "group", chatType,
                "2026-10-02T00:00:00+08:00", null, null, userOpenid, groupOpenid, groupMemberOpenid,
                new InteractionData(type, resolved), 1, "102012345");
        return new InteractionCreateEvent(1, "INTERACTION_CREATE:EVENT_ID", new JsonMapper().readTree("{}"), payload);
    }

    /** 被测功能：只注册一个 next 动作。 */
    static final class CardFeature implements BotFeature {

        final List<FeatureContext> handled = new CopyOnWriteArrayList<>();
        HandlerResult result = HandlerResult.CONSUMED;
        RuntimeException failure;
        Runnable onHandle = () -> { };

        @Override
        public String id() {
            return "card";
        }

        @Override
        public List<ButtonHandler> buttonHandlers() {
            return List.of(new ButtonHandler() {
                @Override
                public String action() {
                    return "next";
                }

                @Override
                public HandlerResult onButton(FeatureContext context) {
                    onHandle.run();
                    handled.add(context);
                    if (failure != null) {
                        throw failure;
                    }
                    return result;
                }
            });
        }
    }
}
