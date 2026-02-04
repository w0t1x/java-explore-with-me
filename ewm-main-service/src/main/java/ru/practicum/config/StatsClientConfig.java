package ru.practicum.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.practicum.statsclient.StatsClient;
import ru.practicum.statsclient.SimpleStatsClient;

@Configuration
public class StatsClientConfig {

    @Value("${stats.server.url:http://localhost:9090}")
    private String statsServerUrl;

    @Bean
    public StatsClient statsClient() {
        return new SimpleStatsClient(statsServerUrl);
    }
}
