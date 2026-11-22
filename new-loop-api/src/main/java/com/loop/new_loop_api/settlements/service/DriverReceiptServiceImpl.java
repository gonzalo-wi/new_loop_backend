package com.loop.new_loop_api.settlements.service;

import com.loop.new_loop_api.common.exception.ForbiddenException;
import com.loop.new_loop_api.common.exception.NotFoundException;
import com.loop.new_loop_api.common.security.CurrentUserProvider;
import com.loop.new_loop_api.integrations.aguas.client.AguasReceiptClient;
import com.loop.new_loop_api.integrations.aguas.dto.ReceiptImage;
import com.loop.new_loop_api.performance.domain.RouteNumber;
import com.loop.new_loop_api.routes.repository.RouteRepository;
import com.loop.new_loop_api.settlements.domain.ReceiptImageFormat;
import com.loop.new_loop_api.settlements.dto.ReceiptUpload;
import com.loop.new_loop_api.settlements.exception.ReceiptImageTooLargeException;
import com.loop.new_loop_api.settlements.exception.RouteWithoutNumberException;
import com.loop.new_loop_api.settlements.exception.UnsupportedReceiptImageException;
import com.loop.new_loop_api.settlements.service.iService.DriverReceiptService;
import com.loop.new_loop_api.users.entity.Role;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Service;

/**
 * Not transactional on purpose (same reason as the settlement screen): no database connection is held while
 * Aguas answers. The checks run in the order of spec 009 FR-8 steps 6 to 9, and Aguas is only called after all
 * of them pass.
 */
@Service
public class DriverReceiptServiceImpl implements DriverReceiptService {

    static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    static final String ROUTE_NOT_FOUND_MESSAGE = "Reparto no encontrado";
    // Explicit zone: the day must not depend on the JVM default or on the zone of the injected clock.
    private static final ZoneId ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");

    private final CurrentUserProvider currentUserProvider;
    private final RouteRepository routeRepository;
    private final AguasReceiptClient aguasReceiptClient;
    private final Clock clock;

    public DriverReceiptServiceImpl(CurrentUserProvider currentUserProvider, RouteRepository routeRepository,
                                    AguasReceiptClient aguasReceiptClient, Clock clock) {
        this.currentUserProvider = currentUserProvider;
        this.routeRepository = routeRepository;
        this.aguasReceiptClient = aguasReceiptClient;
        this.clock = clock;
    }

    @Override
    public void upload(ReceiptUpload upload) {
        var user = currentUserProvider.current()
                .filter(u -> u.role() == Role.REPARTIDOR)
                .orElseThrow(() -> new ForbiddenException("Only drivers can upload receipts"));
        if (upload.size() > MAX_IMAGE_BYTES) throw new ReceiptImageTooLargeException();

        byte[] bytes = readBounded(upload);
        ReceiptImageFormat format = ReceiptImageFormat.resolve(upload.declaredType(), bytes)
                .orElseThrow(UnsupportedReceiptImageException::new);

        var route = routeRepository.findByIdAndDriverIdAndActiveTrue(upload.routeId(), user.id())
                .orElseThrow(() -> new NotFoundException(ROUTE_NOT_FOUND_MESSAGE));
        Long number = RouteNumber.of(route.getCode());
        if (number == null) throw new RouteWithoutNumberException();

        LocalDate day = LocalDate.ofInstant(clock.instant(), ARGENTINA);
        aguasReceiptClient.upload(number, day, new ReceiptImage(format, bytes));
    }

    /** Never trusts the declared size: reads one byte past the limit at most. */
    private static byte[] readBounded(ReceiptUpload upload) {
        try (var in = upload.content().getInputStream()) {
            byte[] bytes = in.readNBytes((int) MAX_IMAGE_BYTES + 1);
            if (bytes.length > MAX_IMAGE_BYTES) throw new ReceiptImageTooLargeException();
            return bytes;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded receipt image", e);
        }
    }
}
