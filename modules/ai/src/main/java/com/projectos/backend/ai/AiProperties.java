package com.projectos.backend.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ninerouter")
public class AiProperties {
    private String url = "http://127.0.0.1:20128";
    private String key = "";
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration readTimeout = Duration.ofSeconds(60);

    public AiProperties() {
    }

    public AiProperties(String url, String key, Duration connectTimeout, Duration readTimeout) {
        this.url = url;
        this.key = key;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
    }

    public String url() {
        return url;
    }

    public String key() {
        return key;
    }

    public Duration connectTimeout() {
        return connectTimeout;
    }

    public Duration readTimeout() {
        return readTimeout;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }
}
