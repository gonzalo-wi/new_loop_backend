package com.loop.new_loop_api.performance.domain;

import com.loop.new_loop_api.common.exception.ForbiddenException;
import com.loop.new_loop_api.common.security.AuthenticatedUser;
import com.loop.new_loop_api.users.entity.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MetricsScopeTest {

    private final UUID ownBranch   = UUID.randomUUID();
    private final UUID otherBranch = UUID.randomUUID();

    @Test
    void shouldSeeEveryBranchIncludingMovementsWithoutBranch_when_adminDoesNotFilter() {
        var scope = MetricsScope.resolve(user(Role.ADMIN, null), null);

        assertThat(scope.allBranches()).isTrue();
        assertThat(scope.branchId()).isNull();
    }

    @Test
    void shouldSeeOnlyTheFilteredBranch_when_adminFiltersByBranch() {
        var scope = MetricsScope.resolve(user(Role.ADMIN, ownBranch), otherBranch);

        assertThat(scope.allBranches()).isFalse();
        assertThat(scope.branchId()).isEqualTo(otherBranch);
    }

    @Test
    void shouldIgnoreTheBranchFilter_when_supervisorAsksForAnotherBranch() {
        var scope = MetricsScope.resolve(user(Role.SUPERVISOR, ownBranch), otherBranch);

        assertThat(scope.allBranches()).isFalse();
        assertThat(scope.branchId()).isEqualTo(ownBranch);
    }

    @Test
    void shouldSeeOwnBranch_when_supervisorDoesNotFilter() {
        assertThat(MetricsScope.resolve(user(Role.SUPERVISOR, ownBranch), null).branchId())
                .isEqualTo(ownBranch);
    }

    @Test
    void shouldDenyWithAClearMessage_when_supervisorHasNoBranch() {
        assertThatThrownBy(() -> MetricsScope.resolve(user(Role.SUPERVISOR, null), null))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("no branch");
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"ADMIN", "SUPERVISOR"}, mode = EnumSource.Mode.EXCLUDE)
    void shouldDeny_when_roleIsNotAdminOrSupervisor(Role role) {
        assertThatThrownBy(() -> MetricsScope.resolve(user(role, ownBranch), null))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void shouldDeny_when_thereIsNoAuthenticatedUser() {
        assertThatThrownBy(() -> MetricsScope.resolve(null, null))
                .isInstanceOf(ForbiddenException.class);
    }

    private static AuthenticatedUser user(Role role, UUID branchId) {
        return new AuthenticatedUser(UUID.randomUUID(), "user1", "User One", role, branchId);
    }
}
