package com.loop.new_loop_api.settlements.domain;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Image formats Aguas accepts for a receipt photo (spec 009). Decided by the real bytes, not the client's word. */
public enum ReceiptImageFormat {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png");

    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final Set<String> JPEG_TYPES = Set.of("image/jpeg", "image/jpg", "image/pjpeg");
    private static final String PNG_TYPE = "image/png";
    private static final String OCTET_STREAM = "application/octet-stream";

    private final String contentType;
    private final String extension;

    ReceiptImageFormat(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }

    /**
     * Empty when the bytes are not a supported image, or when a declared image type disagrees with them.
     * A missing, blank or octet-stream declared type leaves the decision to the bytes.
     */
    public static Optional<ReceiptImageFormat> resolve(String declaredType, byte[] head) {
        Optional<ReceiptImageFormat> real = detect(head);
        if (real.isEmpty()) return Optional.empty();
        String declared = normalize(declaredType);
        if (declared.isEmpty() || declared.equals(OCTET_STREAM)) return real;
        if (JPEG_TYPES.contains(declared)) return real.filter(f -> f == JPEG);
        if (declared.equals(PNG_TYPE)) return real.filter(f -> f == PNG);
        return Optional.empty();
    }

    private static Optional<ReceiptImageFormat> detect(byte[] head) {
        if (head == null) return Optional.empty();
        if (startsWith(head, JPEG_MAGIC)) return Optional.of(JPEG);
        if (startsWith(head, PNG_MAGIC)) return Optional.of(PNG);
        return Optional.empty();
    }

    private static boolean startsWith(byte[] head, byte[] magic) {
        if (head.length < magic.length) return false;
        for (int i = 0; i < magic.length; i++) {
            if (head[i] != magic[i]) return false;
        }
        return true;
    }

    private static String normalize(String declaredType) {
        if (declaredType == null) return "";
        int semicolon = declaredType.indexOf(';');
        String base = semicolon >= 0 ? declaredType.substring(0, semicolon) : declaredType;
        return base.trim().toLowerCase(Locale.ROOT);
    }
}
