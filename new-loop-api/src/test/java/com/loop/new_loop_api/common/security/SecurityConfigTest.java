package com.loop.new_loop_api.common.security;

import com.loop.new_loop_api.appupdate.controller.AppVersionController;
import com.loop.new_loop_api.appupdate.service.iService.AppVersionService;
import com.loop.new_loop_api.auth.controller.AuthController;
import com.loop.new_loop_api.auth.service.iService.AuthService;
import com.loop.new_loop_api.common.config.SecurityConfig;
import com.loop.new_loop_api.common.controller.HealthController;
import com.loop.new_loop_api.common.metrics.ErrorMetrics;
import com.loop.new_loop_api.holidays.controller.HolidayController;
import com.loop.new_loop_api.loadforecast.controller.LoadForecastController;
import com.loop.new_loop_api.loadforecast.dto.LoadForecastItemResponse;
import com.loop.new_loop_api.loadforecast.dto.LoadForecastResponse;
import com.loop.new_loop_api.loadforecast.exception.ForecastRouteNotFoundException;
import com.loop.new_loop_api.loadforecast.service.iService.LoadForecastService;
import com.loop.new_loop_api.settlements.controller.SettlementController;
import com.loop.new_loop_api.settlements.service.iService.DriverReceiptService;
import com.loop.new_loop_api.settlements.service.iService.DriverSettlementService;
import com.loop.new_loop_api.stockcontrols.controller.StockControlController;
import com.loop.new_loop_api.stockcontrols.service.iService.StockControlService;
import com.loop.new_loop_api.holidays.service.iService.HolidayService;
import com.loop.new_loop_api.users.controller.UserController;
import com.loop.new_loop_api.users.entity.Role;
import com.loop.new_loop_api.users.service.iService.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The real app filter chain against a few real controllers whose services are mocked. */
@WebMvcTest(controllers = {UserController.class, AppVersionController.class, AuthController.class, HealthController.class,
        HolidayController.class, StockControlController.class, SettlementController.class, LoadForecastController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JsonAuthErrorHandler.class, JwtService.class, SecurityConfigTest.SystemClock.class})
@TestPropertySource(properties = {
        "app.jwt.secret=" + SecurityConfigTest.SECRET,
        "management.security.username=monitor",
        "management.security.password=monitor"
})
class SecurityConfigTest {

    // Any 256-bit base64 key; only for tests.
    static final String SECRET = "dGVzdC1zZWNyZXQtdGhhdC1pcy1sb25nLWVub3VnaC1mb3ItaHMyNTY=";

    static class SystemClock {
        @org.springframework.context.annotation.Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }

    @Autowired private MockMvc    mockMvc;
    @Autowired private JwtService jwtService;

    @MockitoBean private AuthenticatedUserCache authenticatedUserCache;
    @MockitoBean private UserService            userService;
    @MockitoBean private AppVersionService      appVersionService;
    @MockitoBean private AuthService            authService;
    @MockitoBean private ErrorMetrics           errorMetrics;
    @MockitoBean private HolidayService         holidayService;
    @MockitoBean private StockControlService    stockControlService;
    @MockitoBean private DriverSettlementService driverSettlementService;
    @MockitoBean private DriverReceiptService    driverReceiptService;
    @MockitoBean private LoadForecastService     loadForecastService;

    private String tokenFor(String username, Role role) {
        when(authenticatedUserCache.findActive(username))
                .thenReturn(Optional.of(new AuthenticatedUser(UUID.randomUUID(), username, username, role, null)));
        return jwtService.generateToken(user(username), Map.of("role", role.name()));
    }

    private static User user(String username) {
        return (User) User.withUsername(username).password("x").authorities(List.of()).build();
    }

    private static String expiredToken() {
        var yesterday = Clock.fixed(Instant.now().minusSeconds(2 * 86_400), ZoneOffset.UTC);
        return new JwtService(SECRET, yesterday).generateToken(user("ctrl"), Map.of());
    }

    private static final String CONTROL_ID = "7f1c2b4e-0d6a-4c8e-9b1a-2f3e4d5c6b7a";

