package com.umc.pediupatrao.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.dashboard.dto.DashboardCompleto;
import com.umc.pediupatrao.dashboard.dto.ProdutoMaisVendido;
import com.umc.pediupatrao.dashboard.dto.ResumoVendas;
import com.umc.pediupatrao.dashboard.dto.SituacaoPedidos;
import com.umc.pediupatrao.dashboard.dto.VendasPorPeriodo;
import com.umc.pediupatrao.dashboard.dto.VendasPorTipoEntrega;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.PedidoRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Massa de dados usada (periodo 07/11 a 09/11/2026):
 *  p1 08/11 FINALIZADO  ENTREGA  2x Calabresa + 1x Refri  subtotal 103,80  desconto 10,38  total 93,42
 *  p2 08/11 CANCELADO   RETIRADA 1x Mussarela                               total 42,00
 *  p3 09/11 EM_PREPARACAO RETIRADA 1x Calabresa + 4x Esfiha                 total 71,90
 *  p4 07/11 sem status e sem valor (registro antigo/incompleto)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardServiceTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 11, 7);
    private static final LocalDate FIM = LocalDate.of(2026, 11, 9);

    @Mock
    private PedidoRepository pedidoRepository;

    // 09/11/2026 12:00 em Sao Paulo
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-09T15:00:00Z"), ZoneId.of("America/Sao_Paulo"));

    private DashboardService service;

    @BeforeEach
    void setUp() {
        service = new DashboardService(pedidoRepository, clock);

        Pedido p1 = pedido("p1", LocalDateTime.of(2026, 11, 8, 10, 0), StatusPedido.FINALIZADO, TipoEntrega.ENTREGA,
                "93.42", "10.38",
                item("calabresa", "Pizza Calabresa", 2, "45.90"), item("refri", "Refrigerante 2L", 1, "12.00"));
        Pedido p2 = pedido("p2", LocalDateTime.of(2026, 11, 8, 19, 0), StatusPedido.CANCELADO, TipoEntrega.RETIRADA,
                "42.00", "0",
                item("mussarela", "Pizza Mussarela", 1, "42.00"));
        Pedido p3 = pedido("p3", LocalDateTime.of(2026, 11, 9, 11, 0), StatusPedido.EM_PREPARACAO, TipoEntrega.RETIRADA,
                "71.90", "0",
                item("calabresa", "Pizza Calabresa", 1, "45.90"), item("esfiha", "Esfiha de Carne", 4, "6.50"));
        Pedido p4 = new Pedido();
        p4.setId("p4");
        p4.setDataHoraEntrada(LocalDateTime.of(2026, 11, 7, 9, 0));
        p4.setItens(null);
        p4.setValorTotal(null);

        when(pedidoRepository.buscarPorPeriodoDeEntrada(any(), any()))
                .thenReturn(new ArrayList<>(List.of(p1, p2, p3, p4)));
    }

    // ===================== RF01 =====================

    @Test
    void calculaIndicadoresGerais() {
        ResumoVendas r = service.resumo(new FiltroDashboard(INICIO, FIM, null, null));

        assertEquals(new BigDecimal("165.32"), r.faturamento());       // 93,42 + 71,90 (cancelado fora)
        assertEquals(3, r.totalPedidos());                              // p4 sem status e ignorado
        assertEquals(2, r.pedidosValidos());
        assertEquals(1, r.pedidosCancelados());
        assertEquals(1, r.pedidosEmAndamento());
        assertEquals(new BigDecimal("82.66"), r.ticketMedio());         // 165,32 / 2
        assertEquals(new BigDecimal("33.33"), r.taxaCancelamento());    // 1 de 3
        assertEquals(new BigDecimal("10.38"), r.totalDescontos());
        assertEquals(8, r.itensVendidos());                             // 3 + 5
        assertEquals(1, r.registrosIgnorados());
    }

    @Test
    void semPedidosRetornaZerosSemErro() {
        when(pedidoRepository.buscarPorPeriodoDeEntrada(any(), any())).thenReturn(List.of());

        ResumoVendas r = service.resumo(new FiltroDashboard(INICIO, FIM, null, null));

        assertEquals(0, r.totalPedidos());
        assertEquals(new BigDecimal("0.00"), r.ticketMedio());
        assertEquals(new BigDecimal("0.00"), r.taxaCancelamento());
    }

    // ===================== RF02 =====================

    @Test
    void vendasPorDiaIncluemDiasSemVenda() {
        List<VendasPorPeriodo> vendas = service.vendasPorPeriodo(new FiltroDashboard(INICIO, FIM, null, null), Agrupamento.DIA);

        assertEquals(3, vendas.size());
        assertEquals("07/11", vendas.get(0).rotulo());
        assertEquals(0, vendas.get(0).quantidadePedidos());
        assertEquals(new BigDecimal("93.42"), vendas.get(1).faturamento());
        assertEquals(1, vendas.get(1).quantidadePedidos());             // o cancelado do dia 08 nao conta
        assertEquals(new BigDecimal("71.90"), vendas.get(2).faturamento());
    }

    @Test
    void vendasAgrupadasPorMes() {
        List<VendasPorPeriodo> vendas = service.vendasPorPeriodo(new FiltroDashboard(INICIO, FIM, null, null), Agrupamento.MES);

        assertEquals(1, vendas.size());
        assertEquals("2026-11", vendas.get(0).periodo());
        assertEquals(new BigDecimal("165.32"), vendas.get(0).faturamento());
    }

    @Test
    void comparaEntregaERetirada() {
        List<VendasPorTipoEntrega> tipos = service.vendasPorTipoEntrega(new FiltroDashboard(INICIO, FIM, null, null));

        assertEquals("ENTREGA", tipos.get(0).tipoEntrega());
        assertEquals(new BigDecimal("93.42"), tipos.get(0).faturamento());
        assertEquals(1, tipos.get(1).quantidadePedidos());              // retirada cancelada fora
    }

    // ===================== RF03 =====================

    @Test
    void situacaoTrazTodosOsStatusComPercentual() {
        List<SituacaoPedidos> situacao = service.situacaoPedidos(new FiltroDashboard(INICIO, FIM, null, null));

        assertEquals(StatusPedido.values().length, situacao.size());
        SituacaoPedidos cancelado = situacao.stream().filter(s -> s.status().equals("CANCELADO")).findFirst().orElseThrow();
        assertEquals(1, cancelado.quantidade());
        assertEquals(new BigDecimal("33.33"), cancelado.percentual());
        SituacaoPedidos pronto = situacao.stream().filter(s -> s.status().equals("PRONTO")).findFirst().orElseThrow();
        assertEquals(0, pronto.quantidade());
    }

    @Test
    void pedidosRecentesOrdenadosDoMaisNovo() {
        var recentes = service.pedidosRecentes(new FiltroDashboard(INICIO, FIM, null, null), 2);

        assertEquals(2, recentes.size());
        assertEquals("p3", recentes.get(0).id());
        assertEquals("p2", recentes.get(1).id());
    }

    // ===================== RF04 =====================

    @Test
    void rankingDeProdutosPorQuantidade() {
        List<ProdutoMaisVendido> ranking = service.produtosMaisVendidos(new FiltroDashboard(INICIO, FIM, null, null), 5);

        assertEquals(3, ranking.size());                                // mussarela so no cancelado
        assertEquals("Esfiha de Carne", ranking.get(0).nome());
        assertEquals(4, ranking.get(0).quantidade());
        assertEquals("Pizza Calabresa", ranking.get(1).nome());
        assertEquals(3, ranking.get(1).quantidade());
        assertEquals(new BigDecimal("137.70"), ranking.get(1).faturamento());
        assertEquals(new BigDecimal("78.37"), ranking.get(1).participacaoFaturamento());
        assertEquals(1, ranking.get(0).posicao());
    }

    // ===================== RF05 =====================

    @Test
    void filtroPorStatus() {
        ResumoVendas r = service.resumo(new FiltroDashboard(INICIO, FIM, StatusPedido.CANCELADO, null));

        assertEquals(1, r.totalPedidos());
        assertEquals(new BigDecimal("0.00"), r.faturamento());
        assertEquals(new BigDecimal("100.00"), r.taxaCancelamento());
    }

    @Test
    void filtroPorTipoDeEntrega() {
        ResumoVendas r = service.resumo(new FiltroDashboard(INICIO, FIM, null, TipoEntrega.RETIRADA));

        assertEquals(2, r.totalPedidos());
        assertEquals(new BigDecimal("71.90"), r.faturamento());
    }

    @Test
    void semDatasUsaUltimos30Dias() {
        service.resumo(FiltroDashboard.vazio());

        verify(pedidoRepository).buscarPorPeriodoDeEntrada(
                LocalDate.of(2026, 10, 11).atStartOfDay(),
                LocalDate.of(2026, 11, 10).atStartOfDay());
    }

    @Test
    void dataInicialDepoisDaFinalEhRecusada() {
        assertThrows(RegraNegocioException.class,
                () -> service.resumo(new FiltroDashboard(FIM, INICIO, null, null)));
    }

    @Test
    void periodoMaiorQueUmAnoEhRecusado() {
        assertThrows(RegraNegocioException.class,
                () -> service.resumo(new FiltroDashboard(LocalDate.of(2024, 1, 1), FIM, null, null)));
    }

    @Test
    void limiteForaDoIntervaloEhRecusado() {
        assertThrows(RegraNegocioException.class,
                () -> service.produtosMaisVendidos(FiltroDashboard.vazio(), 0));
        assertThrows(RegraNegocioException.class,
                () -> service.pedidosRecentes(FiltroDashboard.vazio(), 51));
    }

    // ===================== RF07 / completo =====================

    @Test
    void dashboardCompletoReuneTodosOsBlocos() {
        DashboardCompleto d = service.gerarCompleto(new FiltroDashboard(INICIO, FIM, null, null), Agrupamento.DIA, 5, 10);

        assertEquals(LocalDateTime.of(2026, 11, 9, 12, 0), d.geradoEm());
        assertEquals(3, d.vendasPorPeriodo().size());
        assertEquals(4, d.pedidosRecentes().size());
        assertTrue(d.pedidosRecentes().stream().anyMatch(p -> "Indefinido".equals(p.statusDescricao())));
    }

    // ===================== auxiliares =====================

    private static ItemPedido item(String id, String nome, int qtd, String preco) {
        return new ItemPedido(id, nome, qtd, new BigDecimal(preco));
    }

    private static Pedido pedido(String id, LocalDateTime entrada, StatusPedido status, TipoEntrega tipo,
                                 String total, String desconto, ItemPedido... itens) {
        Pedido p = new Pedido();
        p.setId(id);
        p.setClienteNome("Cliente " + id);
        p.setDataHoraEntrada(entrada);
        p.setStatus(status);
        p.setTipoEntrega(tipo);
        p.setItens(new ArrayList<>(List.of(itens)));
        p.setValorTotal(new BigDecimal(total));
        p.setValorDesconto(new BigDecimal(desconto));
        p.setRegistradoPor("atendente01");
        return p;
    }
}
