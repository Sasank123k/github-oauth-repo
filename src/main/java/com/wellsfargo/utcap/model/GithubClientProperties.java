package com.wellsfargo.utcap.model;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "github.client")
public class GithubClientProperties {
    private String id= "Ov23li1FvQOcMLpdWLYV";
    private String secret= "89e6cabd4524ddce1a1c474d496288add0848184";

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }
}
