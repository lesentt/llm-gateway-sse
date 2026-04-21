package org.example.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "llm-gateway.security")
public class GatewaySecurityProperties {

    @NotNull
    private ApiKey apiKey = new ApiKey();

    public ApiKey getApiKey() {
        return apiKey;
    }

    public void setApiKey(ApiKey apiKey) {
        this.apiKey = apiKey;
    }

    public static class ApiKey {
        private boolean enabled = false;

        private String pepper = "";

        private List<String> hashes = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getPepper() {
            return pepper;
        }

        public void setPepper(String pepper) {
            this.pepper = pepper;
        }

        public List<String> getHashes() {
            return hashes;
        }

        public void setHashes(List<String> hashes) {
            this.hashes = hashes;
        }
    }
}

