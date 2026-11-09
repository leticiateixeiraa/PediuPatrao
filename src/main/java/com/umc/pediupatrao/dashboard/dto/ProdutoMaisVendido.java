package com.umc.pediupatrao.dashboard.dto;

import java.math.BigDecimal;

/** RF04 - ranking de produtos por quantidade vendida. */
public record ProdutoMaisVendido(int posicao,
                                 String produtoId,
                                 String nome,
                                 long quantidade,
                                 BigDecimal faturamento,
                                 BigDecimal participacaoFaturamento) {
}
