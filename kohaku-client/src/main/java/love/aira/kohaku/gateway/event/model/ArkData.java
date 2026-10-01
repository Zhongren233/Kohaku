package love.aira.kohaku.gateway.event.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.JsonNode;

/**
 * 结构化卡片消息数据（文档 ARKData，{@code message_type=3} 时有值）。
 *
 * @param prompt  卡片消息中的用户操作提示文本
 * @param arkType 卡片类型标识：tuwen=图文 H5、feed=图文卡片、miniapp=小程序、map=位置卡片、
 *                contact_card=好友名片、video_share=视频分享、music_together=一起听歌、picture=图片
 * @param arkName 卡片类型中文名，如「图文 H5」「小程序」
 * @param fields  卡片字段，常见键名：tag/tags、title、desc、jump_url、preview、source、
 *                source_logo、tag_icon、nickname、avatar、address（结构随卡类型变化，按原样透传）
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ArkData(String prompt, String arkType, String arkName, JsonNode fields) {
}
