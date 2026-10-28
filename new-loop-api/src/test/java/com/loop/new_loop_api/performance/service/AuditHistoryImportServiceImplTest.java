package com.loop.new_loop_api.performance.service;

import com.loop.new_loop_api.common.exception.ForbiddenException;
import com.loop.new_loop_api.common.security.AuthenticatedUser;
import com.loop.new_loop_api.common.security.CurrentUserProvider;
import com.loop.new_loop_api.performance.domain.AuditEventTranslator;
import com.loop.new_loop_api.performance.domain.AuditEventTranslator.AuditEntry;
import com.loop.new_loop_api.performance.entity.OperationEvent;
import com.loop.new_loop_api.performance.repository.AuditHistoryRepository;
import com.loop.new_loop_api.users.entity.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditHistoryImportServiceImplTest {

    private static final ZoneId        ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final LocalDateTime NOW       = LocalDateTime.of(2026, 10, 28, 12, 0);
    private static final LocalDateTime T0        = LocalDateTime.of(2026, 3, 1, 9, 0);

    @Mock private AuditHistoryRepository repository;
    @Mock private CurrentUserProvider    currentUserProvider;

    private AuditHistoryImportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuditHistoryImportServiceImpl(repository, currentUserProvider,
                Clock.fixed(NOW.atZone(ARGENTINA).toInstant(), ARGENTINA));
    }

    @Test
    void shouldImportUpToTheStartOfLiveRecordingAndReport() {
        actingAs(Role.ADMIN);
        var liveStart = LocalDateTime.of(2026, 10, 29, 8, 0);
        var first     = UUID.randomUUID();
        var control1  = List.of(create(first), approve());
        var control2  = List.of(broken());
        when(repository.findLiveRecordingStart()).thenReturn(Optional.of(liveStart));
        when(repository.findImportedAuditIds()).thenReturn(Set.of(first));
        when(repository.findUsernames()).thenReturn(Map.of());
        feed(control1, control2);
        when(repository.insertImportedEvents(any())).thenAnswer(inv -> ((List<?>) inv.getArgument(0)).size());

        var report = service.importHistory();

        verify(repository).forEachControlHistory(eq(liveStart), any());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<OperationEvent>> inserted = ArgumentCaptor.forClass(List.class);
        verify(repository, times(2)).insertImportedEvents(inserted.capture());
        // The creation was imported by an earlier run: only the approval goes in, with its round rebuilt.
        assertThat(inserted.getAllValues().stream().flatMap(List::stream).toList())
                .singleElement().satisfies(e -> assertThat(e.getRejectionRound()).isZero());
        assertThat(report.cutoff()).isEqualTo(liveStart);
        assertThat(report.controls()).isEqualTo(2);
        assertThat(report.imported()).isEqualTo(1);
        assertThat(report.importedBefore()).isEqualTo(1);
        assertThat(report.skipped()).isEqualTo(1);
        assertThat(report.skippedByReason()).containsEntry(AuditEventTranslator.UNREADABLE_JSON, 1);
    }

    @Test
    void shouldImportEverythingUntilNow_when_liveRecordingHasNotStarted() {
        actingAs(Role.ADMIN);
        when(repository.findLiveRecordingStart()).thenReturn(Optional.empty());

        assertThat(service.importHistory().cutoff()).isEqualTo(NOW);
        verify(repository).forEachControlHistory(eq(NOW), any());
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = "ADMIN", mode = EnumSource.Mode.EXCLUDE)
    void shouldOnlyLetAdminsImport(Role role) {
        actingAs(role);

        assertThatThrownBy(() -> service.importHistory()).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(repository);
    }

    @SafeVarargs
    private void feed(List<AuditEntry>... controls) {
        doAnswer(inv -> {
            Consumer<List<AuditEntry>> consumer = inv.getArgument(1);
            for (var control : controls) consumer.accept(control);
            return null;
        }).when(repository).forEachControlHistory(any(), any());
    }

    private void actingAs(Role role) {
        when(currentUserProvider.current())
                .thenReturn(Optional.of(new AuthenticatedUser(UUID.randomUUID(), "me", "Me", role, null)));
    }

    private final UUID controlId = UUID.randomUUID();

    private AuditEntry create(UUID auditId) {
        return new AuditEntry(auditId, AuditEventTranslator.CREATE, null, "ctrl1", null, json("PENDING_DRIVER_APPROVAL"), null, T0);
    }

    private AuditEntry approve() {
        return new AuditEntry(UUID.randomUUID(), AuditEventTranslator.APPROVE, null, "driver1", null,
                json("ACCEPTED_BY_DRIVER"), null, T0.plusMinutes(5));
    }

    private static AuditEntry broken() {
        return new AuditEntry(UUID.randomUUID(), AuditEventTranslator.CREATE, null, "ctrl1", null, "{oops", null, T0);
    }

    private String json(String status) {
        return "{\"id\":\"" + controlId + "\",\"type\":\"ENTRY\",\"status\":\"" + status + "\",\"branchId\":\""
                + UUID.randomUUID() + "\",\"routeId\":\"" + UUID.randomUUID() + "\",\"items\":[]}";
    }
}
