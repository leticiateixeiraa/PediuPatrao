package com.umc.pediupatrao.dashboard;

import com.umc.pediupatrao.dashboard.dto.DashboardCompleto;
import com.umc.pediupatrao.dashboard.dto.PedidoRecente;
import com.umc.pediupatrao.dashboard.dto.ProdutoMaisVendido;
import com.umc.pediupatrao.dashboard.dto.ResumoVendas;
import com.umc.pediupatrao.dashboard.dto.SituacaoPedidos;
import com.umc.pediupatrao.dashboard.dto.VendasPorPeriodo;
import com.umc.pediupatrao.dashboard.dto.VendasPorTipoEntrega;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.PedidoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Calcula os indicadores do dashboard administrativo a partir da colecao de pedidos.
 *
 * Regras adotadas:
 * - o periodo considera a data/hora de ENTRADA do pedido;
 * - faturamento, ticket medio, ranking de produtos e grafico de vendas ignoram pedidos CANCELADOS;
 * - situacao dos pedidos e taxa de cancelamento consideram todos os pedidos do periodo;
 * - registros incompletos (sem status, sem valor ou sem itens) nao derrubam o calculo:
 *   sao tratados com valor zero ou ignorados e contados em "registrosIgnorados".
 */
@Service
@PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
public class DashboardService {

    static final int PERIODO_PADRAO_DIAS = 30;
    static final int PERIODO_MAXIMO_DIAS = 366;
    static final int LIMITE_MAXIMO = 50;

    private static final BigDecimal CEM = BigDecimal.valueOf(100);
    private static final DateTimeFormatter ROTULO_DIA = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter ROTULO_MES = DateTimeFormatter.ofPattern("MM/yyyy");

    private final PedidoRepository pedidoRepository;
    private final Clock clock;

    public DashboardService(PedidoRepository pedidoRepository, Clock clock) {
        this.pedidoRepository = pedidoRepository;
        this.clock = clock;
    }

    // ===================== endpoints =====================

    public DashboardCompleto gerarCompleto(FiltroDashboard filtro, Agrupamento agrupamento,
                                           int limiteProdutos, int limiteRecentes) {
        Periodo periodo = resolverPeriodo(filtro);
        validarLimite(limiteProdutos, "limiteProdutos");
        validarLimite(limiteRecentes, "limiteRecentes");
        List<Pedido> pedidos = buscar(filtro, periodo);

        return new DashboardCompleto(
                LocalDateTime.now(clock),
                calcularResumo(pedidos, periodo),
                calcularVendasPorPeriodo(pedidos, periodo, agrupamento),
                calcularSituacao(pedidos),
                calcularPorTipoEntrega(pedidos),
                calcularProdutosMaisVendidos(pedidos, limiteProdutos),
                listarRecentes(pedidos, limiteRecentes));
    }

    public ResumoVendas resumo(FiltroDashboard filtro) {
        Periodo periodo = resolverPeriodo(filtro);
        return calcularResumo(buscar(filtro, periodo), periodo);
    }

    public List<VendasPorPeriodo> vendasPorPeriodo(FiltroDashboard filtro, Agrupamento agrupamento) {
        Periodo periodo = resolverPeriodo(filtro);
        return calcularVendasPorPeriodo(buscar(filtro, periodo), periodo, agrupamento);
    }

    public List<SituacaoPedidos> situacaoPedidos(FiltroDashboard filtro) {
        Periodo periodo = resolverPeriodo(filtro);
        return calcularSituacao(buscar(filtro, periodo));
    }

    public List<VendasPorTipoEntrega> vendasPorTipoEntrega(FiltroDashboard filtro) {
        Periodo periodo = resolverPeriodo(filtro);
        return calcularPorTipoEntrega(buscar(filtro, periodo));
    }

    public List<ProdutoMaisVendido> produtosMaisVendidos(FiltroDashboard filtro, int limite) {
        validarLimite(limite, "limite");
        Periodo periodo = resolverPeriodo(filtro);
        return calcularProdutosMaisVendidos(buscar(filtro, periodo), limite);
    }

