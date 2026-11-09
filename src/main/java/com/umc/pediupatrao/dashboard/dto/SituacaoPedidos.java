package com.umc.pediupatrao.dashboard.dto;

import java.math.BigDecimal;

/** RF03 - quantidade de pedidos em cada status. */
public record SituacaoPedidos(String status,
                              String descricao,
                              long quantidade,
                              BigDecimal percentual) {
}
