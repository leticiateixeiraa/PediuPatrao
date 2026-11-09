package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.dto.DashboardResponse;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.PedidoRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 9, 23, 12, 0);

    @Mock
    private PedidoRepository pedidoRepository;

    private DashboardService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                AGORA.atZone(ZoneId.of("America/Sao_Paulo")).toInstant(),
                ZoneId.of("America/Sao_Paulo"));
        service = new DashboardService(pedidoRepository, clock);
    }

    @Test
    void agregaIndicadoresStatusVendasEProdutosNoPeriodoPadrao() {
        Pedido finalizado = pedido("p-1", "Ana", StatusPedido.FINALIZADO,
                LocalDateTime.of(2026, 9, 2, 10, 0), "80.00");
        finalizado.setItens(List.of(new ItemPedido("pizza", "Pizza", 2, new BigDecimal("40.00"))));
        Pedido cancelado = pedido("p-2", "Bia", StatusPedido.CANCELADO,
                LocalDateTime.of(2026, 9, 3, 10, 0), "30.00");
        Pedido recebido = pedido("p-3", "Caio", StatusPedido.RECEBIDO,
                LocalDateTime.of(2026, 9, 4, 10, 0), "25.00");
        when(pedidoRepository.buscarPorDataHoraEntradaNoIntervalo(
                LocalDateTime.of(2026, 9, 1, 0, 0), LocalDateTime.of(2026, 9, 24, 0, 0)))
                .thenReturn(List.of(finalizado, cancelado, recebido));

        DashboardResponse response = service.consultar(null, null);

        assertEquals(3, response.indicadores().totalPedidos());
        assertEquals(1, response.indicadores().pedidosEmAndamento());
        assertEquals(1, response.indicadores().pedidosCancelados());
        assertEquals(1, response.indicadores().pedidosFinalizados());
        assertEquals(new BigDecimal("80.00"), response.indicadores().faturamento());
        assertEquals(new BigDecimal("80.00"), response.indicadores().ticketMedio());
        assertEquals(23, response.vendasPorPeriodo().size());
        assertEquals("02/09", response.vendasPorPeriodo().get(1).periodo());
        assertEquals(new BigDecimal("80.00"), response.vendasPorPeriodo().get(1).faturamento());
        assertEquals(2, response.produtosMaisVendidos().get(0).quantidadeVendida());
        assertEquals(1, response.pedidosPorStatus().stream()
                .filter(status -> status.status().equals("CANCELADO"))
                .findFirst().orElseThrow().quantidade());
        assertEquals("p-3", response.pedidosRecentes().get(0).id());
    }

    @Test
    void agrupaPorMesEmPeriodosLongos() {
        when(pedidoRepository.buscarPorDataHoraEntradaNoIntervalo(
                LocalDateTime.of(2026, 8, 1, 0, 0), LocalDateTime.of(2026, 10, 1, 0, 0)))
                .thenReturn(List.of(pedido("p-1", "Ana", StatusPedido.FINALIZADO, AGORA, "80.00")));

        DashboardResponse response = service.consultar(
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 30));

        assertEquals(2, response.vendasPorPeriodo().size());
        assertEquals("08/26", response.vendasPorPeriodo().get(0).periodo());
        assertEquals("09/26", response.vendasPorPeriodo().get(1).periodo());
        assertEquals(1, response.vendasPorPeriodo().get(1).quantidadePedidos());
    }

    @Test
    void rejeitaPeriodoComInicioPosteriorAoFim() {
        assertThrows(RegraNegocioException.class,
                () -> service.consultar(LocalDate.of(2026, 9, 24), LocalDate.of(2026, 9, 1)));
    }

    private static Pedido pedido(
            String id, String cliente, StatusPedido status, LocalDateTime data, String total) {
        Pedido pedido = new Pedido();
        pedido.setId(id);
        pedido.setClienteNome(cliente);
        pedido.setStatus(status);
        pedido.setDataHoraEntrada(data);
        pedido.setValorTotal(new BigDecimal(total));
        return pedido;
    }
}
