package love.aira.kohaku.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import love.aira.kohaku.gateway.event.AtMessageCreateEvent;
import love.aira.kohaku.gateway.event.BotDispatchEvent;
import love.aira.kohaku.gateway.event.C2cMessageCreateEvent;
import love.aira.kohaku.gateway.event.ChannelCreateEvent;
import love.aira.kohaku.gateway.event.ChannelDeleteEvent;
import love.aira.kohaku.gateway.event.ChannelUpdateEvent;
import love.aira.kohaku.gateway.event.DirectMessageCreateEvent;
import love.aira.kohaku.gateway.event.FriendAddEvent;
import love.aira.kohaku.gateway.event.FriendDelEvent;
import love.aira.kohaku.gateway.event.GuildCreateEvent;
import love.aira.kohaku.gateway.event.GuildDeleteEvent;
import love.aira.kohaku.gateway.event.GuildUpdateEvent;
import love.aira.kohaku.gateway.event.C2cMsgReceiveEvent;
import love.aira.kohaku.gateway.event.C2cMsgRejectEvent;
import love.aira.kohaku.gateway.event.GroupAddRobotEvent;
import love.aira.kohaku.gateway.event.GroupAtMessageCreateEvent;
import love.aira.kohaku.gateway.event.GroupDelRobotEvent;
import love.aira.kohaku.gateway.event.GroupMessageCreateEvent;
import love.aira.kohaku.gateway.event.GroupMsgReceiveEvent;
import love.aira.kohaku.gateway.event.GroupMsgRejectEvent;
import love.aira.kohaku.gateway.event.InteractionCreateEvent;
import love.aira.kohaku.gateway.event.MessageAuditPassEvent;
import love.aira.kohaku.gateway.event.MessageAuditRejectEvent;
import love.aira.kohaku.gateway.event.MessageCreateEvent;
import love.aira.kohaku.gateway.event.model.C2cMessage;
import love.aira.kohaku.gateway.event.model.ChannelMessage;
import love.aira.kohaku.gateway.event.model.MessageAudited;
import love.aira.kohaku.gateway.event.model.MessageScene;
import love.aira.kohaku.gateway.event.model.MessageAttachment;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 逐个事件验证反序列化：报文取自官方文档（含示例 JSON），并覆盖未知类型/畸形字段的回落路径。 */
class GatewayEventDecoderTest {

    private static final GatewayEventDecoder DECODER = new GatewayEventDecoder(new JsonMapper());

    private static JsonNode json(String raw) {
        return new JsonMapper().readTree(raw);
    }

    private static BotDispatchEvent decode(String type, String payload) {
        return DECODER.decode(7, type, "EVENT_ID", json(payload));
    }

