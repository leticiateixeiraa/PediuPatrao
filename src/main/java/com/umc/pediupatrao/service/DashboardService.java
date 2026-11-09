package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.DashboardResponse;
import com.umc.pediupatrao.dto.DashboardResponse.Indicadores;
import com.umc.pediupatrao.dto.DashboardResponse.PedidoRecente;
import com.umc.pediupatrao.dto.DashboardResponse.ProdutoVendido;
import com.umc.pediupatrao.dto.DashboardResponse.StatusResumo;
import com.umc.pediupatrao.dto.DashboardResponse.VendaPorPeriodo;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.PedidoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final DateTimeFormatter ROTULO_DIA = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter ROTULO_MES = DateTimeFormatter.ofPattern("MM/yy");

    private final PedidoRepository pedidoRepository;
    private final Clock clock;

    public DashboardService(PedidoRepository pedidoRepository, Clock clock) {
        this.pedidoRepository = pedidoRepository;
        this.clock = clock;
    }

    public DashboardResponse consultar(LocalDate inicio, LocalDate fim) {
        LocalDate hoje = LocalDate.now(clock);
        LocalDate dataInicio = inicio != null ? inicio : YearMonth.from(hoje).atDay(1);
        LocalDate dataFim = fim != null ? fim : hoje;

        if (dataInicio.isAfter(dataFim)) {
            throw new RegraNegocioException("A data inicial não pode ser posterior à data final.");
        }

        List<Pedido> pedidos = pedidoRepository
                .buscarPorDataHoraEntradaNoIntervalo(
                        dataInicio.atStartOfDay(), dataFim.plusDays(1).atStartOfDay());

        return montarResposta(dataInicio, dataFim, pedidos);
    }

    private DashboardResponse montarResposta(LocalDate inicio, LocalDate fim, List<Pedido> pedidos) {
        Map<StatusPedido, Long> quantidadesPorStatus = new EnumMap<>(StatusPedido.class);
        for (StatusPedido status : StatusPedido.values()) {
            quantidadesPorStatus.put(status, 0L);
        }

        Map<String, AcumuladoProduto> produtos = new HashMap<>();
        Map<LocalDate, AcumuladoVenda> vendasPorDia = new TreeMap<>();
        Map<YearMonth, AcumuladoVenda> vendasPorMes = new TreeMap<>();
        BigDecimal faturamento = ZERO;
        long finalizados = 0;
        long emAndamento = 0;
        long cancelados = 0;

        for (Pedido pedido : pedidos) {
            StatusPedido status = pedido.getStatus();
            if (status != null) {
                quantidadesPorStatus.compute(status, (chave, quantidade) -> quantidade + 1);
                if (status == StatusPedido.CANCELADO) {
                    cancelados++;
                } else if (status == StatusPedido.FINALIZADO) {
                    finalizados++;
                } else {
                    emAndamento++;
                }
            }

            if (status != StatusPedido.FINALIZADO) {
                continue;
            }

            BigDecimal totalPedido = valorOuZero(pedido.getValorTotal());
            faturamento = faturamento.add(totalPedido);
            LocalDate dataPedido = pedido.getDataHoraEntrada().toLocalDate();
            if (diasNoPeriodo(inicio, fim) <= 31) {
                vendasPorDia.computeIfAbsent(dataPedido, chave -> new AcumuladoVenda())
                        .adicionar(totalPedido);
            } else {
                vendasPorMes.computeIfAbsent(YearMonth.from(dataPedido), chave -> new AcumuladoVenda())
                        .adicionar(totalPedido);
            }

            if (pedido.getItens() == null) {
                continue;
            }
            for (ItemPedido item : pedido.getItens()) {
                if (item == null || item.getQuantidade() <= 0) {
                    continue;
                }
                String chaveProduto = item.getProdutoId() != null
                        ? item.getProdutoId()
                        : item.getNomeProduto();
                if (chaveProduto == null || chaveProduto.isBlank()) {
                    continue;
                }
                AcumuladoProduto acumulado = produtos.computeIfAbsent(chaveProduto,
                        chave -> new AcumuladoProduto(
                                item.getProdutoId(), nomeProduto(item, chave)));
                acumulado.quantidade += item.getQuantidade();
            }
        }

        List<StatusResumo> statusResumo = new ArrayList<>();
        for (StatusPedido status : StatusPedido.values()) {
            statusResumo.add(new StatusResumo(
                    status.name(), status.getDescricao(), quantidadesPorStatus.get(status)));
        }

        List<ProdutoVendido> rankingProdutos = produtos.values().stream()
                .sorted(Comparator.comparingLong((AcumuladoProduto p) -> p.quantidade).reversed()
                        .thenComparing(p -> p.nome, String.CASE_INSENSITIVE_ORDER))
                .limit(5)
                .map(p -> new ProdutoVendido(p.id, p.nome, p.quantidade))
                .toList();

        BigDecimal ticketMedio = finalizados == 0
                ? ZERO
                : faturamento.divide(BigDecimal.valueOf(finalizados), 2, RoundingMode.HALF_UP);
        Indicadores indicadores = new Indicadores(
                pedidos.size(), emAndamento, cancelados, finalizados, moeda(faturamento), ticketMedio);

        return new DashboardResponse(
                inicio,
                fim,
                indicadores,
                statusResumo,
                montarSerie(inicio, fim, vendasPorDia, vendasPorMes),
                rankingProdutos,
                pedidos.stream()
                        .sorted(Comparator.comparing(
                                Pedido::getDataHoraEntrada,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                        .limit(5)
                        .map(this::pedidoRecente)
                        .toList());
    }

    private List<VendaPorPeriodo> montarSerie(
            LocalDate inicio,
            LocalDate fim,
            Map<LocalDate, AcumuladoVenda> vendasPorDia,
            Map<YearMonth, AcumuladoVenda> vendasPorMes) {
        List<VendaPorPeriodo> serie = new ArrayList<>();
        if (diasNoPeriodo(inicio, fim) <= 31) {
            for (LocalDate dia = inicio; !dia.isAfter(fim); dia = dia.plusDays(1)) {
                AcumuladoVenda venda = vendasPorDia.getOrDefault(dia, new AcumuladoVenda());
                serie.add(new VendaPorPeriodo(
                        dia.format(ROTULO_DIA), moeda(venda.faturamento), venda.quantidadePedidos));
            }
            return serie;
        }

        for (YearMonth mes = YearMonth.from(inicio);
                !mes.isAfter(YearMonth.from(fim));
                mes = mes.plusMonths(1)) {
            AcumuladoVenda venda = vendasPorMes.getOrDefault(mes, new AcumuladoVenda());
            serie.add(new VendaPorPeriodo(
                    mes.format(ROTULO_MES), moeda(venda.faturamento), venda.quantidadePedidos));
        }
        return serie;
    }

    private PedidoRecente pedidoRecente(Pedido pedido) {
        StatusPedido status = pedido.getStatus();
        return new PedidoRecente(
                pedido.getId(),
                pedido.getClienteNome(),
                status != null ? status.name() : null,
                status != null ? status.getDescricao() : "Sem status",
                moeda(valorOuZero(pedido.getValorTotal())),
                pedido.getDataHoraEntrada());
    }

    private static long diasNoPeriodo(LocalDate inicio, LocalDate fim) {
        return fim.toEpochDay() - inicio.toEpochDay() + 1;
    }

    private static String nomeProduto(ItemPedido item, String chave) {
        return item.getNomeProduto() == null || item.getNomeProduto().isBlank()
                ? chave
                : item.getNomeProduto();
    }

    private static BigDecimal valorOuZero(BigDecimal valor) {
        return valor != null ? valor : BigDecimal.ZERO;
    }

    private static BigDecimal moeda(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    private static final class AcumuladoVenda {
        private BigDecimal faturamento = BigDecimal.ZERO;
        private long quantidadePedidos;

        private void adicionar(BigDecimal valor) {
            faturamento = faturamento.add(valor);
            quantidadePedidos++;
        }
    }

    private static final class AcumuladoProduto {
        private final String id;
        private final String nome;
        private long quantidade;

        private AcumuladoProduto(String id, String nome) {
            this.id = id;
            this.nome = nome;
        }
    }
}
