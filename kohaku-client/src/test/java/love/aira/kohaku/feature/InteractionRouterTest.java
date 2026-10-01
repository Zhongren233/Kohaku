package love.aira.kohaku.feature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import love.aira.kohaku.api.model.Keyboard;
import love.aira.kohaku.gateway.event.BotEvent;
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
        ButtonContext context = card.handled.getFirst();
        assertThat(context.featureId()).isEqualTo("card");
        assertThat(context.action()).isEqualTo("next");
        assertThat(context.intState("p", 1)).isEqualTo(2);
        assertThat(context.eventId()).isEqualTo("EVENT_ID");
        assertThat(context.buttonId()).isEqualTo("btn-1");
        assertThat(context.isC2c()).isTrue();
        assertThat(context.userOpenid()).isEqualTo("USER_OPENID");
    }

    @Test
    void routesGroupSceneClick() {
        router.handle(click("card:next:p=3", 11, 1, null, "GROUP_OPENID"));

        ButtonContext context = card.handled.getFirst();
        assertThat(context.isGroup()).isTrue();
        assertThat(context.groupOpenid()).isEqualTo("GROUP_OPENID");
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
        Keyboard keyboard = FeatureKeyboards.pagination("card", Pagination.of(1, 5, 12), null);
        List<Keyboard.Button> buttons = keyboard.content().rows().getFirst().buttons();

        assertThat(buttons).extracting(Keyboard.Button::id).containsExactly("page", "next");
        assertThat(buttons.get(1).renderData().label()).isEqualTo("下一页");
        // 直接拿生成出来的 data 去路由，应命中同一个功能
        assertThat(router.handle(click(buttons.get(1).action().data(), 11, 2, "USER", null)))
                .isEqualTo(HandlerResult.CONSUMED);
        assertThat(card.handled.getFirst().intState(FeatureKeyboards.STATE_PAGE, 1)).isEqualTo(2);
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
    void playsWellWithDispatcherChain() {
        List<BotEvent> fallback = new ArrayList<>();
        EventDispatcher dispatcher = new EventDispatcher(List.of(router), fallback::add);

        dispatcher.dispatch(click("card:next:p=2", 11, 2, "USER", null));   // 命中 → 消费
        dispatcher.dispatch(click("other:next:p=2", 11, 2, "USER", null));  // 未命中 → 兜底

        assertThat(card.handled).hasSize(1);
        assertThat(fallback).hasSize(1);
    }

    private static ButtonHandler actionHandler(String action) {
        return new ButtonHandler() {
            @Override
            public String action() {
                return action;
            }

            @Override
            public HandlerResult onButton(ButtonContext context) {
                return HandlerResult.CONSUMED;
            }
        };
    }

    private static InteractionCreateEvent click(String buttonData, int type, Integer chatType, String userOpenid,
                                                String groupOpenid) {
        InteractionResolved resolved = new InteractionResolved(buttonData, "btn-1", null, null, null, null, null, null,
                null, null);
        InteractionCreate payload = new InteractionCreate("EVENT_ID", type, chatType == 2 ? "c2c" : "group", chatType,
                "2026-10-02T00:00:00+08:00", null, null, userOpenid, groupOpenid, null,
                new InteractionData(type, resolved), 1, "102012345");
        return new InteractionCreateEvent(1, new JsonMapper().readTree("{}"), payload);
    }

    /** 被测功能：只注册一个 next 动作。 */
    static final class CardFeature implements BotFeature {

        final List<ButtonContext> handled = new CopyOnWriteArrayList<>();
        HandlerResult result = HandlerResult.CONSUMED;

        @Override
        public String id() {
            return "card";
        }

        @Override
        public List<ButtonHandler> buttonHandlers() {
            return List.of(new ButtonHandler() {
                @Override
                public String action() {
                    return FeatureKeyboards.ACTION_NEXT;
                }

                @Override
                public HandlerResult onButton(ButtonContext context) {
                    handled.add(context);
                    return result;
                }
            });
        }
    }
}