    @Test
    void decodesC2cMessageWithSceneAttachmentsAndElements() {
        // 文档示例 1：普通文本
        C2cMessageCreateEvent text = (C2cMessageCreateEvent) decode("C2C_MESSAGE_CREATE", """
                {"id":"ROBOT1.0_x","author":{"id":"A1B2","user_openid":"A1B2","union_openid":"","username":"","bot":false},
                 "content":"你好，今天有什么推荐的活动吗？","message_type":0,
                 "message_scene":{"source":"default","ext":["msg_idx=REFIDX_abc=="]},
                 "timestamp":"2026-07-21T10:00:00+08:00"}""");
        C2cMessage message = text.payload();
        assertThat(message.id()).isEqualTo("ROBOT1.0_x");
        assertThat(message.author().userOpenid()).isEqualTo("A1B2");
        assertThat(message.content()).isEqualTo("你好，今天有什么推荐的活动吗？");
        assertThat(message.messageType()).isZero();
        assertThat(message.messageScene().messageIndex()).isEqualTo("REFIDX_abc==");
        assertThat(text.type()).isEqualTo("C2C_MESSAGE_CREATE");
        assertThat(text.seq()).isEqualTo(7);
        assertThat(text.data().path("id").stringValue()).isEqualTo("ROBOT1.0_x");   // 原始报文仍在

        // 文档示例 2：结构化卡片（ark_data 有值）
        C2cMessage card = ((C2cMessageCreateEvent) decode("C2C_MESSAGE_CREATE", """
                {"id":"ROBOT1.0_y","author":{"id":"B2","user_openid":"B2","bot":false},"content":"[卡片消息]",
                 "message_type":3,"ark_data":{"ark_type":"miniapp","ark_name":"小程序","prompt":"打卡",
                 "fields":{"title":"快来完成今日学习打卡","tag":"微信小程序"}},
                 "message_scene":{"source":"default","ext":["msg_idx=REFIDX_y=="]},"timestamp":"2026-07-21T10:01:00+08:00"}"""))
                .payload();
        assertThat(card.messageType()).isEqualTo(3);
        assertThat(card.arkData().arkType()).isEqualTo("miniapp");
        assertThat(card.arkData().fields().path("title").stringValue()).isEqualTo("快来完成今日学习打卡");

        // 文档示例 3：引用消息（msg_elements + ref_msg_idx）
        C2cMessage quoted = ((C2cMessageCreateEvent) decode("C2C_MESSAGE_CREATE", """
                {"id":"ROBOT1.0_z","author":{"id":"C3","user_openid":"C3","bot":false},"content":"谢谢你！",
                 "message_type":103,
                 "msg_elements":[{"msg_idx":"REFIDX_a==","message_type":103,"content":"每天坚持阅读半小时"}],
                 "message_scene":{"source":"default","ext":["ref_msg_idx=REFIDX_a==","msg_idx=REFIDX_z=="]},
                 "timestamp":"2026-07-21T10:02:00+08:00"}"""))
                .payload();
        assertThat(quoted.messageType()).isEqualTo(103);
        assertThat(quoted.msgElements()).singleElement()
                .satisfies(element -> assertThat(element.content()).isEqualTo("每天坚持阅读半小时"));
        assertThat(quoted.messageScene().refMessageIndex()).isEqualTo("REFIDX_a==");
        assertThat(quoted.messageScene().value("missing")).isNull();
    }

    @Test
    void decodesGroupMessages() {
        GroupAtMessageCreateEvent at = (GroupAtMessageCreateEvent) decode("GROUP_AT_MESSAGE_CREATE", """
                {"id":"ROBOT1.0_g","author":{"id":"M1","member_openid":"M1","member_role":"admin","bot":false},
                 "content":"内容（已去掉@前缀）","group_openid":"GROUP_OPENID","timestamp":"2026-07-21T10:00:00+08:00",
                 "message_type":0,"message_scene":{"source":"default","ext":["msg_idx=REFIDX_g=="]},
                 "attachments":[{"url":"https://x/a.png","filename":"a.png","width":100,"height":50,"size":10,
                                 "content_type":"image/png"}],
                 "mentions":[{"id":"M2","member_openid":"M2"}],"msg_elements":[]}""");
        assertThat(at.payload().groupOpenid()).isEqualTo("GROUP_OPENID");
        assertThat(at.payload().author().memberRole()).isEqualTo("admin");
        assertThat(at.payload().attachments()).singleElement().satisfies(attachment -> {
            assertThat(attachment.isImage()).isTrue();
            assertThat(attachment.isVoice()).isFalse();
            assertThat(attachment.width()).isEqualTo(100);
        });
        assertThat(at.payload().mentions()).singleElement()
                .satisfies(mention -> assertThat(mention.memberOpenid()).isEqualTo("M2"));

        GroupMessageCreateEvent all = (GroupMessageCreateEvent) decode("GROUP_MESSAGE_CREATE", """
                {"id":"ROBOT1.0_g2","author":{"id":"M3","member_openid":"M3"},"content":"群全量消息",
                 "group_openid":"GROUP2","timestamp":"2026-07-21T10:05:00+08:00","message_type":0}""");
        assertThat(all.payload().groupOpenid()).isEqualTo("GROUP2");
        assertThat(all.payload().content()).isEqualTo("群全量消息");
    }

    @Test
    void decodesVoiceAttachmentFields() {
        MessageAttachment attachment = DECODER.payload(json("""
                {"url":"https://x/v.silk","content_type":"voice","voice_wav_url":"https://x/v.wav",
                 "asr_refer_text":"你好","size":1234}"""), MessageAttachment.class);

        assertThat(attachment.isVoice()).isTrue();
        assertThat(attachment.isImage()).isFalse();
        assertThat(attachment.voiceWavUrl()).isEqualTo("https://x/v.wav");
        assertThat(attachment.asrReferText()).isEqualTo("你好");
        assertThat(attachment.size()).isEqualTo(1234L);
    }

