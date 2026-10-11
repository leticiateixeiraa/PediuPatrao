package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.auditoria.AuditoriaService;
import com.umc.pediupatrao.auditoria.TipoOperacao;
import com.umc.pediupatrao.dto.ItemPedidoRequest;
import com.umc.pediupatrao.dto.PedidoRequest;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.exception.TransicaoInvalidaException;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.security.UsuarioLogado;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PedidoServiceTest {

    @Mock
    private PedidoRepository pedidoRepository;
    @Mock
    private ProdutoRepository produtoRepository;
    @Mock
    private ClienteRepository clienteRepository;
    @Mock
    private UsuarioLogado usuarioLogado;
    @Mock
    private AuditoriaService auditoriaService;

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-23T17:35:00Z"), ZoneId.of("America/Sao_Paulo"));

    private PedidoService service;

    @BeforeEach
    void setUp() {
        service = new PedidoService(pedidoRepository, produtoRepository, clienteRepository,
                usuarioLogado, auditoriaService, clock);

        when(usuarioLogado.username()).thenReturn("atendente01");
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> {
            Pedido p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId("pedido-1");
            }
            return p;
        });

        Cliente cliente = new Cliente();
        cliente.setId("cli-1");
        cliente.setNome("Maria");
        cliente.setLogradouro("Rua das Flores");
        when(clienteRepository.findById("cli-1")).thenReturn(Optional.of(cliente));

        when(produtoRepository.findById("pizza")).thenReturn(Optional.of(produto("pizza", "Calabresa", 45.90)));
        when(produtoRepository.findById("refri")).thenReturn(Optional.of(produto("refri", "Refrigerante 2L", 12.00)));
    }

    // ===================== RF02 =====================

    @Test
    void calculaTotalNoServidorComBaseNosProdutos() {
        Pedido pedido = service.criarPedido(new PedidoRequest("cli-1", TipoEntrega.ENTREGA, null,
                List.of(new ItemPedidoRequest("pizza", 2), new ItemPedidoRequest("refri", 1))));

        // 2 x 45,90 + 12,00 = 103,80
        assertEquals(new BigDecimal("103.80"), pedido.getValorTotal());
        assertEquals(new BigDecimal("103.80"), pedido.getSubtotal());
        assertEquals(new BigDecimal("91.80"), pedido.getItens().get(0).getSubtotal());
        assertEquals(StatusPedido.RECEBIDO, pedido.getStatus());
        assertEquals("atendente01", pedido.getRegistradoPor());
        assertEquals(LocalDateTime.of(2026, 9, 23, 14, 35), pedido.getDataHoraEntrada());
        verify(auditoriaService).registrar(eq(TipoOperacao.INCLUSAO_PEDIDO), eq("PEDIDO"), eq("pedido-1"), any(), isNull());
    }

    @Test
    void naoAceitaPedidoSemItens() {
        assertThrows(RegraNegocioException.class, () ->
                service.criarPedido(new PedidoRequest("cli-1", TipoEntrega.RETIRADA, null, List.of())));
    }

    @Test
    void naoAceitaQuantidadeInvalida() {
        assertThrows(RegraNegocioException.class, () ->
                service.criarPedido(new PedidoRequest("cli-1", TipoEntrega.RETIRADA, null,
                        List.of(new ItemPedidoRequest("pizza", 0)))));
        assertThrows(RegraNegocioException.class, () ->
                service.criarPedido(new PedidoRequest("cli-1", TipoEntrega.RETIRADA, null,
                        List.of(new ItemPedidoRequest("pizza", 51)))));
    }

    @Test
    void naoAceitaProdutoInativo() {
        Produto inativo = produto("velho", "Pizza antiga", 30.0);
        inativo.setAtivo(false);
        when(produtoRepository.findById("velho")).thenReturn(Optional.of(inativo));

        assertThrows(RegraNegocioException.class, () ->
                service.criarPedido(new PedidoRequest("cli-1", TipoEntrega.RETIRADA, null,
                        List.of(new ItemPedidoRequest("velho", 1)))));
    }

    // ===================== RF03 =====================

    @Test
    void naoPermiteFinalizarPedidoEmPreparacao() {
        Pedido pedido = pedidoNoStatus(StatusPedido.EM_PREPARACAO);

        assertThrows(TransicaoInvalidaException.class,
                () -> service.atualizarStatus(pedido.getId(), StatusPedido.FINALIZADO));
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void saidaRegistraDataHoraEResponsavel() {
        Pedido pedido = pedidoNoStatus(StatusPedido.PRONTO);
        when(usuarioLogado.username()).thenReturn("gerente");

        Pedido atualizado = service.atualizarStatus(pedido.getId(), StatusPedido.SAIU_PARA_ENTREGA);

        assertEquals(StatusPedido.SAIU_PARA_ENTREGA, atualizado.getStatus());
        assertEquals("gerente", atualizado.getSaidaRegistradaPor());
        assertNotNull(atualizado.getDataHoraSaida());
        verify(auditoriaService).registrar(eq(TipoOperacao.MUDANCA_STATUS_PEDIDO), eq("PEDIDO"), eq("p-9"), any(), isNull());
    }

    @Test
    void naoPermiteAlterarStatusDePedidoCancelado() {
        Pedido pedido = pedidoNoStatus(StatusPedido.CANCELADO);

        assertThrows(TransicaoInvalidaException.class,
                () -> service.atualizarStatus(pedido.getId(), StatusPedido.EM_PREPARACAO));
    }

    @Test
    void statusCanceladoNaoPodeSerUsadoNaTransicaoComum() {
        Pedido pedido = pedidoNoStatus(StatusPedido.RECEBIDO);

        assertThrows(RegraNegocioException.class,
                () -> service.atualizarStatus(pedido.getId(), StatusPedido.CANCELADO));
    }

    // ===================== RF04 =====================

    @Test
    void cancelamentoExigeJustificativa() {
        Pedido pedido = pedidoNoStatus(StatusPedido.EM_PREPARACAO);

        assertThrows(RegraNegocioException.class, () -> service.cancelar(pedido.getId(), "  "));
        assertThrows(RegraNegocioException.class, () -> service.cancelar(pedido.getId(), "curto"));
    }

    @Test
    void cancelaEAuditaComJustificativa() {
        Pedido pedido = pedidoNoStatus(StatusPedido.PRONTO);
        when(usuarioLogado.username()).thenReturn("gerente");

        Pedido cancelado = service.cancelar(pedido.getId(), "Cliente desistiu do pedido por telefone");

        assertEquals(StatusPedido.CANCELADO, cancelado.getStatus());
        assertEquals(StatusPedido.PRONTO, cancelado.getStatusAntesDoCancelamento());
        assertEquals("gerente", cancelado.getCanceladoPor());
        // dados historicos preservados
        assertEquals(1, cancelado.getItens().size());
        verify(auditoriaService).registrar(eq(TipoOperacao.CANCELAMENTO_PEDIDO), eq("PEDIDO"), eq("p-9"), any(),
                eq("Cliente desistiu do pedido por telefone"));
    }

    @Test
    void naoCancelaDepoisDaSaida() {
        Pedido pedido = pedidoNoStatus(StatusPedido.SAIU_PARA_ENTREGA);

        assertThrows(TransicaoInvalidaException.class,
                () -> service.cancelar(pedido.getId(), "Tentando cancelar depois da saida"));
    }

    @Test
    void descontoLimitadoA20PorCento() {
        Pedido pedido = pedidoNoStatus(StatusPedido.RECEBIDO);

        assertThrows(RegraNegocioException.class,
                () -> service.aplicarDesconto(pedido.getId(), new BigDecimal("20.01"), null));
        assertThrows(RegraNegocioException.class,
                () -> service.aplicarDesconto(pedido.getId(), BigDecimal.ZERO, null));
        assertThrows(RegraNegocioException.class,
                () -> service.aplicarDesconto(pedido.getId(), new BigDecimal("-5"), null));
    }

    @Test
    void aplicaDescontoERecalculaTotal() {
        Pedido pedido = pedidoNoStatus(StatusPedido.EM_PREPARACAO); // subtotal 100,00
        when(usuarioLogado.username()).thenReturn("gerente");

        Pedido comDesconto = service.aplicarDesconto(pedido.getId(), new BigDecimal("10"), null);

        assertEquals(new BigDecimal("10.00"), comDesconto.getValorDesconto());
        assertEquals(new BigDecimal("90.00"), comDesconto.getValorTotal());
        assertEquals("gerente", comDesconto.getDescontoAplicadoPor());
        verify(auditoriaService).registrar(eq(TipoOperacao.DESCONTO_PEDIDO), eq("PEDIDO"), eq("p-9"), any(), isNull());
    }

    @Test
    void novoDescontoSubstituiOAnteriorSemAcumular() {
        Pedido pedido = pedidoNoStatus(StatusPedido.RECEBIDO);

        service.aplicarDesconto(pedido.getId(), new BigDecimal("15"), null);
        Pedido depois = service.aplicarDesconto(pedido.getId(), new BigDecimal("20"), null);

        assertEquals(new BigDecimal("80.00"), depois.getValorTotal());
    }

    @Test
    void naoDaDescontoDepoisDaSaida() {
        Pedido pedido = pedidoNoStatus(StatusPedido.RETIRADO);

        assertThrows(TransicaoInvalidaException.class,
                () -> service.aplicarDesconto(pedido.getId(), new BigDecimal("5"), null));
    }

    // ===================== auxiliares =====================

    private Produto produto(String id, String nome, double preco) {
        Produto p = new Produto();
        p.setId(id);
        p.setNome(nome);
        p.setPreco(preco);
        p.setAtivo(true);
        return p;
    }

    /** Pedido com um item de R$ 100,00 no status informado. */
    private Pedido pedidoNoStatus(StatusPedido status) {
        Pedido pedido = new Pedido();
        pedido.setId("p-9");
        pedido.setClienteId("cli-1");
        pedido.setTipoEntrega(TipoEntrega.ENTREGA);
        pedido.setItens(new ArrayList<>(List.of(new ItemPedido("pizza", "Especial", 1, new BigDecimal("100.00")))));
        pedido.setSubtotal(new BigDecimal("100.00"));
        pedido.setValorTotal(new BigDecimal("100.00"));
        pedido.setStatus(status);
        when(pedidoRepository.findById(anyString())).thenReturn(Optional.of(pedido));
        return pedido;
    }
}
