package com.blanchebridal.backend.auth;

import com.blanchebridal.backend.auth.security.JwtFilter;
import com.blanchebridal.backend.config.security.SecurityConfig;
import com.blanchebridal.backend.user.controller.AdminController;
import com.blanchebridal.backend.user.dto.req.CreateUserRequest;
import com.blanchebridal.backend.user.dto.req.CreateWalkInCustomerRequest;
import com.blanchebridal.backend.user.dto.req.MeasurementsRequest;
import com.blanchebridal.backend.user.dto.req.UpdateCustomerProfileRequest;
import com.blanchebridal.backend.user.dto.res.CustomerDetailResponse;
import com.blanchebridal.backend.user.dto.res.MeasurementsResponse;
import com.blanchebridal.backend.user.dto.res.UserResponse;
import com.blanchebridal.backend.user.entity.User;
import com.blanchebridal.backend.user.entity.UserRole;
import com.blanchebridal.backend.user.entity.UserStatus;
import com.blanchebridal.backend.user.service.AdminService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-layer tests for AdminController.
 *
 * Scope: verifies routing, request validation wiring, and role-based access control
 * (@PreAuthorize + SecurityConfig URL rules), mirroring the manual Postman verification
 * already done in "AdminController" collection (folders A–E).
 *
 * Does NOT hit a real database — AdminService is mocked. Business logic inside
 * AdminService itself should be covered separately (service-layer unit tests) or
 * by integration tests against a real/Testcontainers Postgres for the modules where
 * that level of confidence matters more (e.g. Orders, Payments).
 *
 * ASSUMPTIONS MADE (adjust if wrong — these were not visible from the controller alone):
 *  - UserRole enum has constants ADMIN, EMPLOYEE, CUSTOMER
 *  - AdminService method return types match what's used below (List<UserResponse>,
 *    UserResponse, CustomerDetailResponse, List<MeasurementsResponse>, MeasurementsResponse)
 *  - spring-security-test is on the test classpath (needed for SecurityMockMvcRequestPostProcessors)
 */
