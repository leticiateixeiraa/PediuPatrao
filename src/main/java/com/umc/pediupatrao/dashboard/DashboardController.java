package com.umc.pediupatrao.dashboard;

import com.umc.pediupatrao.dashboard.dto.DashboardCompleto;
import com.umc.pediupatrao.dashboard.dto.PedidoRecente;
import com.umc.pediupatrao.dashboard.dto.ProdutoMaisVendido;
import com.umc.pediupatrao.dashboard.dto.ResumoVendas;
import com.umc.pediupatrao.dashboard.dto.SituacaoPedidos;
import com.umc.pediupatrao.dashboard.dto.VendasPorPeriodo;
import com.umc.pediupatrao.dashboard.dto.VendasPorTipoEntrega;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API do dashboard administrativo (somente leitura, perfis ADMIN e GERENTE).
 *
 * Filtros comuns a todos os endpoints (todos opcionais):
 *   dataInicio, dataFim  -> yyyy-MM-dd (padrao: ultimos 30 dias)
 *   status               -> RECEBIDO, EM_PREPARACAO, PRONTO, SAIU_PARA_ENTREGA, RETIRADO, FINALIZADO, CANCELADO
 *   tipoEntrega          -> ENTREGA ou RETIRADA
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /** Todos os blocos numa chamada so (usado pela tela). */
    @GetMapping
    public DashboardCompleto completo(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) StatusPedido status,
            @RequestParam(required = false) TipoEntrega tipoEntrega,
            @RequestParam(defaultValue = "DIA") Agrupamento agrupamento,
            @RequestParam(defaultValue = "5") int limiteProdutos,
            @RequestParam(defaultValue = "10") int limiteRecentes) {
        return dashboardService.gerarCompleto(new FiltroDashboard(dataInicio, dataFim, status, tipoEntrega),
                agrupamento, limiteProdutos, limiteRecentes);
    }

    /** RF01 - indicadores gerais. */
    @GetMapping("/resumo")
    public ResumoVendas resumo(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) StatusPedido status,
            @RequestParam(required = false) TipoEntrega tipoEntrega) {
        return dashboardService.resumo(new FiltroDashboard(dataInicio, dataFim, status, tipoEntrega));
    }

    /** RF02 - evolucao das vendas por dia, semana ou mes. */
    @GetMapping("/vendas")
    public List<VendasPorPeriodo> vendas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) StatusPedido status,
            @RequestParam(required = false) TipoEntrega tipoEntrega,
            @RequestParam(defaultValue = "DIA") Agrupamento agrupamento) {
        return dashboardService.vendasPorPeriodo(new FiltroDashboard(dataInicio, dataFim, status, tipoEntrega), agrupamento);
    }

    /** RF02 - entrega x retirada. */
    @GetMapping("/vendas-por-tipo")
    public List<VendasPorTipoEntrega> vendasPorTipo(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) StatusPedido status,
            @RequestParam(required = false) TipoEntrega tipoEntrega) {
        return dashboardService.vendasPorTipoEntrega(new FiltroDashboard(dataInicio, dataFim, status, tipoEntrega));
    }

    /** RF03 - quantidade de pedidos por status. */
    @GetMapping("/situacao-pedidos")
    public List<SituacaoPedidos> situacao(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) StatusPedido status,
            @RequestParam(required = false) TipoEntrega tipoEntrega) {
        return dashboardService.situacaoPedidos(new FiltroDashboard(dataInicio, dataFim, status, tipoEntrega));
    }

    /** RF03 - ultimos pedidos. */
    @GetMapping("/pedidos-recentes")
    public List<PedidoRecente> pedidosRecentes(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) StatusPedido status,
            @RequestParam(required = false) TipoEntrega tipoEntrega,
            @RequestParam(defaultValue = "10") int limite) {
        return dashboardService.pedidosRecentes(new FiltroDashboard(dataInicio, dataFim, status, tipoEntrega), limite);
    }

    /** RF04 - ranking de produtos. */
    @GetMapping("/produtos-mais-vendidos")
    public List<ProdutoMaisVendido> produtosMaisVendidos(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) StatusPedido status,
            @RequestParam(required = false) TipoEntrega tipoEntrega,
            @RequestParam(defaultValue = "5") int limite) {
        return dashboardService.produtosMaisVendidos(new FiltroDashboard(dataInicio, dataFim, status, tipoEntrega), limite);
    }
}
