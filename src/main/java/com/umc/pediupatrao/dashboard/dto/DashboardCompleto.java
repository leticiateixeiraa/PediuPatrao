package com.umc.pediupatrao.dashboard.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Tudo que a tela precisa numa unica chamada (evita 6 requisicoes a cada atualizacao).
 * Os mesmos blocos tambem estao disponiveis em endpoints separados.
 */
public record DashboardCompleto(LocalDateTime geradoEm,
                                ResumoVendas resumo,
                                List<VendasPorPeriodo> vendasPorPeriodo,
                                List<SituacaoPedidos> situacaoPedidos,
                                List<VendasPorTipoEntrega> vendasPorTipoEntrega,
                                List<ProdutoMaisVendido> produtosMaisVendidos,
                                List<PedidoRecente> pedidosRecentes) {
}
