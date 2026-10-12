package com.example.paymentservice.acquirer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MockAcquirerController.class)
class MockAcquirerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldApproveWithUuidReference_whenAmountIsNotSpecialCased() throws Exception {
        mockMvc.perform(post("/mock-acquirer/authorizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 1000, "currency": "USD"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.reference").isNotEmpty());
    }

    @Test
    void shouldDecline_whenAmountIs6666() throws Exception {
        mockMvc.perform(post("/mock-acquirer/authorizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 6666, "currency": "USD"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLINED"));
    }

    @Test
    void shouldReturnServerError_whenAmountIs9999() throws Exception {
        mockMvc.perform(post("/mock-acquirer/authorizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 9999, "currency": "USD"}
                                """))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void shouldApproveCapture_whenAmountIsNotSpecialCased() throws Exception {
        mockMvc.perform(post("/mock-acquirer/captures")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 500}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }
}
