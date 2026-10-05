package com.loop.new_loop_api.audit.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class AuditSourceTest {

    @ParameterizedTest
    @CsvSource(nullValues = "NULL", value = {
            // explicit header wins over the user agent
            "web,     okhttp/4.12.0,                                         ADMIN_WEB",
            "MOBILE,  Mozilla/5.0 (Windows NT 10.0),                         MOBILE_APP",
            // no header: inferred from the user agent
            "NULL,    'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/129', ADMIN_WEB",
            "NULL,    okhttp/4.12.0,                                         MOBILE_APP",
            "NULL,    LoopApp/12 CFNetwork/1498.700.2 Darwin/23.6.0,         MOBILE_APP",
            "NULL,    'Dalvik/2.1.0 (Linux; U; Android 13)',                 MOBILE_APP",
            "NULL,    curl/8.4.0,                                            UNKNOWN",
            "NULL,    NULL,                                                  UNKNOWN",
            "tablet,  NULL,                                                  UNKNOWN",
    })
    void should_resolveSourceFromClientHeaderOrUserAgent(String clientApp, String userAgent, String expected) {
        assertThat(AuditSource.resolve(clientApp, userAgent)).isEqualTo(expected);
    }
}
