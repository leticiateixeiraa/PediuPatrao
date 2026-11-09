package com.umc.pediupatrao.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * RF01 - indicadores gerais. Valores monetarios em reais.
 * faturamento, ticketMedio e itensVendidos desconsideram pedidos cancelados.
 */
public record ResumoVendas(LocalDate dataInicio,
                           LocalDate dataFim,
                           BigDecimal faturamento,
                           long totalPedidos,
                           long pedidosValidos,
                           long pedidosCancelados,
                           long pedidosEmAndamento,
                           BigDecimal ticketMedio,
                           BigDecimal taxaCancelamento,
                           BigDecimal totalDescontos,
                           long itensVendidos,
                           long registrosIgnorados) {
}
