package com.umc.pediupatrao.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.umc.pediupatrao.auditoria.AuditoriaService;
import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.service.ClienteService;
import com.umc.pediupatrao.service.PedidoService;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import com.umc.pediupatrao.service.UsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Garante que as restricoes estao no backend: chamadas diretas na API
 * com o perfil errado recebem 403, mesmo sem passar pela tela.
 */
@WebMvcTest(controllers = {PedidoController.class, AuditoriaController.class,
        ClienteController.class, UsuarioController.class})
@Import(SecurityConfig.class)
class SegurancaApiTest {

    private static final String JSON_CANCELAMENTO = "{\"justificativa\":\"Cliente desistiu do pedido\"}";
    private static final String JSON_DESCONTO = "{\"percentual\":10}";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PedidoService pedidoService;
    @MockitoBean
    private AuditoriaService auditoriaService;
    @MockitoBean
    private ClienteService clienteService;
    @MockitoBean
    private UsuarioService usuarioService;
    @MockitoBean
    private UsuarioDetailsService usuarioDetailsService;

    @BeforeEach
    void setUp() {
        when(pedidoService.cancelar(anyString(), anyString())).thenReturn(new Pedido());
        when(pedidoService.aplicarDesconto(anyString(), any(), any())).thenReturn(new Pedido());
        when(auditoriaService.consultar(any(), anyInt(), anyInt())).thenReturn(Page.empty());
    }

    // ---------- sem login ----------

    @Test
    void semLoginRecebe401() throws Exception {
        mvc.perform(get("/api/pedidos")).andExpect(status().isUnauthorized());
    }

    // ---------- cancelamento: so GERENTE ----------

    @Test
    @WithMockUser(username = "atendente01", roles = "ATENDENTE")
    void atendenteNaoCancelaPedido() throws Exception {
        mvc.perform(post("/api/pedidos/1/cancelamento").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(JSON_CANCELAMENTO))
                .andExpect(status().isForbidden());
        verify(pedidoService, never()).cancelar(anyString(), anyString());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminTambemNaoCancelaPedido() throws Exception {
        mvc.perform(post("/api/pedidos/1/cancelamento").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(JSON_CANCELAMENTO))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "gerente", roles = "GERENTE")
    void gerenteCancelaPedido() throws Exception {
        mvc.perform(post("/api/pedidos/1/cancelamento").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(JSON_CANCELAMENTO))
                .andExpect(status().isOk());
    }

    // ---------- desconto: so GERENTE ----------

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminNaoAplicaDesconto() throws Exception {
        mvc.perform(post("/api/pedidos/1/desconto").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(JSON_DESCONTO))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "atendente01", roles = "ATENDENTE")
    void atendenteNaoAplicaDesconto() throws Exception {
        mvc.perform(post("/api/pedidos/1/desconto").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(JSON_DESCONTO))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "gerente", roles = "GERENTE")
    void gerenteAplicaDesconto() throws Exception {
        mvc.perform(post("/api/pedidos/1/desconto").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(JSON_DESCONTO))
                .andExpect(status().isOk());
    }

    // ---------- pedidos ----------

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminSoConsultaPedidos() throws Exception {
        mvc.perform(get("/api/pedidos")).andExpect(status().isOk());
        mvc.perform(post("/api/pedidos").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/pedidos/1/avancar").with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "atendente01", roles = "ATENDENTE")
    void requisicaoSemCsrfEBloqueada() throws Exception {
        mvc.perform(post("/api/pedidos")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    // ---------- auditoria: leitura ADMIN/GERENTE, ninguem altera ----------

    @Test
    @WithMockUser(username = "atendente01", roles = "ATENDENTE")
    void atendenteNaoVeAuditoria() throws Exception {
        mvc.perform(get("/api/auditoria")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "gerente", roles = "GERENTE")
    void gerenteVeAuditoria() throws Exception {
        mvc.perform(get("/api/auditoria")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void ninguemApagaAuditoria() throws Exception {
        mvc.perform(get("/api/auditoria")).andExpect(status().isOk());
        mvc.perform(delete("/api/auditoria/abc").with(csrf())).andExpect(status().isForbidden());
        mvc.perform(put("/api/auditoria/abc").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    // ---------- usuarios e clientes ----------

    @Test
    @WithMockUser(username = "gerente", roles = "GERENTE")
    void gerenteNaoGerenciaUsuarios() throws Exception {
        mvc.perform(get("/api/usuarios")).andExpect(status().isForbidden());
        mvc.perform(post("/api/usuarios").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"hacker\",\"password\":\"123456\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
        verify(usuarioService, never()).salvarUsuario(any());
    }

    @Test
    @WithMockUser(username = "gerente", roles = "GERENTE")
    void gerenteConsultaMasNaoAlteraCliente() throws Exception {
        mvc.perform(get("/api/clientes")).andExpect(status().isOk());
        mvc.perform(put("/api/clientes/1").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"X\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminNaoAcessaClientes() throws Exception {
        mvc.perform(get("/api/clientes")).andExpect(status().isForbidden());
    }
}
