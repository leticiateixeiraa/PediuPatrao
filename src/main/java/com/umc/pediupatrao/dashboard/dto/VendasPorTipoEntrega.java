package com.umc.pediupatrao.dashboard.dto;

import java.math.BigDecimal;

/** RF02 - comparacao entre pedidos para entrega e para retirada. */
public record VendasPorTipoEntrega(String tipoEntrega,
                                   long quantidadePedidos,
                                   BigDecimal faturamento) {
}
