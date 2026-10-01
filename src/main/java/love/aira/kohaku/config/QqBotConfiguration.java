package love.aira.kohaku.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(QqBotProperties.class)
public class QqBotConfiguration {

    /**
     * 供开放平台 REST 接口复用的 HTTP 客户端（Gateway 长连接自带单线程执行器的独立客户端）。
     */
    @Bean(destroyMethod = "close")
    HttpClient qqHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }
}
