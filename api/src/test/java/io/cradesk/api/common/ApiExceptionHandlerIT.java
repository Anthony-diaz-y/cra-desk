package io.cradesk.api.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import io.cradesk.api.support.IntegrationTest;
import io.cradesk.api.support.TestTenants;

@IntegrationTest
@Transactional
class ApiExceptionHandlerIT {

    @Autowired
    TestTenants tenants;

    MockMvc mvc;

    // Inicia sesión como miembro de una empresa nueva antes de cada test.
    @BeforeEach
    void signIn() {
        mvc = tenants.mvcFor(tenants.create("Acme"));
    }

    // Comprueba que un error inesperado responde 500 sin mostrar detalles internos.
    @Test
    void unexpectedErrorReturns500ProblemWithoutInternals() throws Exception {
        String body = mvc.perform(get("/api/test-only/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Unexpected error"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("secret internal detail");
    }
}
