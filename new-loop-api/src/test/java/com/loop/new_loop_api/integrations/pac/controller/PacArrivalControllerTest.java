package com.loop.new_loop_api.integrations.pac.controller;

import com.loop.new_loop_api.common.exception.GlobalExceptionHandler;
import com.loop.new_loop_api.common.metrics.ErrorMetrics;
import com.loop.new_loop_api.common.security.JwtAuthenticationFilter;
import com.loop.new_loop_api.integrations.pac.dto.PacArrivalResponse;
import com.loop.new_loop_api.integrations.pac.service.iService.PacArrivalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PacArrivalController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class PacArrivalControllerTest {

    private static final String URL = "/integrations/pac/arrivals";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private PacArrivalService pacArrivalService;
    @MockitoBean private ErrorMetrics      errorMetrics;
    // WebMvcTest scans Filter beans even with the filters switched off.
    @MockitoBean private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void shouldReturnTheArrivalsInTheEnvelope_withTheExactOffsetString() throws Exception {
        var branchId = UUID.randomUUID();
        var day = LocalDate.parse("2026-11-02");
        when(pacArrivalService.getArrivals(day)).thenReturn(List.of(new PacArrivalResponse(
                179L, "Rto 179", branchId, "Centro", OffsetDateTime.parse("2026-11-02T15:20:00-03:00"))));

        mockMvc.perform(get(URL).param("date", "2026-11-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Arrivals retrieved successfully"))
                .andExpect(jsonPath("$.data[0].routeNumber").value(179))
                .andExpect(jsonPath("$.data[0].routeCode").value("Rto 179"))
                .andExpect(jsonPath("$.data[0].branchId").value(branchId.toString()))
                .andExpect(jsonPath("$.data[0].branchName").value("Centro"))
                .andExpect(jsonPath("$.data[0].arrivedAt").value("2026-11-02T15:20:00-03:00"));
    }

    @Test
    void shouldAnswer400_when_theDateIsMalformed() throws Exception {
        mockMvc.perform(get(URL).param("date", "2026-13-40"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(pacArrivalService);
    }

    @Test
    void shouldPassANullDate_when_theDateIsMissing() throws Exception {
        when(pacArrivalService.getArrivals(null)).thenReturn(List.of());

        mockMvc.perform(get(URL)).andExpect(status().isOk());

        verify(pacArrivalService).getArrivals(null);
        verify(pacArrivalService, never()).getArrivals(any(LocalDate.class));
    }

    @Test
    void shouldReturnAnEmptyList_when_nobodyArrived() throws Exception {
        when(pacArrivalService.getArrivals(any())).thenReturn(List.of());

        mockMvc.perform(get(URL).param("date", "2026-11-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
