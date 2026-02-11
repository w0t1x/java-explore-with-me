package ru.practicum.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.practicum.statsclient.SimpleStatsClient;
import ru.practicum.statsclient.StatsClient;

@Configuration
public class StatsClientConfig {

    @Bean
    public StatsClient statsClient(@Value("${stats-server.url:http://localhost:9090}") String serverUrl) {
        return new SimpleStatsClient(serverUrl);
    }
}
