package com.ourtoday.api.image;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestStorageConfig {

    @Bean
    @Primary
    public FakeImageStorage fakeImageStorage() {
        return new FakeImageStorage();
    }
}
