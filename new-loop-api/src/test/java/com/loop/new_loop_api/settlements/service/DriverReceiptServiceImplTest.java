package com.loop.new_loop_api.settlements.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.loop.new_loop_api.common.exception.ForbiddenException;
import com.loop.new_loop_api.common.exception.NotFoundException;
import com.loop.new_loop_api.common.security.AuthenticatedUser;
import com.loop.new_loop_api.common.security.CurrentUserProvider;
import com.loop.new_loop_api.integrations.aguas.client.AguasReceiptClient;
import com.loop.new_loop_api.integrations.aguas.dto.ReceiptImage;
import com.loop.new_loop_api.integrations.aguas.exception.AguasReceiptUploadException;
import com.loop.new_loop_api.routes.entity.Route;
import com.loop.new_loop_api.routes.repository.RouteRepository;
import com.loop.new_loop_api.settlements.domain.ReceiptImageFormat;
import com.loop.new_loop_api.settlements.dto.ReceiptUpload;
import com.loop.new_loop_api.settlements.exception.ReceiptImageTooLargeException;
import com.loop.new_loop_api.settlements.exception.RouteWithoutNumberException;
import com.loop.new_loop_api.settlements.exception.UnsupportedReceiptImageException;
import com.loop.new_loop_api.users.entity.Role;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamSource;

@ExtendWith(MockitoExtension.class)
class DriverReceiptServiceImplTest {

    private static final int MAX = 10 * 1024 * 1024;
    private static final byte[] JPEG_HEAD = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    @Mock private CurrentUserProvider currentUserProvider;
    @Mock private RouteRepository routeRepository;
    @Mock private AguasReceiptClient aguasReceiptClient;

    private final UUID driverId = UUID.randomUUID();
    private final UUID routeId = UUID.randomUUID();

    private DriverReceiptServiceImpl service;

    @BeforeEach
    void setUp() {
        service = serviceAt("2026-11-21T15:00:00Z");
    }

    private DriverReceiptServiceImpl serviceAt(String utcInstant) {
        // A UTC clock on purpose: the upload day must come from Argentina's zone, not from the clock's.
        Clock clock = Clock.fixed(Instant.parse(utcInstant), ZoneOffset.UTC);
        return new DriverReceiptServiceImpl(currentUserProvider, routeRepository, aguasReceiptClient, clock);
    }

    private void actingAs(Role role) {
        when(currentUserProvider.current())
                .thenReturn(Optional.of(new AuthenticatedUser(driverId, "chofer", "Chofer", role, null)));
    }

    private static byte[] jpegOfSize(int size) {
        byte[] bytes = new byte[size];
        System.arraycopy(JPEG_HEAD, 0, bytes, 0, Math.min(JPEG_HEAD.length, size));
        return bytes;
    }

    private ReceiptUpload upload(byte[] bytes, String declaredType) {
        return new ReceiptUpload(routeId, declaredType, bytes.length, new ByteArrayResource(bytes));
    }

    private static Route route(String code) {
        var route = new Route();
        route.setId(UUID.randomUUID());
        route.setCode(code);
        return route;
    }

    private void ownRoute(String code) {
        when(routeRepository.findByIdAndDriverIdAndActiveTrue(routeId, driverId)).thenReturn(Optional.of(route(code)));
    }

    private LocalDate sentDay() {
        ArgumentCaptor<LocalDate> day = ArgumentCaptor.forClass(LocalDate.class);
        verify(aguasReceiptClient).upload(anyLong(), day.capture(), any(ReceiptImage.class));
        return day.getValue();
    }

