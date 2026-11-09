package com.umc.pediupatrao.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record DashboardResponse(
        LocalDate inicio,
        LocalDate fim,
        Indicadores indicadores,
        List<StatusResumo> pedidosPorStatus,
        List<VendaPorPeriodo> vendasPorPeriodo,
        List<ProdutoVendido> produtosMaisVendidos,
        List<PedidoRecente> pedidosRecentes) {

    public record Indicadores(
            long totalPedidos,
            long pedidosEmAndamento,
            long pedidosCancelados,
            long pedidosFinalizados,
            BigDecimal faturamento,
            BigDecimal ticketMedio) {
    }

    public record StatusResumo(String status, String descricao, long quantidade) {
    }

    public record VendaPorPeriodo(String periodo, BigDecimal faturamento, long quantidadePedidos) {
    }

    public record ProdutoVendido(
            String produtoId,
            String nomeProduto,
            long quantidadeVendida) {
    }

    public record PedidoRecente(
            String id,
            String clienteNome,
            String status,
            String statusDescricao,
            BigDecimal valorTotal,
            LocalDateTime dataHoraEntrada) {
    }
}
