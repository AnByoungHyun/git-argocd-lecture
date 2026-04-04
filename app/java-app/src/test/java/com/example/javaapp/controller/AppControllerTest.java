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
    // GET / — 기본 정보 응답 테스트
    // ----------------------------------------------------------------
    @Test
    void getRoot_returns200WithAppInfo() throws Exception {
        mockMvc.perform(get("/")
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
    // GET /health — 헬스체크 응답 테스트
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
    // 404 — 존재하지 않는 경로 테스트
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
    // 405 — 허용되지 않는 메서드 테스트
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