    @Test
    void decodesGuildAndChannelEvents() {
        GuildCreateEvent created = (GuildCreateEvent) decode("GUILD_CREATE", """
                {"id":"123456789012345678","name":"技术交流频道","icon":"https://thirdqq.qlogo.cn/0",
                 "owner_id":"123456789012345678","member_count":100,"max_members":1000,
                 "description":"专注于技术分享与交流的频道","joined_at":"2026-01-01T00:00:00+08:00",
                 "op_user_id":"123456789012345678"}""");
        assertThat(created.payload().name()).isEqualTo("技术交流频道");
        assertThat(created.payload().memberCount()).isEqualTo(100);
        assertThat(created.payload().joinedAt()).isEqualTo("2026-01-01T00:00:00+08:00");

        GuildUpdateEvent updated = (GuildUpdateEvent) decode("GUILD_UPDATE", """
                {"id":"1","name":"更新后的频道","owner_id":"1","icon":"https://i","member_count":12,
                 "max_members":1000,"description":"更新后的描述"}""");
        assertThat(updated.payload().description()).isEqualTo("更新后的描述");

        GuildDeleteEvent deleted = (GuildDeleteEvent) decode("GUILD_DELETE", """
                {"id":"1","name":"测试频道","owner_id":"1","member_count":10,"max_members":1000}""");
        assertThat(deleted.payload().name()).isEqualTo("测试频道");

        ChannelCreateEvent channel = (ChannelCreateEvent) decode("CHANNEL_CREATE", """
                {"id":"100010","guild_id":"18700000000001","name":"闲聊","type":0,"sub_type":0,
                 "owner_id":"1234","position":3}""");
        assertThat(channel.payload().guildId()).isEqualTo("18700000000001");
        assertThat(channel.payload().type()).isZero();
        assertThat(channel.payload().position()).isEqualTo(3);

        ChannelUpdateEvent updatedChannel = (ChannelUpdateEvent) decode("CHANNEL_UPDATE",
                "{\"id\":\"1\",\"guild_id\":\"2\",\"name\":\"改名\",\"type\":0,\"sub_type\":0,\"owner_id\":\"3\"}");
        assertThat(updatedChannel.payload().name()).isEqualTo("改名");

        ChannelDeleteEvent deletedChannel = (ChannelDeleteEvent) decode("CHANNEL_DELETE",
                "{\"id\":\"1\",\"guild_id\":\"2\",\"name\":\"已删除\",\"type\":0,\"sub_type\":0,\"owner_id\":\"3\"}");
        assertThat(deletedChannel.payload().name()).isEqualTo("已删除");
    }