    public List<PedidoRecente> pedidosRecentes(FiltroDashboard filtro, int limite) {
        validarLimite(limite, "limite");
        Periodo periodo = resolverPeriodo(filtro);
        return listarRecentes(buscar(filtro, periodo), limite);
    }

    // ===================== filtros e consulta =====================

    record Periodo(LocalDate inicio, LocalDate fim) {
    }

    Periodo resolverPeriodo(FiltroDashboard filtro) {
        FiltroDashboard f = filtro != null ? filtro : FiltroDashboard.vazio();
        LocalDate hoje = LocalDate.now(clock);
        LocalDate fim = f.dataFim() != null ? f.dataFim() : hoje;
        LocalDate inicio = f.dataInicio() != null ? f.dataInicio() : fim.minusDays(PERIODO_PADRAO_DIAS - 1L);

        if (inicio.isAfter(fim)) {
            throw new RegraNegocioException("A data inicial não pode ser posterior à data final.");
        }
        if (ChronoUnit.DAYS.between(inicio, fim) + 1 > PERIODO_MAXIMO_DIAS) {
            throw new RegraNegocioException("O período consultado pode ter no máximo " + PERIODO_MAXIMO_DIAS + " dias.");
        }
        return new Periodo(inicio, fim);
    }

    private void validarLimite(int limite, String nome) {
        if (limite < 1 || limite > LIMITE_MAXIMO) {
            throw new RegraNegocioException("O parâmetro '" + nome + "' deve ficar entre 1 e " + LIMITE_MAXIMO + ".");
        }
    }

    private List<Pedido> buscar(FiltroDashboard filtro, Periodo periodo) {
        List<Pedido> doPeriodo = pedidoRepository.buscarPorPeriodoDeEntrada(
                periodo.inicio().atStartOfDay(), periodo.fim().plusDays(1).atStartOfDay());
        if (doPeriodo == null) {
            return List.of();
        }
        StatusPedido status = filtro != null ? filtro.status() : null;
        TipoEntrega tipo = filtro != null ? filtro.tipoEntrega() : null;

        return doPeriodo.stream()
                .filter(Objects::nonNull)
                .filter(p -> p.getDataHoraEntrada() != null)
                .filter(p -> status == null || status == p.getStatus())
                .filter(p -> tipo == null || tipo == p.getTipoEntrega())
                .toList();
    }

    // ===================== calculos =====================

    ResumoVendas calcularResumo(List<Pedido> pedidos, Periodo periodo) {
        long cancelados = 0;
        long emAndamento = 0;
        long ignorados = 0;
        long itensVendidos = 0;
        BigDecimal faturamento = BigDecimal.ZERO;
        BigDecimal descontos = BigDecimal.ZERO;

        for (Pedido p : pedidos) {
            if (p.getStatus() == null) {
                ignorados++;
                continue;
            }
            if (p.getStatus() == StatusPedido.CANCELADO) {
                cancelados++;
                continue;
            }
            if (!p.getStatus().ehFinal()) {
                emAndamento++;
            }
            faturamento = faturamento.add(valor(p.getValorTotal()));
            descontos = descontos.add(valor(p.getValorDesconto()));
            itensVendidos += itens(p).stream().mapToLong(ItemPedido::getQuantidade).sum();
        }

        long total = pedidos.size() - ignorados;
        long validos = total - cancelados;
        BigDecimal ticketMedio = validos > 0
                ? faturamento.divide(BigDecimal.valueOf(validos), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2);

        return new ResumoVendas(periodo.inicio(), periodo.fim(),
                faturamento.setScale(2, RoundingMode.HALF_UP),
                total, validos, cancelados, emAndamento,
                ticketMedio,
                percentual(cancelados, total),
                descontos.setScale(2, RoundingMode.HALF_UP),
                itensVendidos,
                ignorados);
    }