    @Test
    void shouldSendRouteNumberDayAndImageToAguas() {
        actingAs(Role.REPARTIDOR);
        ownRoute("Rto 179");
        byte[] bytes = jpegOfSize(100);

        service.upload(upload(bytes, "image/jpeg"));

        var image = ArgumentCaptor.forClass(ReceiptImage.class);
        verify(aguasReceiptClient).upload(eq(179L), eq(LocalDate.of(2026, 11, 21)), image.capture());
        assertThat(image.getValue().format()).isEqualTo(ReceiptImageFormat.JPEG);
        assertThat(image.getValue().bytes()).isEqualTo(bytes);
    }

    @Test
    void shouldKeepTodayAt2200ArgentinaEvenWithAUtcClock() {
        actingAs(Role.REPARTIDOR);
        ownRoute("Rto 1");
        service = serviceAt("2026-11-22T01:00:00Z"); // 22:00 on the 21st in Argentina

        service.upload(upload(jpegOfSize(10), null));

        assertThat(sentDay()).isEqualTo(LocalDate.of(2026, 11, 21));
    }

    @Test
    void shouldUseTheLastSecondOfTheDay() {
        actingAs(Role.REPARTIDOR);
        ownRoute("Rto 1");
        service = serviceAt("2026-11-22T02:59:59Z"); // 23:59:59 on the 21st

        service.upload(upload(jpegOfSize(10), null));

        assertThat(sentDay()).isEqualTo(LocalDate.of(2026, 11, 21));
    }

    @Test
    void shouldRollToTheNextDayAtArgentinaMidnight() {
        actingAs(Role.REPARTIDOR);
        ownRoute("Rto 1");
        service = serviceAt("2026-11-22T03:00:00Z"); // 00:00:00 on the 22nd

        service.upload(upload(jpegOfSize(10), null));

        assertThat(sentDay()).isEqualTo(LocalDate.of(2026, 11, 22));
    }

    @Test
    void shouldAcceptExactlyTenMegabytes() {
        actingAs(Role.REPARTIDOR);
        ownRoute("Rto 5");

        service.upload(upload(jpegOfSize(MAX), "image/jpeg"));

        verify(aguasReceiptClient).upload(eq(5L), any(LocalDate.class), any(ReceiptImage.class));
    }

    @Test
    void shouldRejectOneByteOverTenMegabytesWithoutReadingItOrTouchingTheRoute() throws IOException {
        actingAs(Role.REPARTIDOR);
        InputStreamSource content = mock(InputStreamSource.class);
        var tooBig = new ReceiptUpload(routeId, "image/jpeg", MAX + 1L, content);

        assertThatThrownBy(() -> service.upload(tooBig)).isInstanceOf(ReceiptImageTooLargeException.class);

        verify(content, never()).getInputStream();
        verifyNoInteractions(routeRepository, aguasReceiptClient);
    }

    @Test
    void shouldAnswerTooLargeBeforeNotFoundForAForeignRoute() {
        actingAs(Role.REPARTIDOR);
        var tooBig = new ReceiptUpload(routeId, "image/jpeg", 11L * 1024 * 1024, new ByteArrayResource(new byte[0]));

        assertThatThrownBy(() -> service.upload(tooBig)).isInstanceOf(ReceiptImageTooLargeException.class);

        verifyNoInteractions(routeRepository, aguasReceiptClient);
    }

    @Test
    void shouldNotTrustTheDeclaredSizeWhenTheStreamHasMoreBytes() {
        actingAs(Role.REPARTIDOR);
        var lying = new ReceiptUpload(routeId, "image/jpeg", 10, new ByteArrayResource(jpegOfSize(MAX + 1)));

        assertThatThrownBy(() -> service.upload(lying)).isInstanceOf(ReceiptImageTooLargeException.class);

        verifyNoInteractions(routeRepository, aguasReceiptClient);
    }

    @Test
    void shouldAnswerUnsupportedBeforeNotFoundForAForeignRoute() {
        actingAs(Role.REPARTIDOR);
        byte[] pdf = "%PDF-1.7".getBytes(StandardCharsets.US_ASCII);

        assertThatThrownBy(() -> service.upload(upload(pdf, "image/jpeg")))
                .isInstanceOf(UnsupportedReceiptImageException.class);

        verifyNoInteractions(routeRepository, aguasReceiptClient);
    }

