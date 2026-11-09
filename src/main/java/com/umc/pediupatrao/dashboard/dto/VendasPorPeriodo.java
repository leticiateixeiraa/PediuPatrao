package com.umc.pediupatrao.dashboard.dto;

import java.math.BigDecimal;

/** RF02 - um ponto do grafico de evolucao das vendas. */
public record VendasPorPeriodo(String periodo,
                               String rotulo,
                               long quantidadePedidos,
                               BigDecimal faturamento) {
}
