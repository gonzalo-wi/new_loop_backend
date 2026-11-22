package com.loop.new_loop_api.integrations.aguas.client;

import com.loop.new_loop_api.integrations.aguas.dto.ReceiptImage;
import com.loop.new_loop_api.integrations.aguas.exception.AguasReceiptUploadException;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Sends one receipt photo to Aguas, once: no retry, no redirect following, no body in logs (spec 009). */
@Component
public class AguasReceiptClient {

    static final String PATH = "/api/set-file-reparto-comprobantes";

    private static final Logger log = LoggerFactory.getLogger(AguasReceiptClient.class);

    private final RestClient restClient;

    public AguasReceiptClient(@Qualifier(AguasReceiptClientConfig.REST_CLIENT_BEAN) RestClient restClient) {
        this.restClient = restClient;
    }

    /** Returns on any 2xx (the body is ignored); throws {@link AguasReceiptUploadException} otherwise. */
    public void upload(long reparto, LocalDate fecha, ReceiptImage image) {
        String filename = "comprobante-" + reparto + "-" + fecha + "." + image.format().extension();
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("reparto", String.valueOf(reparto));
        parts.add("fecha", fecha.toString());
        parts.add("imagen", imagePart(filename, image));

        int status;
        try {
            // exchange(): a non-2xx never becomes a Spring exception, so no response body travels into messages.
            status = restClient.post()
                    .uri(PATH)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(parts)
                    .exchange((request, response) -> response.getStatusCode().value());
        } catch (RestClientException ex) {
            log.warn("Aguas receipt upload failed for route {} on {}: {}: {}",
                    reparto, fecha, ex.getClass().getSimpleName(), ex.getMessage());
            throw new AguasReceiptUploadException(ex);
        }

        if (status < 200 || status >= 300) {
            log.warn("Aguas receipt upload failed for route {} on {}: HTTP {}", reparto, fecha, status);
            throw new AguasReceiptUploadException("HTTP " + status);
        }
        log.info("Aguas receipt uploaded for route {} on {}", reparto, fecha);
    }

    /** Plain Spring converters only: MultipartBodyBuilder drags in reactive classes this app does not ship. */
    private static HttpEntity<ByteArrayResource> imagePart(String filename, ReceiptImage image) {
        ByteArrayResource resource = new ByteArrayResource(image.bytes()) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(image.format().contentType()));
        return new HttpEntity<>(resource, headers);
    }
}