    @Test
    void decodesChannelMessageEventsAndAudit() {
        // 文档示例：AT_MESSAGE_CREATE（内容为 Message 对象）
        AtMessageCreateEvent at = (AtMessageCreateEvent) decode("AT_MESSAGE_CREATE", """
                {"author":{"avatar":"http://thirdqq.qlogo.cn/0","bot":false,"id":"1234","username":"abc"},
                 "channel_id":"100010","content":"ndnnd","guild_id":"18700000000001","id":"0812345677890abcdef",
                 "member":{"joined_at":"2021-04-12T16:34:42+08:00","roles":["1"]},
                 "timestamp":"2021-05-20T15:14:58+08:00","seq":101}""");
        ChannelMessage message = at.payload();
        assertThat(message.id()).isEqualTo("0812345677890abcdef");
        assertThat(message.channelId()).isEqualTo("100010");
        assertThat(message.author().username()).isEqualTo("abc");
        assertThat(message.author().avatar()).isEqualTo("http://thirdqq.qlogo.cn/0");
        assertThat(message.member().roles()).containsExactly("1");
        assertThat(message.seq()).isEqualTo(101);

        MessageCreateEvent all = (MessageCreateEvent) decode("MESSAGE_CREATE",
                "{\"id\":\"m1\",\"channel_id\":\"c1\",\"guild_id\":\"g1\",\"content\":\"hi\"}");
        assertThat(all.payload().content()).isEqualTo("hi");

        DirectMessageCreateEvent direct = (DirectMessageCreateEvent) decode("DIRECT_MESSAGE_CREATE",
                "{\"id\":\"m2\",\"channel_id\":\"c2\",\"guild_id\":\"g2\",\"content\":\"dm\"}");
        assertThat(direct.payload().content()).isEqualTo("dm");

        // 文档示例：MESSAGE_AUDIT_PASS / REJECT（内容为 MessageAudited）
        MessageAuditPassEvent passed = (MessageAuditPassEvent) decode("MESSAGE_AUDIT_PASS", """
                {"audit_id":"5f60b782-d134-4628-93b8-9baa4b182f48","audit_time":"2022-01-04T18:05:42+08:00",
                 "channel_id":"1699792","create_time":"2022-01-04T18:05:42+08:00",
                 "guild_id":"46646271634786417","message_id":"10d0df67"}""");
        MessageAudited audited = passed.payload();
        assertThat(audited.auditId()).isEqualTo("5f60b782-d134-4628-93b8-9baa4b182f48");
        assertThat(audited.messageId()).isEqualTo("10d0df67");

        MessageAuditRejectEvent rejected = (MessageAuditRejectEvent) decode("MESSAGE_AUDIT_REJECT",
                "{\"audit_id\":\"a2\",\"message_id\":null,\"guild_id\":\"g\",\"channel_id\":\"c\"}");
        assertThat(rejected.payload().auditId()).isEqualTo("a2");
        assertThat(rejected.payload().messageId()).isNull();
    }

    @Test
    void decodesFriendAndGroupLifecycleEvents() {
        FriendAddEvent friendAdd = (FriendAddEvent) decode("FRIEND_ADD", """
                {"openid":"A1B2C3D4","timestamp":1784570523,"scene":1001,"scene_param":"",
                 "author":{"union_openid":"DB85A74E"}}""");
        assertThat(friendAdd.payload().openid()).isEqualTo("A1B2C3D4");
        assertThat(friendAdd.payload().timestamp()).isEqualTo(1784570523L);
        assertThat(friendAdd.payload().scene()).isEqualTo(1001);
        assertThat(friendAdd.payload().author().unionOpenid()).isEqualTo("DB85A74E");

        FriendDelEvent friendDel = (FriendDelEvent) decode("FRIEND_DEL",
                "{\"openid\":\"A1B2\",\"timestamp\":1784570524}");
        assertThat(friendDel.payload().openid()).isEqualTo("A1B2");

        C2cMsgRejectEvent reject = (C2cMsgRejectEvent) decode("C2C_MSG_REJECT",
                "{\"openid\":\"A1B2\",\"timestamp\":1784570599}");
        assertThat(reject.payload().timestamp()).isEqualTo(1784570599L);

        C2cMsgReceiveEvent receive = (C2cMsgReceiveEvent) decode("C2C_MSG_RECEIVE",
                "{\"openid\":\"A1B2\",\"timestamp\":1784570617}");
        assertThat(receive.payload().openid()).isEqualTo("A1B2");

        GroupAddRobotEvent added = (GroupAddRobotEvent) decode("GROUP_ADD_ROBOT",
                "{\"group_openid\":\"G1\",\"op_member_openid\":\"M1\",\"timestamp\":1784570534}");
        assertThat(added.payload().groupOpenid()).isEqualTo("G1");
        assertThat(added.payload().opMemberOpenid()).isEqualTo("M1");

        GroupDelRobotEvent removed = (GroupDelRobotEvent) decode("GROUP_DEL_ROBOT",
                "{\"group_openid\":\"G1\",\"op_member_openid\":\"M2\",\"timestamp\":1784570535}");
        assertThat(removed.payload().opMemberOpenid()).isEqualTo("M2");

        GroupMsgRejectEvent groupReject = (GroupMsgRejectEvent) decode("GROUP_MSG_REJECT",
                "{\"group_openid\":\"G1\",\"op_member_openid\":\"M3\",\"timestamp\":1784570536}");
        assertThat(groupReject.payload().groupOpenid()).isEqualTo("G1");

        GroupMsgReceiveEvent groupReceive = (GroupMsgReceiveEvent) decode("GROUP_MSG_RECEIVE",
                "{\"group_openid\":\"G1\",\"op_member_openid\":\"M4\",\"timestamp\":1784570537}");
        assertThat(groupReceive.payload().opMemberOpenid()).isEqualTo("M4");
    }

