package com.loop.new_loop_api.integrations.aguas.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.loop.new_loop_api.integrations.aguas.dto.ReceiptImage;
import com.loop.new_loop_api.integrations.aguas.exception.AguasReceiptUploadException;
import com.loop.new_loop_api.settlements.domain.ReceiptImageFormat;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AguasReceiptClientTest {

    private static final String URL = "http://aguas.test/api/set-file-reparto-comprobantes";
    private static final LocalDate DAY = LocalDate.of(2026, 11, 21);
    private static final byte[] JPEG_BYTES = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01, 0x02};

    private MockRestServiceServer server;
    private AguasReceiptClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://aguas.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AguasReceiptClient(builder.build());
    }

    private static String body(org.springframework.http.client.ClientHttpRequest request) {
        return ((MockClientHttpRequest) request).getBodyAsString(StandardCharsets.ISO_8859_1);
    }

    @Test
    void shouldPostMultipartWithRepartoFechaAndImagen() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, org.hamcrest.Matchers.startsWith("multipart/form-data")))
                .andExpect(request -> {
                    String body = body(request);
                    assertThat(body).contains("name=\"reparto\"").contains("\r\n\r\n1234\r\n");
                    assertThat(body).contains("name=\"fecha\"").contains("\r\n\r\n2026-11-21\r\n");
                    assertThat(body).contains("name=\"imagen\"; filename=\"comprobante-1234-2026-11-21.jpg\"");
                    assertThat(body).contains("Content-Type: image/jpeg");
                    assertThat(body).contains(new String(JPEG_BYTES, StandardCharsets.ISO_8859_1));
                })
                .andRespond(withNoContent());

        client.upload(1234L, DAY, new ReceiptImage(ReceiptImageFormat.JPEG, JPEG_BYTES));

        server.verify();
    }

    @Test
    void shouldUsePngFilenameAndContentTypeForPng() {
        byte[] png = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        server.expect(requestTo(URL))
                .andExpect(request -> {
                    String body = body(request);
                    assertThat(body).contains("filename=\"comprobante-7-2026-11-21.png\"");
                    assertThat(body).contains("Content-Type: image/png");
                })
                .andRespond(withNoContent());

        client.upload(7L, DAY, new ReceiptImage(ReceiptImageFormat.PNG, png));

        server.verify();
    }

    @Test
    void shouldTreatAny2xxWithAnyBodyAsSuccess() {
        server.expect(requestTo(URL)).andRespond(withSuccess("not json {{", MediaType.TEXT_HTML));

        client.upload(1L, DAY, new ReceiptImage(ReceiptImageFormat.JPEG, JPEG_BYTES));

        server.verify();
    }

    @Test
    void shouldTreat201WithEmptyBodyAsSuccess() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.CREATED));

        client.upload(1L, DAY, new ReceiptImage(ReceiptImageFormat.JPEG, JPEG_BYTES));

        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 404, 500, 503, 302, 301})
    void shouldFailWithAguasReceiptUploadExceptionOnNon2xx(int status) {
        server.expect(requestTo(URL))
                .andRespond(withStatus(HttpStatus.valueOf(status)).body("secret body from aguas"));

        assertThatThrownBy(() -> client.upload(1L, DAY, new ReceiptImage(ReceiptImageFormat.JPEG, JPEG_BYTES)))
                .isInstanceOf(AguasReceiptUploadException.class)
                .hasMessage("No se pudo enviar la foto a Aguas. Probá de nuevo.")
                .satisfies(e -> assertThat(((AguasReceiptUploadException) e).getDetail()).contains(String.valueOf(status)));

        server.verify();
    }

    @Test
    void shouldWrapTransportFailureKeepingTheCause() {
        IOException io = new IOException("connection reset");
        server.expect(requestTo(URL)).andRespond(withException(io));

        assertThatThrownBy(() -> client.upload(1L, DAY, new ReceiptImage(ReceiptImageFormat.JPEG, JPEG_BYTES)))
                .isInstanceOf(AguasReceiptUploadException.class)
                .hasCauseInstanceOf(RuntimeException.class);

        server.verify();
    }

    @Test
    void shouldNeverPrintImageBytesInToString() {
        var image = new ReceiptImage(ReceiptImageFormat.JPEG, JPEG_BYTES);

        assertThat(image.toString()).contains("JPEG").contains("5").doesNotContain("-1").doesNotContain("[B@");
    }
}
