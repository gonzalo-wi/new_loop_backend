package com.loop.new_loop_api.performance.service;

import com.loop.new_loop_api.common.security.CurrentUserProvider;
import com.loop.new_loop_api.performance.domain.AuditEventTranslator;
import com.loop.new_loop_api.performance.dto.ImportReport;
import com.loop.new_loop_api.performance.exception.MetricsAccessDeniedException;
import com.loop.new_loop_api.performance.repository.AuditHistoryRepository;
import com.loop.new_loop_api.performance.service.iService.AuditHistoryImportService;
import com.loop.new_loop_api.users.entity.Role;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.TreeMap;

/**
 * One-time import of the stock-control history still in the audit log (spec 001, FR-17 to FR-20).
 * It stops where live recording started, so no fact is counted twice, and it can be run again: entries
 * imported before are left alone. Each control is translated with its whole history, because rejection
 * rounds and response times depend on what came before.
 */
@Service
@RequiredArgsConstructor
public class AuditHistoryImportServiceImpl implements AuditHistoryImportService {

    private static final Logger log = LoggerFactory.getLogger(AuditHistoryImportServiceImpl.class);

    private final AuditHistoryRepository repository;
    private final CurrentUserProvider    currentUserProvider;
    private final Clock                  clock;

    @Override
    @Transactional
    public ImportReport importHistory() {
        assertAdmin();
        var cutoff     = repository.findLiveRecordingStart().orElseGet(() -> LocalDateTime.now(clock));
        var importedBefore = repository.findImportedAuditIds();
        var translator = new AuditEventTranslator(repository.findUsernames());
        var tally      = new Tally();

        repository.forEachControlHistory(cutoff, entries -> {
            var result = translator.translate(entries);
            var fresh  = result.events().stream()
                    .filter(event -> !importedBefore.contains(event.getSourceAuditId()))
                    .toList();
            tally.controls++;
            tally.imported += repository.insertImportedEvents(fresh);
            tally.importedBefore += result.events().size() - fresh.size();
            result.skipped().forEach(skip -> tally.skippedByReason.merge(skip.reason(), 1, Integer::sum));
        });

        var report = new ImportReport(cutoff, tally.controls, tally.imported, tally.importedBefore,
                tally.skippedByReason.values().stream().mapToInt(Integer::intValue).sum(), tally.skippedByReason);
        log.info("Audit history import up to {}: {} control(s), {} event(s) imported, {} imported before, {} skipped {}",
                cutoff, report.controls(), report.imported(), report.importedBefore(), report.skipped(),
                report.skippedByReason());
        return report;
    }

    private void assertAdmin() {
        var isAdmin = currentUserProvider.current().map(user -> user.role() == Role.ADMIN).orElse(false);
        if (!isAdmin) throw MetricsAccessDeniedException.importNotAllowed();
    }

    private static final class Tally {
        private int controls;
        private int imported;
        private int importedBefore;
        private final TreeMap<String, Integer> skippedByReason = new TreeMap<>();
    }
}