    @Test
    void shouldRejectAnEmptyFileAsUnsupported() {
        actingAs(Role.REPARTIDOR);

        assertThatThrownBy(() -> service.upload(upload(new byte[0], "image/png")))
                .isInstanceOf(UnsupportedReceiptImageException.class);

        verifyNoInteractions(routeRepository, aguasReceiptClient);
    }

    @Test
    void shouldRejectADeclaredTypeThatDisagreesWithTheBytes() {
        actingAs(Role.REPARTIDOR);

        assertThatThrownBy(() -> service.upload(upload(jpegOfSize(20), "image/png")))
                .isInstanceOf(UnsupportedReceiptImageException.class);

        verifyNoInteractions(routeRepository, aguasReceiptClient);
    }

    @Test
    void shouldGiveOneNotFoundForMissingForeignUnassignedAndInactiveRoutes() {
        actingAs(Role.REPARTIDOR);
        // The repository query collapses all four cases into one empty result.
        when(routeRepository.findByIdAndDriverIdAndActiveTrue(routeId, driverId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upload(upload(jpegOfSize(20), null)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Reparto no encontrado");

        verifyNoInteractions(aguasReceiptClient);
    }

    @Test
    void shouldRejectARouteWithoutNumber() {
        actingAs(Role.REPARTIDOR);
        ownRoute("Casa central");

        assertThatThrownBy(() -> service.upload(upload(jpegOfSize(20), null)))
                .isInstanceOf(RouteWithoutNumberException.class);

        verifyNoInteractions(aguasReceiptClient);
    }

    @Test
    void shouldRejectARouteWithTooManyDigitsAsWithoutNumber() {
        actingAs(Role.REPARTIDOR);
        ownRoute("Rto " + "1".repeat(19));

        assertThatThrownBy(() -> service.upload(upload(jpegOfSize(20), null)))
                .isInstanceOf(RouteWithoutNumberException.class);

        verifyNoInteractions(aguasReceiptClient);
    }

    @Test
    void shouldDenyEveryoneButDriversWithoutTouchingAnything() {
        actingAs(Role.SUPERVISOR);

        assertThatThrownBy(() -> service.upload(upload(jpegOfSize(20), null))).isInstanceOf(ForbiddenException.class);

        verifyNoInteractions(routeRepository, aguasReceiptClient);
    }

    @Test
    void shouldDenyAnAnonymousCall() {
        when(currentUserProvider.current()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upload(upload(jpegOfSize(20), null))).isInstanceOf(ForbiddenException.class);

        verifyNoInteractions(routeRepository, aguasReceiptClient);
    }

    @Test
    void shouldLetAguasFailurePropagateUntouchedAndCallAguasOnce() {
        actingAs(Role.REPARTIDOR);
        ownRoute("Rto 9");
        var failure = new AguasReceiptUploadException("HTTP 500");
        doThrow(failure).when(aguasReceiptClient).upload(anyLong(), any(LocalDate.class), any(ReceiptImage.class));

        assertThatThrownBy(() -> service.upload(upload(jpegOfSize(20), null))).isSameAs(failure);

        verify(aguasReceiptClient, times(1)).upload(anyLong(), any(LocalDate.class), any(ReceiptImage.class));
    }

    @Test
    void shouldReportAnUnreadableUploadWithContext() {
        actingAs(Role.REPARTIDOR);
        InputStreamSource broken = () -> {
            throw new IOException("disk gone");
        };

        assertThatThrownBy(() -> service.upload(new ReceiptUpload(routeId, "image/jpeg", 10, broken)))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("receipt")
                .hasCauseInstanceOf(IOException.class);

        verifyNoInteractions(routeRepository, aguasReceiptClient);
    }
}
