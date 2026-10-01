package love.aira.kohaku.config;

import java.util.Collection;

/**
 * 事件订阅 Intents 位，YAML 中多选，例如：
 *
 * <pre>
 * kohaku:
 *   qq:
 *     intents:
 *       - GUILDS
 *       - PUBLIC_GUILD_MESSAGES
 * </pre>
 *
 * <p>只有 {@link #GUILDS}、{@link #GUILD_MEMBERS}、{@link #PUBLIC_GUILD_MESSAGES} 是基础权限，
 * 其余需要在开放平台申请；订阅了无权限的位，网关会下发 OpCode 9 并以 4014 断开。
 */
public enum QqIntent {

    /** 频道基础事件：GUILD_CREATE/UPDATE/DELETE、CHANNEL_CREATE/UPDATE/DELETE（基础权限） */
    GUILDS(1 << 0),

    /** 频道成员事件：GUILD_MEMBER_ADD/UPDATE/REMOVE（基础权限） */
    GUILD_MEMBERS(1 << 1),

    /** 频道内全量消息：MESSAGE_CREATE/DELETE，仅私域机器人可订阅 */
    GUILD_MESSAGES(1 << 9),

    /** 消息表情表态：MESSAGE_REACTION_ADD/REMOVE */
    GUILD_MESSAGE_REACTIONS(1 << 10),

    /** 私信消息：DIRECT_MESSAGE_CREATE/DELETE */
    DIRECT_MESSAGE(1 << 12),

    /** 群与单聊事件：GROUP_AT_MESSAGE_CREATE、C2C_MESSAGE_CREATE、群成员变更等（需申请） */
    GROUP_AND_C2C_EVENT(1 << 25),

    /** 互动事件：INTERACTION_CREATE（需申请） */
    INTERACTION(1 << 26),

    /** 消息审核事件：MESSAGE_AUDIT_PASS/REJECT（需申请） */
    MESSAGE_AUDIT(1 << 27),

    /** 论坛事件：FORUM_THREAD_*、FORUM_POST_*、FORUM_REPLY_*，仅私域机器人（需申请） */
    FORUMS_EVENT(1 << 28),

    /** 音频事件：AUDIO_START/FINISH、AUDIO_ON_MIC/OFF_MIC（需申请） */
    AUDIO_ACTION(1 << 29),

    /** 公域消息事件：频道内 @机器人 的 AT_MESSAGE_CREATE、PUBLIC_MESSAGE_DELETE（基础权限） */
    PUBLIC_GUILD_MESSAGES(1 << 30);

    private final int bit;

    QqIntent(int bit) {
        this.bit = bit;
    }

    /** 该事件在 intents 位掩码中占用的位。 */
    public int bit() {
        return bit;
    }

    /** 将多选枚举合并为上报给网关的 intents 位掩码。 */
    public static int mask(Collection<QqIntent> intents) {
        int mask = 0;
        for (QqIntent intent : intents) {
            mask |= intent.bit;
        }
        return mask;
    }
}
