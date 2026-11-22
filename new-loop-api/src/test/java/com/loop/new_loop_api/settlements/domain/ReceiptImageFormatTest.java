package com.loop.new_loop_api.settlements.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ReceiptImageFormatTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10};
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00};
    private static final byte[] PDF = "%PDF-1.7".getBytes(StandardCharsets.US_ASCII);

    @ParameterizedTest
    @ValueSource(strings = {"image/jpeg", "image/jpg", "image/pjpeg"})
    void shouldAcceptEveryJpegDeclaredTypeWithJpegBytes(String declared) {
        assertThat(ReceiptImageFormat.resolve(declared, JPEG)).contains(ReceiptImageFormat.JPEG);
    }

    @Test
    void shouldAcceptPngDeclaredTypeWithPngBytes() {
        assertThat(ReceiptImageFormat.resolve("image/png", PNG)).contains(ReceiptImageFormat.PNG);
    }

    @ParameterizedTest
    @ValueSource(strings = {"IMAGE/JPEG", "Image/Png", "image/jpeg; charset=binary", "  image/jpeg ;q=1"})
    void shouldIgnoreCaseAndMimeParameters(String declared) {
        byte[] bytes = declared.toLowerCase().contains("png") ? PNG : JPEG;
        ReceiptImageFormat expected = bytes == PNG ? ReceiptImageFormat.PNG : ReceiptImageFormat.JPEG;
        assertThat(ReceiptImageFormat.resolve(declared, bytes)).contains(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "application/octet-stream", "Application/Octet-Stream; x=y"})
    void shouldLetBytesDecideWhenUndeclared(String declared) {
        assertThat(ReceiptImageFormat.resolve(declared, JPEG)).contains(ReceiptImageFormat.JPEG);
        assertThat(ReceiptImageFormat.resolve(declared, PNG)).contains(ReceiptImageFormat.PNG);
        assertThat(ReceiptImageFormat.resolve(declared, PDF)).isEmpty();
    }

    @Test
    void shouldRejectPngDeclaredAsJpegAndViceVersa() {
        assertThat(ReceiptImageFormat.resolve("image/jpeg", PNG)).isEmpty();
        assertThat(ReceiptImageFormat.resolve("image/png", JPEG)).isEmpty();
    }

    @Test
    void shouldRejectPdfBytesEvenWhenDeclaredAsImage() {
        assertThat(ReceiptImageFormat.resolve("image/jpeg", PDF)).isEmpty();
        assertThat(ReceiptImageFormat.resolve("image/png", PDF)).isEmpty();
    }

    @Test
    void shouldRejectEmptyAndTruncatedBytes() {
        assertThat(ReceiptImageFormat.resolve("image/jpeg", new byte[0])).isEmpty();
        assertThat(ReceiptImageFormat.resolve(null, new byte[0])).isEmpty();
        assertThat(ReceiptImageFormat.resolve(null, new byte[] {(byte) 0xFF, (byte) 0xD8})).isEmpty();
        assertThat(ReceiptImageFormat.resolve(null, new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47})).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"application/pdf", "image/gif", "text/plain", "image/jpegx"})
    void shouldRejectUnknownDeclaredTypeEvenWithValidBytes(String declared) {
        assertThat(ReceiptImageFormat.resolve(declared, JPEG)).isEmpty();
        assertThat(ReceiptImageFormat.resolve(declared, PNG)).isEmpty();
    }

    @Test
    void shouldExposeContentTypeAndExtension() {
        assertThat(ReceiptImageFormat.JPEG.contentType()).isEqualTo("image/jpeg");
        assertThat(ReceiptImageFormat.JPEG.extension()).isEqualTo("jpg");
        assertThat(ReceiptImageFormat.PNG.contentType()).isEqualTo("image/png");
        assertThat(ReceiptImageFormat.PNG.extension()).isEqualTo("png");
    }

    @Test
    void shouldReturnOptionalType() {
        Optional<ReceiptImageFormat> result = ReceiptImageFormat.resolve(null, JPEG);
        assertThat(result).isPresent();
    }
}
