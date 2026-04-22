package org.example.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "llm-gateway.upstream")
public class GatewayUpstreamProperties {

    private String mode = "mock";
    private Openai openai = new Openai();

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public Openai getOpenai() {
        return openai;
    }

    public void setOpenai(Openai openai) {
        this.openai = openai;
    }

    public static class Openai {
        private String baseUrl = "https://api.openai.com";
        private String chatCompletionsPath = "/v1/chat/completions";
        private String apiKey = "";
        private String defaultModel = "gpt-4o-mini";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getChatCompletionsPath() {
            return chatCompletionsPath;
        }

        public void setChatCompletionsPath(String chatCompletionsPath) {
            this.chatCompletionsPath = chatCompletionsPath;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getDefaultModel() {
            return defaultModel;
        }

        public void setDefaultModel(String defaultModel) {
            this.defaultModel = defaultModel;
        }
    }
}