    @Test
    void decodesInteractionCreate() {
        InteractionCreateEvent button = (InteractionCreateEvent) decode("INTERACTION_CREATE", """
                {"id":"EVENT_ID","type":11,"scene":"c2c","chat_type":2,"timestamp":"2026-07-21T10:00:00+08:00",
                 "user_openid":"U1","data":{"type":11,"resolved":{"button_data":"/retry","button_id":"b1"}},
                 "version":1,"application_id":"102012345"}""");
        assertThat(button.payload().type()).isEqualTo(11);
        assertThat(button.payload().scene()).isEqualTo("c2c");
        assertThat(button.payload().userOpenid()).isEqualTo("U1");
        assertThat(button.payload().data().type()).isEqualTo(11);
        assertThat(button.payload().data().resolved().buttonId()).isEqualTo("b1");
        assertThat(button.payload().data().resolved().buttonData()).isEqualTo("/retry");

        InteractionCreateEvent authorize = (InteractionCreateEvent) decode("INTERACTION_CREATE", """
                {"id":"E2","type":18,"scene":"group","chat_type":1,"timestamp":"2026-07-21T10:01:00+08:00",
                 "group_openid":"G1","group_member_openid":"M1",
                 "data":{"type":18,"resolved":{"authorize_data":{"opt_scene":"dialog","scope":"group_push"}}},
                 "version":1,"application_id":"102012345"}""");
        assertThat(authorize.payload().data().resolved().authorizeData().scope()).isEqualTo("group_push");
        assertThat(authorize.payload().data().resolved().authorizeData().optScene()).isEqualTo("dialog");

        InteractionCreateEvent feedback = (InteractionCreateEvent) decode("INTERACTION_CREATE", """
                {"id":"E3","type":13,"scene":"c2c","chat_type":2,"data":{"type":13,"resolved":
                 {"feedback_opt":"LIKE","checked":1,"message_id":"MSG_1",
                  "message_scene":{"ext":["disable_net_search=1"]}}}}""");
        assertThat(feedback.payload().data().resolved().feedbackOpt()).isEqualTo("LIKE");
        assertThat(feedback.payload().data().resolved().checked()).isEqualTo(1);
        assertThat(feedback.payload().data().resolved().messageScene().ext()).containsExactly("disable_net_search=1");
    }

    @Test
    void fallsBackToRawEventForUnknownType() {
        assertThat(DECODER.decode(1, "SOME_FUTURE_EVENT", "ID", json("{\"a\":1}"))).isNull();
        assertThat(DECODER.decode(1, null, "ID", json("{}"))).isNull();
        assertThat(DECODER.decode(1, "C2C_MESSAGE_CREATE", "ID", json("null"))).isNull();
    }

    @Test
    void fallsBackWhenPayloadShapeIsUnexpected() {
        // attachments 期望数组却给了字符串 → 解析失败，返回 null 让调用方用原始事件（不打断长连接）
        assertThat(DECODER.decode(1, "C2C_MESSAGE_CREATE", "ID", json("{\"attachments\":\"oops\"}"))).isNull();
        // 未知字段与缺失字段都应被容忍
        C2cMessageCreateEvent tolerant = (C2cMessageCreateEvent) DECODER.decode(1, "C2C_MESSAGE_CREATE", "ID",
                json("{\"id\":\"m\",\"unknown_field\":123}"));
        assertThat(tolerant.payload().id()).isEqualTo("m");
        assertThat(tolerant.payload().content()).isNull();
    }

    @Test
    void payloadEscapeHatchParsesArbitraryTypes() {
        MessageScene scene = DECODER.payload(json("{\"source\":\"default\",\"ext\":[\"msg_idx=REF==\"]}"),
                MessageScene.class);
        assertThat(scene.messageIndex()).isEqualTo("REF==");
    }
}
