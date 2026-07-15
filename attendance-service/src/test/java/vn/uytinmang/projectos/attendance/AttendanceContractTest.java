package vn.uytinmang.projectos.attendance;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.uytinmang.projectos.attendance.integration.OrganizationClient;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AttendanceContractTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl); registry.add("spring.datasource.username", POSTGRES::getUsername); registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.jwt.secret", () -> "test-secret-that-is-at-least-32-bytes-long");
    }
    @Autowired MockMvc mvc; private final ObjectMapper json = new ObjectMapper(); @MockitoBean OrganizationClient organization;

    @Test void employeeCanCheckInAndOutOncePerScheduledDay() throws Exception {
        UUID organizationId=UUID.randomUUID(), userId=UUID.randomUUID(), employeeId=UUID.randomUUID();
        when(organization.timezone(organizationId)).thenReturn("Asia/Ho_Chi_Minh");
        when(organization.access(organizationId,userId)).thenReturn(new OrganizationClient.Access("Asia/Ho_Chi_Minh","OWNER"));
        when(organization.employeeByUser(organizationId,userId)).thenReturn(new OrganizationClient.Employee(employeeId,organizationId,null,userId,"active"));
        when(organization.employee(any(),any())).thenReturn(new OrganizationClient.Employee(employeeId,organizationId,null,userId,"active"));
        var actor=jwt().jwt(token->token.claim("uid",userId.toString()).claim("role","USER"));
        String base="/api/v1/organizations/"+organizationId+"/attendance";
        String shiftId=id(mvc.perform(post(base+"/shifts").with(actor).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Day\",\"startTime\":\"08:00\",\"endTime\":\"17:00\",\"breakMinutes\":60}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        int day=LocalDate.now().getDayOfWeek().getValue();
        String scheduleId=id(mvc.perform(post(base+"/schedules").with(actor).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Week\",\"slots\":[{\"shiftId\":\""+shiftId+"\",\"dayOfWeek\":"+day+"}]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mvc.perform(post(base+"/assignments").with(actor).contentType(MediaType.APPLICATION_JSON).content("{\"employeeId\":\""+employeeId+"\",\"scheduleId\":\""+scheduleId+"\",\"effectiveFrom\":\""+LocalDate.now()+"\"}")) .andExpect(status().isCreated());
        mvc.perform(post(base+"/check-in").with(actor)).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("open"));
        mvc.perform(post(base+"/check-in").with(actor)).andExpect(status().isConflict());
        mvc.perform(post(base+"/check-out").with(actor)).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("completed"));
        mvc.perform(get(base+"/timesheet").with(actor)).andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(1))
                .andExpect(jsonPath("$.data[0].employeeId").value(employeeId.toString()));

        String adjustmentId=id(mvc.perform(post(base+"/adjustments").with(actor).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workDate\":\""+LocalDate.now()+"\",\"checkInAt\":\""+Instant.now().minusSeconds(60)+"\",\"reason\":\"Correction\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mvc.perform(post(base+"/adjustments/"+adjustmentId+"/decision").with(actor).contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"approve\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("approved"));

        String invalidAdjustmentId=id(mvc.perform(post(base+"/adjustments").with(actor).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workDate\":\""+LocalDate.now()+"\",\"checkInAt\":\""+Instant.now().plusSeconds(3600)+"\",\"reason\":\"Invalid correction\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mvc.perform(post(base+"/adjustments/"+invalidAdjustmentId+"/decision").with(actor).contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"approve\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("invalid_adjustment_time"));
    }
    @Test void employeeOnlySeesSelfWhileManagerCanViewDirectReport() throws Exception {
        UUID organizationId=UUID.randomUUID(), managerUserId=UUID.randomUUID(), managerEmployeeId=UUID.randomUUID(), reportEmployeeId=UUID.randomUUID(), peerEmployeeId=UUID.randomUUID();
        when(organization.timezone(organizationId)).thenReturn("Asia/Ho_Chi_Minh");
        when(organization.access(organizationId,managerUserId)).thenReturn(new OrganizationClient.Access("Asia/Ho_Chi_Minh","MEMBER"));
        when(organization.employeeByUser(organizationId,managerUserId)).thenReturn(new OrganizationClient.Employee(managerEmployeeId,organizationId,null,managerUserId,"active"));
        when(organization.employee(organizationId,reportEmployeeId)).thenReturn(new OrganizationClient.Employee(reportEmployeeId,organizationId,managerEmployeeId,UUID.randomUUID(),"active"));
        when(organization.employee(organizationId,peerEmployeeId)).thenReturn(new OrganizationClient.Employee(peerEmployeeId,organizationId,UUID.randomUUID(),UUID.randomUUID(),"active"));
        var actor=jwt().jwt(token->token.claim("uid",managerUserId.toString()).claim("role","USER"));
        String base="/api/v1/organizations/"+organizationId+"/attendance";

        mvc.perform(get(base+"/scope").with(actor)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.employeeId").value(managerEmployeeId.toString()))
                .andExpect(jsonPath("$.data.organizationAdmin").value(false));
        mvc.perform(get(base+"/timesheet").param("employeeId",reportEmployeeId.toString()).with(actor))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isArray());
        mvc.perform(get(base+"/reports/monthly").param("month",LocalDate.now().toString().substring(0,7)).param("employeeId",reportEmployeeId.toString()).with(actor))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.employeeId").value(reportEmployeeId.toString()));
        mvc.perform(get(base+"/timesheet").param("employeeId",peerEmployeeId.toString()).with(actor))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("attendance_access_denied"));
    }
    private String id(String body) throws Exception { return json.readTree(body).path("data").path("id").asText(); }
}