    @Test
    void shouldLetAViewerReadStockControls() throws Exception {
        var token = tokenFor("visor", Role.VISUALIZADOR);
        mockMvc.perform(get("/stock-controls").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/stock-controls/" + CONTROL_ID).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void shouldAnswer403_when_aViewerTriesToChangeAControl() throws Exception {
        var auth = "Bearer " + tokenFor("visor", Role.VISUALIZADOR);
        var json = "application/json";
        mockMvc.perform(post("/stock-controls").header("Authorization", auth).contentType(json).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/stock-controls/" + CONTROL_ID).header("Authorization", auth).contentType(json).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/stock-controls/" + CONTROL_ID + "/approve").header("Authorization", auth))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/stock-controls/" + CONTROL_ID + "/reject").header("Authorization", auth).contentType(json).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/stock-controls/" + CONTROL_ID + "/correct").header("Authorization", auth).contentType(json).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/stock-controls/remito").header("Authorization", auth))
                .andExpect(status().isForbidden());
    }

    // Viewers print the remitos of their branch from the panel (the service scopes the branch).
    @Test
    void shouldLetAViewerDownloadAndMarkRemitosPrinted() throws Exception {
        var auth = "Bearer " + tokenFor("visor", Role.VISUALIZADOR);
        var json = "application/json";
        var batch = "{\"ids\":[\"" + CONTROL_ID + "\"]}";
        mockMvc.perform(get("/stock-controls/" + CONTROL_ID + "/remito").header("Authorization", auth))
                .andExpect(status().isOk());
        mockMvc.perform(post("/stock-controls/" + CONTROL_ID + "/remito/printed").header("Authorization", auth))
                .andExpect(status().isOk());
        mockMvc.perform(post("/stock-controls/remitos").header("Authorization", auth).contentType(json).content(batch))
                .andExpect(status().isOk());
        mockMvc.perform(post("/stock-controls/remitos/printed").header("Authorization", auth).contentType(json).content(batch))
                .andExpect(status().isOk());
    }

    @Test
    void shouldAnswer403_when_aViewerReadsOutsideStockControls() throws Exception {
        mockMvc.perform(get("/users").header("Authorization", "Bearer " + tokenFor("visor", Role.VISUALIZADOR)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/holidays").header("Authorization", "Bearer " + tokenFor("visor", Role.VISUALIZADOR)))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAnswer403_when_aClarkistaReadsStockControls() throws Exception {
        mockMvc.perform(get("/stock-controls").header("Authorization", "Bearer " + tokenFor("clark", Role.CLARKISTA)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/users").header("Authorization", "Bearer " + tokenFor("clark", Role.CLARKISTA)))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAnswer403_when_anotherRoleReadsTheLoadForecast() throws Exception {
        for (var role : List.of(Role.REPARTIDOR, Role.CONTROLADOR, Role.SUPERVISOR, Role.VISUALIZADOR)) {
            mockMvc.perform(get("/load-forecast").param("routeNumber", "179")
                            .header("Authorization", "Bearer " + tokenFor("u" + role, role)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void shouldKeepPublicEndpointsOpen_forReadOnlyRoles() throws Exception {
        mockMvc.perform(get("/app/version").header("Authorization", "Bearer " + tokenFor("clark", Role.CLARKISTA)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"visor\",\"password\":\"secret\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldAnswer401_when_anAnonymousUserReadsStockControls() throws Exception {
        mockMvc.perform(get("/stock-controls")).andExpect(status().isUnauthorized());
    }

    @Test
    void shouldLetAClarkistaReadTheLoadForecast() throws Exception {
        when(loadForecastService.forecast(179)).thenReturn(new LoadForecastResponse("Rto 179",
                java.time.LocalDate.of(2026, 11, 17), List.of(java.time.LocalDate.of(2026, 11, 10)),
                List.of(new LoadForecastItemResponse("B20", "Bidon 20 L", 120))));

        mockMvc.perform(get("/load-forecast").param("routeNumber", "179")
                        .header("Authorization", "Bearer " + tokenFor("clark", Role.CLARKISTA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.routeCode").value("Rto 179"))
                .andExpect(jsonPath("$.data.loadDate").value("2026-11-17"))
                .andExpect(jsonPath("$.data.basedOnDates[0]").value("2026-11-10"))
                .andExpect(jsonPath("$.data.items[0].productCode").value("B20"))
                .andExpect(jsonPath("$.data.items[0].productName").value("Bidon 20 L"))
                .andExpect(jsonPath("$.data.items[0].quantity").value(120));
    }

    @Test
    void shouldLetAnAdminReadTheLoadForecast() throws Exception {
        when(loadForecastService.forecast(179)).thenReturn(new LoadForecastResponse("Rto 179",
                java.time.LocalDate.of(2026, 11, 17), List.of(), List.of()));

        mockMvc.perform(get("/load-forecast").param("routeNumber", "179")
                        .header("Authorization", "Bearer " + tokenFor("boss", Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    void shouldAnswer403_when_aClarkistaWritesOrGoesUnderTheForecastPath() throws Exception {
        var auth = "Bearer " + tokenFor("clark", Role.CLARKISTA);
        mockMvc.perform(post("/load-forecast").header("Authorization", auth))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/load-forecast/anything").header("Authorization", auth))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAnswer404_when_theForecastRouteIsNotFound() throws Exception {
        when(loadForecastService.forecast(999)).thenThrow(new ForecastRouteNotFoundException(999));

        mockMvc.perform(get("/load-forecast").param("routeNumber", "999")
                        .header("Authorization", "Bearer " + tokenFor("clark", Role.CLARKISTA)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldAnswer400_when_theRouteNumberIsMissingOrInvalid() throws Exception {
        var auth = "Bearer " + tokenFor("clark", Role.CLARKISTA);
        mockMvc.perform(get("/load-forecast").header("Authorization", auth))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/load-forecast").param("routeNumber", "0").header("Authorization", auth))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/load-forecast").param("routeNumber", "abc").header("Authorization", auth))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldAnswer401AsJson_when_thereIsNoToken() throws Exception {
        mockMvc.perform(get("/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/users"));
    }

    @Test
    void shouldAnswer401_when_theTokenExpired() throws Exception {
        mockMvc.perform(get("/users").header("Authorization", "Bearer " + expiredToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAnswer401_when_theTokenIsGarbage() throws Exception {
        mockMvc.perform(get("/users").header("Authorization", "Bearer not-a-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAnswer401_when_theUserIsNoLongerActive() throws Exception {
        var token = jwtService.generateToken(user("gone"), Map.of());
        when(authenticatedUserCache.findActive("gone")).thenReturn(Optional.empty());

        mockMvc.perform(get("/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldLetTheRequestThrough_when_theTokenIsValid() throws Exception {
        mockMvc.perform(get("/users").header("Authorization", "Bearer " + tokenFor("ctrl", Role.CONTROLADOR)))
                .andExpect(status().isOk());
    }

    @Test
    void shouldKeepPublicEndpointsOpen_withoutAToken() throws Exception {
        mockMvc.perform(get("/health")).andExpect(status().isOk());
        mockMvc.perform(get("/app/version")).andExpect(status().isOk());
        mockMvc.perform(get("/app/download/loop-1.2.12.apk")).andExpect(status().isOk());
    }

    @Test
    void shouldNotBlockTheLogin_when_aStaleTokenIsSent() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .header("Authorization", "Bearer " + expiredToken())
                        .contentType("application/json")
                        .content("{\"username\":\"ctrl\",\"password\":\"secret\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/app/version").header("Authorization", "Bearer not-a-token"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldAnswer403AsJson_when_aNonAdminUploadsAnApk() throws Exception {
        mockMvc.perform(apkUpload().header("Authorization", "Bearer " + tokenFor("ctrl", Role.CONTROLADOR)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void shouldAnswer401_when_anApkIsUploadedWithoutAToken() throws Exception {
        mockMvc.perform(apkUpload()).andExpect(status().isUnauthorized());
    }

    @Test
    void shouldLetAnAdminUploadAnApk() throws Exception {
        mockMvc.perform(apkUpload().header("Authorization", "Bearer " + tokenFor("boss", Role.ADMIN)))
                .andExpect(status().isCreated());
    }

    @Test
    void shouldLetADriverReadTheirSettlement() throws Exception {
        mockMvc.perform(get("/settlements/me").header("Authorization", "Bearer " + tokenFor("chofer", Role.REPARTIDOR)))
                .andExpect(status().isOk());
    }

    @Test
    void shouldAnswer403_when_aNonDriverReadsASettlement() throws Exception {
        mockMvc.perform(get("/settlements/me").header("Authorization", "Bearer " + tokenFor("sup", Role.SUPERVISOR)))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAnswer401_when_theSettlementIsReadWithoutAToken() throws Exception {
        mockMvc.perform(get("/settlements/me")).andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder receiptUpload() {
        return multipart("/settlements/me/receipts")
                .file(new MockMultipartFile("image", "photo.jpg", "image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}))
                .param("routeId", UUID.randomUUID().toString());
    }

    @Test
    void shouldAnswer401_when_aReceiptIsUploadedWithoutAToken() throws Exception {
        mockMvc.perform(receiptUpload()).andExpect(status().isUnauthorized());

        org.mockito.Mockito.verifyNoInteractions(driverReceiptService);
    }

    @Test
    void shouldLetADriverReachTheReceiptUpload() throws Exception {
        mockMvc.perform(receiptUpload().header("Authorization", "Bearer " + tokenFor("chofer", Role.REPARTIDOR)))
                .andExpect(status().isNoContent());

        org.mockito.Mockito.verify(driverReceiptService).upload(org.mockito.ArgumentMatchers.any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = Role.class, mode = org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE,
            names = "REPARTIDOR")
    void shouldAnswer403_when_aNonDriverUploadsAReceipt(Role role) throws Exception {
        mockMvc.perform(receiptUpload().header("Authorization", "Bearer " + tokenFor("u" + role, role)))
                .andExpect(status().isForbidden());

        org.mockito.Mockito.verifyNoInteractions(driverReceiptService);
    }

    @Test
    void shouldLetAnyLoggedInUserReadTheHolidays() throws Exception {
        mockMvc.perform(get("/holidays").header("Authorization", "Bearer " + tokenFor("sup", Role.SUPERVISOR)))
                .andExpect(status().isOk());
    }

    @Test
    void shouldAnswer401_when_theHolidaysAreReadWithoutAToken() throws Exception {
        mockMvc.perform(get("/holidays")).andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAnswer403_when_aNonAdminAddsAHoliday() throws Exception {
        mockMvc.perform(holidayCreation().header("Authorization", "Bearer " + tokenFor("sup", Role.SUPERVISOR)))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAnswer403_when_aNonAdminDeletesAHoliday() throws Exception {
        mockMvc.perform(delete("/holidays/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + tokenFor("sup", Role.SUPERVISOR)))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldLetAnAdminAddAndDeleteHolidays() throws Exception {
        mockMvc.perform(holidayCreation().header("Authorization", "Bearer " + tokenFor("boss", Role.ADMIN)))
                .andExpect(status().isCreated());
        mockMvc.perform(delete("/holidays/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + tokenFor("boss", Role.ADMIN)))
                .andExpect(status().isNoContent());
    }

    // Batch remito requests (spec 005, FR-20): validated before reaching the service.
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"/stock-controls/remitos", "/stock-controls/remitos/printed"})
    void shouldAnswer400_when_aRemitoBatchIsEmpty(String path) throws Exception {
        mockMvc.perform(remitoBatch(path, "{\"ids\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("ids must not be empty")));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"/stock-controls/remitos", "/stock-controls/remitos/printed"})
    void shouldAnswer400_when_aRemitoBatchHasMoreThan100Ids(String path) throws Exception {
        var ids = java.util.stream.IntStream.range(0, 101)
                .mapToObj(i -> "\"" + UUID.randomUUID() + "\"")
                .collect(java.util.stream.Collectors.joining(","));

        mockMvc.perform(remitoBatch(path, "{\"ids\":[" + ids + "]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("at most 100 remitos")));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"/stock-controls/remitos", "/stock-controls/remitos/printed"})
    void shouldAnswer400_when_aRemitoBatchHasANullId(String path) throws Exception {
        mockMvc.perform(remitoBatch(path, "{\"ids\":[null]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("ids must not contain null")));
    }

    @Test
    void shouldAcceptARemitoBatchOf100Ids() throws Exception {
        var ids = java.util.stream.IntStream.range(0, 100)
                .mapToObj(i -> "\"" + UUID.randomUUID() + "\"")
                .collect(java.util.stream.Collectors.joining(","));
        when(stockControlService.generateRemitosPdf(org.mockito.ArgumentMatchers.anyList())).thenReturn(new byte[]{1});

        mockMvc.perform(remitoBatch("/stock-controls/remitos", "{\"ids\":[" + ids + "]}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder remitoBatch(String path, String body) {
        return post(path)
                .header("Authorization", "Bearer " + tokenFor("sup", Role.SUPERVISOR))
                .contentType("application/json")
                .content(body);
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder holidayCreation() {
        return post("/holidays")
                .contentType("application/json")
                .content("{\"date\":\"2026-11-23\",\"description\":\"Soberanía Nacional\"}");
    }

    private static org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder apkUpload() {
        var builder = multipart("/app/version")
                .file(new MockMultipartFile("file", "loop.apk", "application/vnd.android.package-archive", new byte[]{1}));
        builder.param("version", "9.9.9");
        return builder;
    }

    @Test
    void shouldAnswer401WithoutCreatingASession_when_aBrowserAsksAnonymously() throws Exception {
        mockMvc.perform(get("/users").accept("text/html"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(request().sessionAttributeDoesNotExist("SPRING_SECURITY_SAVED_REQUEST"));
    }

    @Test
    void shouldAnswer401_when_theTokenIsSignedWithAnotherSecret() throws Exception {
        var otherSecret = "YW5vdGhlci1zZWNyZXQtdGhhdC1pcy1sb25nLWVub3VnaC1mb3ItaHMyNTY=";
        var foreign = new JwtService(otherSecret, Clock.systemUTC()).generateToken(user("ctrl"), Map.of());
        tokenFor("ctrl", Role.ADMIN);

        mockMvc.perform(get("/users").header("Authorization", "Bearer " + foreign))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldLetCorsPreflightsThrough_withoutAToken() throws Exception {
        mockMvc.perform(options("/users")).andExpect(status().is2xxSuccessful());
    }
}