    List<VendasPorPeriodo> calcularVendasPorPeriodo(List<Pedido> pedidos, Periodo periodo, Agrupamento agrupamento) {
        Agrupamento ag = agrupamento != null ? agrupamento : Agrupamento.DIA;

        // cria todos os intervalos do periodo, inclusive os sem venda, pra o grafico nao "pular" dias
        Map<LocalDate, long[]> quantidades = new LinkedHashMap<>();
        Map<LocalDate, BigDecimal> valores = new LinkedHashMap<>();
        LocalDate cursor = inicioDoGrupo(periodo.inicio(), ag);
        while (!cursor.isAfter(periodo.fim())) {
            quantidades.put(cursor, new long[]{0});
            valores.put(cursor, BigDecimal.ZERO);
            cursor = proximoGrupo(cursor, ag);
        }

        for (Pedido p : pedidos) {
            if (p.getStatus() == null || p.getStatus() == StatusPedido.CANCELADO) {
                continue;
            }
            LocalDate chave = inicioDoGrupo(p.getDataHoraEntrada().toLocalDate(), ag);
            if (!quantidades.containsKey(chave)) {
                continue;
            }
            quantidades.get(chave)[0]++;
            valores.merge(chave, valor(p.getValorTotal()), BigDecimal::add);
        }

        List<VendasPorPeriodo> resultado = new ArrayList<>();
        for (Map.Entry<LocalDate, long[]> e : quantidades.entrySet()) {
            LocalDate data = e.getKey();
            // a primeira semana/mes pode comecar antes do filtro: o rotulo mostra a data real de inicio
            LocalDate inicioRotulo = data.isBefore(periodo.inicio()) ? periodo.inicio() : data;
            resultado.add(new VendasPorPeriodo(chaveTexto(data, ag), rotulo(inicioRotulo, ag),
                    e.getValue()[0], valores.get(data).setScale(2, RoundingMode.HALF_UP)));
        }
        return resultado;
    }

    List<SituacaoPedidos> calcularSituacao(List<Pedido> pedidos) {
        Map<StatusPedido, Long> contagem = new EnumMap<>(StatusPedido.class);
        for (StatusPedido s : StatusPedido.values()) {
            contagem.put(s, 0L);
        }
        long total = 0;
        for (Pedido p : pedidos) {
            if (p.getStatus() != null) {
                contagem.merge(p.getStatus(), 1L, Long::sum);
                total++;
            }
        }
        List<SituacaoPedidos> resultado = new ArrayList<>();
        for (Map.Entry<StatusPedido, Long> e : contagem.entrySet()) {
            resultado.add(new SituacaoPedidos(e.getKey().name(), e.getKey().getDescricao(),
                    e.getValue(), percentual(e.getValue(), total)));
        }
        return resultado;
    }

    List<VendasPorTipoEntrega> calcularPorTipoEntrega(List<Pedido> pedidos) {
        List<VendasPorTipoEntrega> resultado = new ArrayList<>();
        for (TipoEntrega tipo : TipoEntrega.values()) {
            long quantidade = 0;
            BigDecimal total = BigDecimal.ZERO;
            for (Pedido p : pedidos) {
                if (p.getTipoEntrega() == tipo && p.getStatus() != null && p.getStatus() != StatusPedido.CANCELADO) {
                    quantidade++;
                    total = total.add(valor(p.getValorTotal()));
                }
            }
            resultado.add(new VendasPorTipoEntrega(tipo.name(), quantidade, total.setScale(2, RoundingMode.HALF_UP)));
        }
        return resultado;
    }

