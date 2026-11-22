package com.loop.new_loop_api.settlements.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.loop.new_loop_api.common.exception.GlobalExceptionHandler;
import com.loop.new_loop_api.common.metrics.ErrorMetrics;
import com.loop.new_loop_api.common.security.JwtAuthenticationFilter;
import com.loop.new_loop_api.settlements.dto.ReceiptUpload;
import com.loop.new_loop_api.settlements.service.iService.DriverReceiptService;
import com.loop.new_loop_api.settlements.service.iService.DriverSettlementService;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(controllers = SettlementController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class SettlementControllerReceiptTest {

    private static final String URL = "/settlements/me/receipts";
    private static final String INVALID_UPLOAD = "Falta el reparto o la imagen, o el reparto no es válido";
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01};

    @Autowired private MockMvc mockMvc;

    @MockitoBean private DriverReceiptService driverReceiptService;
    @MockitoBean private DriverSettlementService driverSettlementService;
    @MockitoBean private ErrorMetrics errorMetrics;
    // WebMvcTest scans Filter beans even with the filters switched off.
    @MockitoBean private JwtAuthenticationFilter jwtAuthenticationFilter;

    private final UUID routeId = UUID.randomUUID();

    private static MockMultipartFile image(String name, String contentType) {
        return new MockMultipartFile(name, "photo.jpg", contentType, JPEG);
    }

    private void expectSpanishError(ResultActions result, int status, String error, String message) throws Exception {
        result.andExpect(status().is(status))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.error").value(error))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.path").value(URL))
                .andExpect(jsonPath("$.timestamp").exists());
        verifyNoInteractions(driverReceiptService);
    }

    @Test
    void shouldAnswer204WithEmptyBodyAndCallTheServiceOnceWithTheUpload() throws Exception {
        mockMvc.perform(multipart(URL).file(image("image", "image/jpeg")).param("routeId", routeId.toString()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        var captor = ArgumentCaptor.forClass(ReceiptUpload.class);
        verify(driverReceiptService).upload(captor.capture());
        ReceiptUpload upload = captor.getValue();
        assertThat(upload.routeId()).isEqualTo(routeId);
        assertThat(upload.declaredType()).isEqualTo("image/jpeg");
        assertThat(upload.size()).isEqualTo(JPEG.length);
        assertThat(readAll(upload)).isEqualTo(JPEG);
    }

    private static byte[] readAll(ReceiptUpload upload) throws IOException {
        try (var in = upload.content().getInputStream()) {
            return in.readAllBytes();
        }
    }

    @Test
    void shouldPassANullDeclaredTypeWhenThePartHasNone() throws Exception {
        mockMvc.perform(multipart(URL).file(image("image", null)).param("routeId", routeId.toString()))
                .andExpect(status().isNoContent());

        var captor = ArgumentCaptor.forClass(ReceiptUpload.class);
        verify(driverReceiptService).upload(captor.capture());
        assertThat(captor.getValue().declaredType()).isNull();
    }

    @Test
    void shouldIgnoreAnExtraFechaPartAndAnyOtherPart() throws Exception {
        mockMvc.perform(multipart(URL)
                        .file(image("image", "image/jpeg"))
                        .file(new MockMultipartFile("other", "x.txt", "text/plain", new byte[] {1}))
                        .param("routeId", routeId.toString())
                        .param("fecha", "1999-01-01"))
                .andExpect(status().isNoContent());

        var captor = ArgumentCaptor.forClass(ReceiptUpload.class);
        verify(driverReceiptService).upload(captor.capture());
        assertThat(captor.getValue().routeId()).isEqualTo(routeId);
    }

    @Test
    void shouldAnswer415WhenTheRequestIsJson() throws Exception {
        expectSpanishError(
                mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"routeId\":\"" + routeId + "\"}")),
                415, "Unsupported Media Type", "La foto debe enviarse como multipart/form-data");
    }

    @Test
    void shouldAnswer415NotFourHundredWhenTheBodyIsEmptyAndNotMultipart() throws Exception {
        expectSpanishError(mockMvc.perform(post(URL)), 415, "Unsupported Media Type",
                "La foto debe enviarse como multipart/form-data");
    }

    @Test
    void shouldAnswer400WhenRouteIdIsMissing() throws Exception {
        expectSpanishError(mockMvc.perform(multipart(URL).file(image("image", "image/jpeg"))),
                400, "Bad Request", INVALID_UPLOAD);
    }

    @Test
    void shouldAnswer400WhenRouteIdIsNotAUuid() throws Exception {
        expectSpanishError(
                mockMvc.perform(multipart(URL).file(image("image", "image/jpeg")).param("routeId", "not-a-uuid")),
                400, "Bad Request", INVALID_UPLOAD);
    }

    @Test
    void shouldAnswer400WhenRouteIdIsBlank() throws Exception {
        expectSpanishError(
                mockMvc.perform(multipart(URL).file(image("image", "image/jpeg")).param("routeId", " ")),
                400, "Bad Request", INVALID_UPLOAD);
    }

    @Test
    void shouldAnswer400WhenRouteIdIsRepeated() throws Exception {
        expectSpanishError(
                mockMvc.perform(multipart(URL).file(image("image", "image/jpeg"))
                        .param("routeId", routeId.toString(), UUID.randomUUID().toString())),
                400, "Bad Request", INVALID_UPLOAD);
    }

    @Test
    void shouldAnswer400WhenTheImageIsMissing() throws Exception {
        expectSpanishError(mockMvc.perform(multipart(URL).param("routeId", routeId.toString())),
                400, "Bad Request", INVALID_UPLOAD);
    }

    @Test
    void shouldAnswer400WhenTheImageIsRepeated() throws Exception {
        expectSpanishError(
                mockMvc.perform(multipart(URL)
                        .file(image("image", "image/jpeg"))
                        .file(image("image", "image/jpeg"))
                        .param("routeId", routeId.toString())),
                400, "Bad Request", INVALID_UPLOAD);
    }

    @Test
    void shouldNotTreatAFileNamedDifferentlyAsTheImage() throws Exception {
        expectSpanishError(
                mockMvc.perform(multipart(URL).file(image("photo", "image/jpeg")).param("routeId", routeId.toString())),
                400, "Bad Request", INVALID_UPLOAD);
    }

    @Test
    void shouldLetAZeroByteImageThroughSoTheServiceAnswers415() throws Exception {
        mockMvc.perform(multipart(URL)
                        .file(new MockMultipartFile("image", "empty.jpg", "image/jpeg", new byte[0]))
                        .param("routeId", routeId.toString()))
                .andExpect(status().isNoContent());

        var captor = ArgumentCaptor.forClass(ReceiptUpload.class);
        verify(driverReceiptService).upload(captor.capture());
        assertThat(captor.getValue().size()).isZero();
    }

    @Test
    void shouldNeverCallTheServiceOnAnyRejection() throws Exception {
        mockMvc.perform(multipart(URL).param("routeId", "x"));
        verifyNoInteractions(driverReceiptService);
        verify(errorMetrics, org.mockito.Mockito.atLeastOnce()).recordError(any(), any());
    }
}
