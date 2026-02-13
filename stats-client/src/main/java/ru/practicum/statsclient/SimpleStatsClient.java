package ru.practicum.statsclient;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import ru.practicum.statsdto.EndpointHit;
import ru.practicum.statsdto.ViewStats;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class SimpleStatsClient implements StatsClient {
    private final HttpClient httpClient;
    private final String serverUrl;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Charset CHARSET = StandardCharsets.UTF_8;

    public SimpleStatsClient(String serverUrl) {
        this.httpClient = HttpClient.newHttpClient();
        this.serverUrl = serverUrl;

        this.objectMapper = new ObjectMapper();
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        SimpleModule module = new SimpleModule();
        module.addSerializer(LocalDateTime.class, new StdSerializer<>(LocalDateTime.class) {
            @Override
            public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider provider) throws IOException {
                gen.writeString(value.format(FORMATTER));
            }
        });
        this.objectMapper.registerModule(module);
    }

    @Override
    public void hit(EndpointHit endpointHit) {
        try {
            String json = objectMapper.writeValueAsString(endpointHit);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl + "/hit"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            System.err.println("Ошибка при отправке статистики: " + e.getMessage());
        }
    }

    @Override
    public List<ViewStats> getStats(LocalDateTime start, LocalDateTime end,
                                    List<String> uris, boolean unique) {
        try {
            StringBuilder urlBuilder = new StringBuilder(serverUrl + "/stats")
                    .append("?start=").append(URLEncoder.encode(start.format(FORMATTER), CHARSET))
                    .append("&end=").append(URLEncoder.encode(end.format(FORMATTER), CHARSET))
                    .append("&unique=").append(unique);

            if (uris != null && !uris.isEmpty()) {
                urlBuilder.append("&uris=").append(String.join(",", uris));
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(urlBuilder.toString()))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            return objectMapper.readValue(response.body(), new TypeReference<List<ViewStats>>() {
            });
        } catch (Exception e) {
            System.err.println("Ошибка при получении статистики: " + e.getMessage());
            return List.of();
        }
    }
}
