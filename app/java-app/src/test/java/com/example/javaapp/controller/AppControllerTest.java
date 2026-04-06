package com.example.javaapp.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AppControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // ----------------------------------------------------------------
    // GET / — HTML 웹 페이지 테스트 (신규)
    // ----------------------------------------------------------------
    @Test
    void getRoot_returns200WithHtml() throws Exception {
        mockMvc.perform(get("/")
                .accept(MediaType.TEXT_HTML))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("java-app")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Running")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Spring Boot 3.x")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("#FF6B35")));
    }

    // ----------------------------------------------------------------
    // GET /api — JSON 응답 테스트 (GET / 에서 이동)
    // ----------------------------------------------------------------
    @Test
    void getApi_returns200WithAppInfo() throws Exception {
        mockMvc.perform(get("/api")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.app").value("java-app"))
            .andExpect(jsonPath("$.language").value("Java"))
            .andExpect(jsonPath("$.framework").value("Spring Boot 3.x"))
            .andExpect(jsonPath("$.port").value(8080))
            .andExpect(jsonPath("$.version").exists())
            .andExpect(jsonPath("$.environment").exists());
    }

    // ----------------------------------------------------------------
    // GET /health — 헬스체크 응답 테스트 (변경 없음)
    // ----------------------------------------------------------------
    @Test
    void getHealth_returns200WithStatusOk() throws Exception {
        mockMvc.perform(get("/health")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.status").value("ok"))
            .andExpect(jsonPath("$.app").value("java-app"))
            .andExpect(jsonPath("$.version").exists());
    }

    // ----------------------------------------------------------------
    // 404 — 존재하지 않는 경로 테스트 (변경 없음)
    // ----------------------------------------------------------------
    @Test
    void unknownPath_returns404WithErrorFormat() throws Exception {
        mockMvc.perform(get("/unknown-path")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value("error"))
            .andExpect(jsonPath("$.code").value(404))
            .andExpect(jsonPath("$.message").value("Not Found"))
            .andExpect(jsonPath("$.path").value("/unknown-path"));
    }

    // ----------------------------------------------------------------
    // 405 — POST / → Method Not Allowed 테스트 (변경 없음)
    // ----------------------------------------------------------------
    @Test
    void postRoot_returns405WithErrorFormat() throws Exception {
        mockMvc.perform(post("/")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(jsonPath("$.status").value("error"))
            .andExpect(jsonPath("$.code").value(405))
            .andExpect(jsonPath("$.message").value("Method Not Allowed"));
    }
}
