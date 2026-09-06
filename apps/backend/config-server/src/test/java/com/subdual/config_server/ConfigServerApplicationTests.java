package com.subdual.config_server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "server.port=0",
        "spring.profiles.active=native"
})
class ConfigServerApplicationTests {

    @Test
    void contextLoads() {
    }
}