@WebMvcTest(controllers = AdminController.class)
@Import(SecurityConfig.class)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AdminService adminService;

    // Real SecurityConfig wires a real JwtFilter bean requirement — mock it and make it
    // a pass-through so our manually-injected Authentication (via .with(authentication(...)))
    // is what actually gets evaluated by @PreAuthorize / the URL security rules, not real JWT parsing.
    @MockitoBean
    private JwtFilter jwtFilter;

    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final UUID EMPLOYEE_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID MEASUREMENT_ID = UUID.randomUUID();

    @BeforeEach
    void makeJwtFilterPassThrough() throws Exception {
        doAnswer(invocation -> {
            ServletRequest request = invocation.getArgument(0);
            ServletResponse response = invocation.getArgument(1);
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(request, response);
            return null;
        }).when(jwtFilter).doFilter(any(), any(), any());
    }

    // ─── Authentication helpers ─────────────────────────────────────────────

    private User principal(UUID id, UserRole role) {
        return User.builder()
                .id(id)
                .email(role.name().toLowerCase() + "@test.com")
                .role(role)
                .status(UserStatus.ACTIVE)
                .firstName("Test")
                .lastName(role.name())
                .phone("0770000000")
                .build();
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor asAdmin() {
        User admin = principal(ADMIN_ID, UserRole.ADMIN);
        return authentication(new UsernamePasswordAuthenticationToken(
                admin, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor asEmployee() {
        User employee = principal(EMPLOYEE_ID, UserRole.EMPLOYEE);
        return authentication(new UsernamePasswordAuthenticationToken(
                employee, null, List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor asCustomer() {
        User customer = principal(CUSTOMER_ID, UserRole.CUSTOMER);
        return authentication(new UsernamePasswordAuthenticationToken(
                customer, null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
    }

    // ─── Sample DTOs ────────────────────────────────────────────────────────

    private UserResponse sampleUser(UUID id, String role, boolean active) {
        return new UserResponse(id, "sample@test.com", role, "First", "Last",
                "0771234567", null, active, LocalDateTime.now());
    }

    private CustomerDetailResponse sampleCustomerDetail(UUID id) {
        return new CustomerDetailResponse(id, "walkin@test.com", "Walkin", "Customer",
                "0779876543", true, LocalDateTime.now(),
                "Prefers ivory tones", List.of("https://example.com/design1.jpg"),
                List.of());
    }

    private MeasurementsResponse sampleMeasurement(UUID customerId) {
        return new MeasurementsResponse(MEASUREMENT_ID, "msr_abc123", customerId,
                BigDecimal.valueOf(165.5), BigDecimal.valueOf(140), BigDecimal.valueOf(90),
                BigDecimal.valueOf(75), BigDecimal.valueOf(68), BigDecimal.valueOf(96),
                BigDecimal.valueOf(38), BigDecimal.valueOf(40), BigDecimal.valueOf(55),
                BigDecimal.valueOf(58), BigDecimal.valueOf(105), BigDecimal.valueOf(42),
                BigDecimal.valueOf(28), BigDecimal.valueOf(24), BigDecimal.valueOf(15),
                BigDecimal.valueOf(55), BigDecimal.valueOf(85), BigDecimal.valueOf(18),
                BigDecimal.valueOf(25), BigDecimal.valueOf(34), BigDecimal.valueOf(200),
                "Standard fitting", LocalDateTime.now());
    }

    private CreateUserRequest validUserRequest() {
        return new CreateUserRequest("newuser@test.com", "Password123", "New", "User", "0771112222");
    }

    private CreateWalkInCustomerRequest validWalkInRequest() {
        return new CreateWalkInCustomerRequest("walkin@test.com", "Walkin", "Customer", "0779876543");
    }

    private MeasurementsRequest validMeasurementsRequest() {
        return new MeasurementsRequest(
                BigDecimal.valueOf(165.5), BigDecimal.valueOf(140), BigDecimal.valueOf(90),
                BigDecimal.valueOf(75), BigDecimal.valueOf(68), BigDecimal.valueOf(96),
                BigDecimal.valueOf(38), BigDecimal.valueOf(40), BigDecimal.valueOf(55),
                BigDecimal.valueOf(58), BigDecimal.valueOf(105), BigDecimal.valueOf(42),
                BigDecimal.valueOf(28), BigDecimal.valueOf(24), BigDecimal.valueOf(15),
                BigDecimal.valueOf(55), BigDecimal.valueOf(85), BigDecimal.valueOf(18),
                BigDecimal.valueOf(25), BigDecimal.valueOf(34), BigDecimal.valueOf(200),
                "Standard fitting");
    }

    private UpdateCustomerProfileRequest validProfileRequest() {
        return new UpdateCustomerProfileRequest("Prefers ivory tones", List.of("https://example.com/design1.jpg"));
    }

    // ─── A. Employees ───────────────────────────────────────────────────────

    @Nested
    class Employees {

        @Test
        void listEmployees_asAdmin_returns200() throws Exception {
            when(adminService.listEmployees()).thenReturn(List.of(sampleUser(EMPLOYEE_ID, "EMPLOYEE", true)));

            mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/employees").with(asAdmin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data[0].role").value("EMPLOYEE"));
        }

        @Test
        void createEmployee_asAdmin_returns200WithEmployeeRole() throws Exception {
            when(adminService.createEmployee(any())).thenReturn(sampleUser(EMPLOYEE_ID, "EMPLOYEE", true));

            mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/employees")
                            .with(asAdmin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validUserRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.role").value("EMPLOYEE"));
        }

        @Test
        void createEmployee_missingRequiredField_returns400() throws Exception {
            String invalidJson = "{\"email\":\"bad-email-format\",\"password\":\"123\",\"firstName\":\"\",\"lastName\":\"User\"}";

            mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/employees")
                            .with(asAdmin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidJson))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void deactivateEmployee_asAdmin_returns200() throws Exception {
            when(adminService.deactivateEmployee(EMPLOYEE_ID)).thenReturn(sampleUser(EMPLOYEE_ID, "EMPLOYEE", false));

            mockMvc.perform(MockMvcRequestBuilders.put("/api/admin/employees/{id}/deactivate", EMPLOYEE_ID).with(asAdmin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.active").value(false));
        }

        @Test
        void activateEmployee_asAdmin_returns200() throws Exception {
            when(adminService.activateEmployee(EMPLOYEE_ID)).thenReturn(sampleUser(EMPLOYEE_ID, "EMPLOYEE", true));

            mockMvc.perform(MockMvcRequestBuilders.put("/api/admin/employees/{id}/activate", EMPLOYEE_ID).with(asAdmin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.active").value(true));
        }
    }

    // ─── B. Customers ───────────────────────────────────────────────────────

    @Nested
    class Customers {

        @Test
        void createWalkInCustomer_asAdmin_isActiveImmediately() throws Exception {
            when(adminService.createWalkInCustomer(any())).thenReturn(sampleUser(CUSTOMER_ID, "CUSTOMER", true));

            mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/customers")
                            .with(asAdmin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validWalkInRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.active").value(true));
        }

        @Test
        void getCustomerDetail_asAdmin_returnsProfileAndMeasurements() throws Exception {
            when(adminService.getCustomerDetail(CUSTOMER_ID)).thenReturn(sampleCustomerDetail(CUSTOMER_ID));

            mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/customers/{id}/detail", CUSTOMER_ID).with(asAdmin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.adminNotes").value("Prefers ivory tones"));
        }

        @Test
        void updateCustomerProfile_asAdmin_returns200() throws Exception {
            when(adminService.updateCustomerProfile(eq(CUSTOMER_ID), any())).thenReturn(sampleCustomerDetail(CUSTOMER_ID));

            mockMvc.perform(MockMvcRequestBuilders.put("/api/admin/customers/{id}/profile", CUSTOMER_ID)
                            .with(asAdmin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validProfileRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.adminNotes").value("Prefers ivory tones"));
        }

        @Test
        void deactivateCustomer_asEmployee_returns403() throws Exception {
            mockMvc.perform(MockMvcRequestBuilders.put("/api/admin/customers/{id}/deactivate", CUSTOMER_ID).with(asEmployee()))
                    .andExpect(status().isForbidden());
        }
    }

    // ─── C. Measurements ────────────────────────────────────────────────────

    @Nested
    class Measurements {

        @Test
        void addMeasurement_asAdmin_returns200() throws Exception {
            when(adminService.addMeasurement(eq(CUSTOMER_ID), any(), eq(ADMIN_ID)))
                    .thenReturn(sampleMeasurement(CUSTOMER_ID));

            mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/customers/{id}/measurements", CUSTOMER_ID)
                            .with(asAdmin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validMeasurementsRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.customerId").value(CUSTOMER_ID.toString()));
            // Confirms the controller correctly resolves the authenticated admin's id
            // (currentUser.getId()) and passes it through — this is the recordedBy check
            // flagged as worth verifying manually in the Postman round.
        }

        @Test
        void listMeasurements_asAdmin_returns200() throws Exception {
            when(adminService.listMeasurements(CUSTOMER_ID)).thenReturn(List.of(sampleMeasurement(CUSTOMER_ID)));

            mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/customers/{id}/measurements", CUSTOMER_ID).with(asAdmin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].id").value(MEASUREMENT_ID.toString()));
        }

        @Test
        void updateMeasurement_asAdmin_returns200() throws Exception {
            when(adminService.updateMeasurement(eq(CUSTOMER_ID), eq(MEASUREMENT_ID), any()))
                    .thenReturn(sampleMeasurement(CUSTOMER_ID));

            mockMvc.perform(MockMvcRequestBuilders.put(
                                    "/api/admin/customers/{cid}/measurements/{mid}", CUSTOMER_ID, MEASUREMENT_ID)
                            .with(asAdmin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validMeasurementsRequest())))
                    .andExpect(status().isOk());
        }

        @Test
        void addMeasurement_asCustomer_returns403() throws Exception {
            mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/customers/{id}/measurements", CUSTOMER_ID)
                            .with(asCustomer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validMeasurementsRequest())))
                    .andExpect(status().isForbidden());
        }
    }

    // ─── D. Admins ──────────────────────────────────────────────────────────

    @Nested
    class Admins {

        @Test
        void listAdmins_asAdmin_returns200() throws Exception {
            when(adminService.listAdmins()).thenReturn(List.of(sampleUser(ADMIN_ID, "ADMIN", true)));

            mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/admins").with(asAdmin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].role").value("ADMIN"));
        }

        @Test
        void createAdmin_asAdmin_returns200() throws Exception {
            UUID newAdminId = UUID.randomUUID();
            when(adminService.createAdmin(any())).thenReturn(sampleUser(newAdminId, "ADMIN", true));

            mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/admins")
                            .with(asAdmin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validUserRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.role").value("ADMIN"));
        }
    }

    // ─── E. Negative auth — covers every endpoint, both wrong-role and no-auth ─

    @Nested
    class NegativeAuth {

        static Stream<Arguments> allAdminEndpoints() {
            UUID id = UUID.randomUUID();
            return Stream.of(
                    Arguments.of(HttpMethod.GET, "/api/admin/employees"),
                    Arguments.of(HttpMethod.POST, "/api/admin/employees"),
                    Arguments.of(HttpMethod.PUT, "/api/admin/employees/" + id + "/deactivate"),
                    Arguments.of(HttpMethod.PUT, "/api/admin/employees/" + id + "/activate"),
                    Arguments.of(HttpMethod.GET, "/api/admin/customers"),
                    Arguments.of(HttpMethod.GET, "/api/admin/customers/" + id),
                    Arguments.of(HttpMethod.PUT, "/api/admin/customers/" + id + "/activate"),
                    Arguments.of(HttpMethod.PUT, "/api/admin/customers/" + id + "/deactivate"),
                    Arguments.of(HttpMethod.POST, "/api/admin/customers"),
                    Arguments.of(HttpMethod.GET, "/api/admin/customers/" + id + "/detail"),
                    Arguments.of(HttpMethod.PUT, "/api/admin/customers/" + id + "/profile"),
                    Arguments.of(HttpMethod.GET, "/api/admin/customers/" + id + "/measurements"),
                    Arguments.of(HttpMethod.POST, "/api/admin/customers/" + id + "/measurements"),
                    Arguments.of(HttpMethod.PUT, "/api/admin/customers/" + id + "/measurements/" + UUID.randomUUID()),
                    Arguments.of(HttpMethod.GET, "/api/admin/admins"),
                    Arguments.of(HttpMethod.POST, "/api/admin/admins"),
                    Arguments.of(HttpMethod.PUT, "/api/admin/admins/" + id + "/deactivate"),
                    Arguments.of(HttpMethod.PUT, "/api/admin/admins/" + id + "/activate")
            );
        }

        private MockHttpServletRequestBuilder buildRequest(HttpMethod method, String url) {
            MockHttpServletRequestBuilder builder = switch (method.name()) {
                case "GET" -> MockMvcRequestBuilders.get(url);
                case "POST" -> MockMvcRequestBuilders.post(url).contentType(MediaType.APPLICATION_JSON).content("{}");
                case "PUT" -> MockMvcRequestBuilders.put(url).contentType(MediaType.APPLICATION_JSON).content("{}");
                default -> throw new IllegalArgumentException("Unhandled method: " + method);
            };
            return builder;
        }

        @ParameterizedTest(name = "{0} {1} as EMPLOYEE -> 403")
        @MethodSource("allAdminEndpoints")
        void employeeToken_alwaysForbidden(HttpMethod method, String url) throws Exception {
            mockMvc.perform(buildRequest(method, url).with(asEmployee()))
                    .andExpect(status().isForbidden());
        }

        @ParameterizedTest(name = "{0} {1} as CUSTOMER -> 403")
        @MethodSource("allAdminEndpoints")
        void customerToken_alwaysForbidden(HttpMethod method, String url) throws Exception {
            mockMvc.perform(buildRequest(method, url).with(asCustomer()))
                    .andExpect(status().isForbidden());
        }

        @ParameterizedTest(name = "{0} {1} with no auth -> 403")
        @MethodSource("allAdminEndpoints")
        void noAuth_alwaysForbidden(HttpMethod method, String url) throws Exception {
            // NOTE: JwtFilter passes through unconditionally when the Authorization header
            // is entirely absent (see JwtFilter.doFilterInternal, top guard clause). With no
            // Authentication ever set, SecurityConfig's hasRole("ADMIN") is evaluated against
            // an anonymous principal, and Spring Security's default AuthenticationEntryPoint
            // (Http403ForbiddenEntryPoint) returns 403 — not 401 — since no exceptionHandling()
            // override or httpBasic()/formLogin() is configured. Confirmed against the live
            // server (GET /api/admin/admins, no token -> 403). The BACKEND_HANDOVER_V2.md note
            // about "401, empty body" for Spring Security's own rejections describes a different
            // case (e.g. an invalid/expired token reaching JwtFilter's second guard clause) —
            // that path is not exercised by this particular test and is worth verifying
            // separately if it matters for the report/viva narrative.
            mockMvc.perform(buildRequest(method, url))
                    .andExpect(status().isForbidden());
        }
    }
}