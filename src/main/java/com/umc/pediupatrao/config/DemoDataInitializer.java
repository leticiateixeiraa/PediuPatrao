package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.HistoricoStatus;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Gera pedidos de demonstracao dos ultimos 45 dias para o dashboard ter o que mostrar.
 * So roda com app.demo-data=true (variavel de ambiente DEMO_DATA=true) e quando ha menos
 * de 20 pedidos no banco. Pedidos gerados ficam com registradoPor = "demo".
 */
@Component
@ConditionalOnProperty(name = "app.demo-data", havingValue = "true")
public class DemoDataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);
    private static final int DIAS = 45;
    private static final String USUARIO_DEMO = "demo";

    private final PedidoRepository pedidoRepository;
    private final ProdutoRepository produtoRepository;
    private final ClienteRepository clienteRepository;
    private final Clock clock;

    public DemoDataInitializer(PedidoRepository pedidoRepository, ProdutoRepository produtoRepository,
                               ClienteRepository clienteRepository, Clock clock) {
        this.pedidoRepository = pedidoRepository;
        this.produtoRepository = produtoRepository;
        this.clienteRepository = clienteRepository;
        this.clock = clock;
    }

    // roda depois dos CommandLineRunner (usuarios e produtos iniciais ja existem)
    @EventListener(ApplicationReadyEvent.class)
    public void gerar() {
        if (pedidoRepository.count() >= 20) {
            log.info("Dados de demonstracao nao gerados: ja existem pedidos suficientes");
            return;
        }
        List<Produto> produtos = produtoRepository.findAll().stream()
                .filter(p -> !Boolean.FALSE.equals(p.getAtivo()) && p.getPreco() != null && p.getPreco() > 0)
                .toList();
        if (produtos.isEmpty()) {
            log.warn("Dados de demonstracao nao gerados: nenhum produto ativo");
            return;
        }
        List<Cliente> clientes = garantirClientes();

        Random random = new Random(2026);
        LocalDate hoje = LocalDate.now(clock);
        List<Pedido> pedidos = new ArrayList<>();

        for (int dia = DIAS - 1; dia >= 0; dia--) {
            LocalDate data = hoje.minusDays(dia);
            // fim de semana vende mais
            int base = (data.getDayOfWeek().getValue() >= 5) ? 4 : 2;
            int quantidade = base + random.nextInt(3);
            for (int i = 0; i < quantidade; i++) {
                LocalDateTime entrada = data.atTime(18 + random.nextInt(5), random.nextInt(60));
                if (entrada.isAfter(LocalDateTime.now(clock))) {
                    entrada = LocalDateTime.now(clock).minusMinutes(5L + random.nextInt(120));
                }
                pedidos.add(montarPedido(random, entrada, dia, produtos, clientes));
            }
        }
        pedidoRepository.saveAll(pedidos);
        log.info("{} pedidos de demonstracao gerados", pedidos.size());
    }

    private Pedido montarPedido(Random random, LocalDateTime entrada, int diasAtras,
                                List<Produto> produtos, List<Cliente> clientes) {
        Cliente cliente = clientes.get(random.nextInt(clientes.size()));
        TipoEntrega tipo = random.nextInt(10) < 6 ? TipoEntrega.ENTREGA : TipoEntrega.RETIRADA;

        List<ItemPedido> itens = new ArrayList<>();
        int qtdItens = 1 + random.nextInt(3);
        for (int i = 0; i < qtdItens; i++) {
            // os primeiros produtos (pizzas) aparecem mais
            Produto produto = produtos.get(Math.min(random.nextInt(produtos.size()), random.nextInt(produtos.size())));
            int qtd = 1 + random.nextInt(produto.getPreco() < 15 ? 4 : 2);
            BigDecimal preco = BigDecimal.valueOf(produto.getPreco()).setScale(2, RoundingMode.HALF_UP);
            itens.add(new ItemPedido(produto.getId(), produto.getNome(), qtd, preco));
        }

        Pedido p = new Pedido();
        p.setClienteId(cliente.getId());
        p.setClienteNome(cliente.getNome());
        p.setTipoEntrega(tipo);
        p.setItens(itens);
        p.setRegistradoPor(USUARIO_DEMO);
        p.setDataHoraEntrada(entrada);

        BigDecimal subtotal = itens.stream().map(ItemPedido::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal percentual = random.nextInt(10) == 0 ? BigDecimal.TEN : BigDecimal.ZERO;
        BigDecimal desconto = subtotal.multiply(percentual).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        p.setSubtotal(subtotal.setScale(2, RoundingMode.HALF_UP));
        p.setPercentualDesconto(percentual.setScale(2, RoundingMode.HALF_UP));
        p.setValorDesconto(desconto);
        p.setValorTotal(subtotal.subtract(desconto).setScale(2, RoundingMode.HALF_UP));

        StatusPedido status = sortearStatus(random, diasAtras, tipo);
        p.setStatus(status);
        p.getHistorico().add(new HistoricoStatus(StatusPedido.RECEBIDO, entrada, USUARIO_DEMO));

        if (status.jaSaiu()) {
            LocalDateTime saida = entrada.plusMinutes(30 + random.nextInt(20));
            p.setDataHoraSaida(saida);
            p.setSaidaRegistradaPor(USUARIO_DEMO);
            p.getHistorico().add(new HistoricoStatus(tipo == TipoEntrega.RETIRADA ? StatusPedido.RETIRADO
                    : StatusPedido.SAIU_PARA_ENTREGA, saida, USUARIO_DEMO));
            if (status == StatusPedido.FINALIZADO) {
                p.setDataHoraFinalizacao(saida.plusMinutes(20));
                p.setFinalizadoPor(USUARIO_DEMO);
                p.getHistorico().add(new HistoricoStatus(StatusPedido.FINALIZADO, saida.plusMinutes(20), USUARIO_DEMO));
            }
        }
        if (status == StatusPedido.CANCELADO) {
            p.setStatusAntesDoCancelamento(StatusPedido.RECEBIDO);
            p.setCanceladoPor(USUARIO_DEMO);
            p.setDataHoraCancelamento(entrada.plusMinutes(10));
            p.setJustificativaCancelamento("Pedido de demonstração cancelado");
            p.getHistorico().add(new HistoricoStatus(StatusPedido.CANCELADO, entrada.plusMinutes(10), USUARIO_DEMO));
        }
        return p;
    }

    private StatusPedido sortearStatus(Random random, int diasAtras, TipoEntrega tipo) {
        int sorteio = random.nextInt(100);
        if (sorteio < 8) {
            return StatusPedido.CANCELADO;
        }
        if (diasAtras > 0) {
            return StatusPedido.FINALIZADO;
        }
        // pedidos de hoje: operacao em andamento
        if (sorteio < 30) {
            return StatusPedido.RECEBIDO;
        }
        if (sorteio < 50) {
            return StatusPedido.EM_PREPARACAO;
        }
        if (sorteio < 65) {
            return StatusPedido.PRONTO;
        }
        if (sorteio < 80) {
            return tipo == TipoEntrega.RETIRADA ? StatusPedido.RETIRADO : StatusPedido.SAIU_PARA_ENTREGA;
        }
        return StatusPedido.FINALIZADO;
    }

    private List<Cliente> garantirClientes() {
        List<Cliente> existentes = clienteRepository.findAll();
        if (!existentes.isEmpty()) {
            return existentes;
        }
        List<Cliente> novos = new ArrayList<>();
        String[][] dados = {
                {"Ana Souza", "(11) 91111-2222"},
                {"Bruno Lima", "(11) 93333-4444"},
                {"Carla Mendes", "(11) 95555-6666"}
        };
        for (String[] d : dados) {
            Cliente c = new Cliente();
            c.setNome(d[0]);
            c.setTelefone(d[1]);
            c.setLogradouro("Rua de Demonstração");
            c.setNumero("100");
            c.setCidade("São Paulo");
            c.setEstado("SP");
            novos.add(clienteRepository.save(c));
        }
        return novos;
    }
}