    List<ProdutoMaisVendido> calcularProdutosMaisVendidos(List<Pedido> pedidos, int limite) {
        // chave = id do produto (ou o nome, para itens antigos sem id)
        Map<String, String> nomes = new LinkedHashMap<>();
        Map<String, Long> quantidades = new LinkedHashMap<>();
        Map<String, BigDecimal> valores = new LinkedHashMap<>();
        BigDecimal faturamentoItens = BigDecimal.ZERO;

        for (Pedido p : pedidos) {
            if (p.getStatus() == null || p.getStatus() == StatusPedido.CANCELADO) {
                continue;
            }
            for (ItemPedido item : itens(p)) {
                if (item.getQuantidade() <= 0) {
                    continue;
                }
                String chave = item.getProdutoId() != null ? item.getProdutoId() : item.getNomeProduto();
                if (chave == null) {
                    continue;
                }
                BigDecimal subtotal = valor(item.getSubtotal());
                nomes.putIfAbsent(chave, item.getNomeProduto() != null ? item.getNomeProduto() : "(sem nome)");
                quantidades.merge(chave, (long) item.getQuantidade(), Long::sum);
                valores.merge(chave, subtotal, BigDecimal::add);
                faturamentoItens = faturamentoItens.add(subtotal);
            }
        }

        BigDecimal totalItens = faturamentoItens;
        List<String> ordem = new ArrayList<>(quantidades.keySet());
        ordem.sort(Comparator.<String>comparingLong(quantidades::get).reversed()
                .thenComparing(valores::get, Comparator.reverseOrder())
                .thenComparing(nomes::get));

        List<ProdutoMaisVendido> ranking = new ArrayList<>();
        for (int i = 0; i < ordem.size() && i < limite; i++) {
            String chave = ordem.get(i);
            BigDecimal valor = valores.get(chave);
            BigDecimal participacao = totalItens.signum() > 0
                    ? valor.multiply(CEM).divide(totalItens, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(2);
            ranking.add(new ProdutoMaisVendido(i + 1, chave, nomes.get(chave), quantidades.get(chave),
                    valor.setScale(2, RoundingMode.HALF_UP), participacao));
        }
        return ranking;
    }

    List<PedidoRecente> listarRecentes(List<Pedido> pedidos, int limite) {
        return pedidos.stream()
                .sorted(Comparator.comparing(Pedido::getDataHoraEntrada).reversed())
                .limit(limite)
                .map(p -> new PedidoRecente(
                        p.getId(),
                        p.getClienteNome(),
                        p.getStatus() != null ? p.getStatus().name() : null,
                        p.getStatus() != null ? p.getStatus().getDescricao() : "Indefinido",
                        p.getTipoEntrega() != null ? p.getTipoEntrega().name() : null,
                        valor(p.getValorTotal()).setScale(2, RoundingMode.HALF_UP),
                        p.getDataHoraEntrada(),
                        p.getRegistradoPor()))
                .toList();
    }

    // ===================== auxiliares =====================

    private static LocalDate inicioDoGrupo(LocalDate data, Agrupamento ag) {
        return switch (ag) {
            case DIA -> data;
            case SEMANA -> data.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MES -> data.withDayOfMonth(1);
        };
    }

    private static LocalDate proximoGrupo(LocalDate data, Agrupamento ag) {
        return switch (ag) {
            case DIA -> data.plusDays(1);
            case SEMANA -> data.plusWeeks(1);
            case MES -> data.plusMonths(1);
        };
    }

    private static String chaveTexto(LocalDate data, Agrupamento ag) {
        return ag == Agrupamento.MES ? YearMonth.from(data).toString() : data.toString();
    }

    private static String rotulo(LocalDate data, Agrupamento ag) {
        return switch (ag) {
            case DIA -> data.format(ROTULO_DIA);
            case SEMANA -> "Sem. " + data.format(ROTULO_DIA);
            case MES -> data.format(ROTULO_MES);
        };
    }

    private static BigDecimal percentual(long parte, long total) {
        if (total <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return BigDecimal.valueOf(parte).multiply(CEM).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal valor(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static List<ItemPedido> itens(Pedido p) {
        return p.getItens() != null ? p.getItens().stream().filter(Objects::nonNull).toList() : List.of();
    }
}
