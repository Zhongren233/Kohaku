package love.aira.kohaku.gateway.event.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 消息附件（文档 MessageAttachment）：图片、视频、语音、文件等。
 *
 * @param url           附件下载 URL
 * @param filename      文件名
 * @param width         图片宽度（像素），非图片附件无此字段
 * @param height        图片高度（像素），非图片附件无此字段
 * @param size          文件大小（字节）
 * @param contentType   附件内容类型：voice=语音、image/jpeg、image/png、image/gif、video/mp4、file=群文件
 * @param voiceWavUrl   语音转码后的 WAV URL
 * @param asrReferText  语音 ASR 参考结果
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MessageAttachment(
        String url,
        String filename,
        Integer width,
        Integer height,
        Long size,
        String contentType,
        String voiceWavUrl,
        String asrReferText) {

    public boolean isVoice() {
        return "voice".equals(contentType);
    }

    public boolean isImage() {
        return contentType != null && contentType.startsWith("image/");
    }

    public boolean isVideo() {
        return "video/mp4".equals(contentType);
    }
}
