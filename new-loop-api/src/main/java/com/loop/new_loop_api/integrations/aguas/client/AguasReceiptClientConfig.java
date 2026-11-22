package com.loop.new_loop_api.integrations.aguas.client;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Dedicated HTTP client for receipt photos. It is not the Feign client of the other Aguas calls, whose
 * package-level logging prints full requests: here nothing logs a body, redirects are never followed and
 * connect + read stay inside the 15 s budget of spec 009 (FR-16, FR-17, NFR-3, NFR-4).
 */
@Configuration
public class AguasReceiptClientConfig {

    public static final String REST_CLIENT_BEAN = "aguasReceiptRestClient";

    @Bean(name = REST_CLIENT_BEAN)
    public RestClient aguasReceiptRestClient(
            @Value("${integrations.aguas.base-url}") String baseUrl,
            @Value("${integrations.aguas.receipts.connect-timeout-ms}") long connectTimeoutMs,
            @Value("${integrations.aguas.receipts.read-timeout-ms}") long readTimeoutMs) {
        return restClient(baseUrl, Duration.ofMillis(connectTimeoutMs), Duration.ofMillis(readTimeoutMs));
    }

    /** Also used by tests with small timeouts. */
    public static RestClient restClient(String baseUrl, Duration connectTimeout, Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }
}
