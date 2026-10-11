package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.ItemPedidoRequest;
import com.umc.pediupatrao.dto.PedidoRequest;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.HistoricoStatus;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.exception.TransicaoInvalidaException;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.security.UsuarioLogado;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class PedidoService {

    static final int QUANTIDADE_MAXIMA_POR_ITEM = 50;
    static final int MAXIMO_ITENS = 30;
    static final int TAMANHO_MAXIMO_OBSERVACAO = 300;
    static final BigDecimal DESCONTO_MAXIMO = new BigDecimal("20");
    static final int TAMANHO_MINIMO_JUSTIFICATIVA = 10;
    static final int TAMANHO_MAXIMO_JUSTIFICATIVA = 500;

    private final PedidoRepository pedidoRepository;
    private final ProdutoRepository produtoRepository;
    private final ClienteRepository clienteRepository;
    private final UsuarioLogado usuarioLogado;
    private final Clock clock;

    public PedidoService(PedidoRepository pedidoRepository,
                         ProdutoRepository produtoRepository,
                         ClienteRepository clienteRepository,
                         UsuarioLogado usuarioLogado,
                         Clock clock) {
        this.pedidoRepository = pedidoRepository;
        this.produtoRepository = produtoRepository;
        this.clienteRepository = clienteRepository;
        this.usuarioLogado = usuarioLogado;
        this.clock = clock;
    }

    public List<Pedido> listarPedidos() {
        return pedidoRepository.findAll(Sort.by(Sort.Direction.DESC, "dataHoraEntrada"));
    }

    public Pedido buscarPorId(String id) {
        return pedidoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido não encontrado."));
    }

    /**
     * Registra a entrada de um pedido (status RECEBIDO).
     * Precos e total sao calculados aqui, nunca vem da requisicao.
     */
    public Pedido criarPedido(PedidoRequest request) {
        if (request == null) {
            throw new RegraNegocioException("Dados do pedido não informados.");
        }
        Cliente cliente = buscarCliente(request.clienteId());
        TipoEntrega tipoEntrega = validarTipoEntrega(request.tipoEntrega(), cliente);

        Pedido pedido = new Pedido();
        pedido.setClienteId(cliente.getId());
        pedido.setClienteNome(cliente.getNome());
        pedido.setTipoEntrega(tipoEntrega);
        pedido.setObservacao(validarObservacao(request.observacao()));
        pedido.setItens(montarItens(request.itens()));
        pedido.setPercentualDesconto(BigDecimal.ZERO);
        recalcularValores(pedido);

        LocalDateTime agora = agora();
        String usuario = usuarioLogado.username();
        pedido.setStatus(StatusPedido.RECEBIDO);
        pedido.setRegistradoPor(usuario);
        pedido.setDataHoraEntrada(agora);
        pedido.getHistorico().add(new HistoricoStatus(StatusPedido.RECEBIDO, agora, usuario));

        return pedidoRepository.save(pedido);
    }

    /**
     * Avanca o pedido no fluxo. So aceita o proximo status valido;
     * cancelamento tem operacao propria (exclusiva do gerente).
     */
    public Pedido atualizarStatus(String id, StatusPedido novoStatus) {
        if (novoStatus == null) {
            throw new RegraNegocioException("Informe o novo status.");
        }
        Pedido pedido = buscarPorId(id);
        StatusPedido atual = pedido.getStatus();

        if (novoStatus == StatusPedido.CANCELADO) {
            throw new RegraNegocioException("Para cancelar use a opção de cancelamento (somente gerente, com justificativa).");
        }
        if (atual == StatusPedido.CANCELADO) {
            throw new TransicaoInvalidaException("Pedido cancelado não pode mais ter o status alterado.");
        }
        if (atual == null || atual.ehFinal()) {
            throw new TransicaoInvalidaException("Pedido já finalizado não pode mais ter o status alterado.");
        }
        if (!atual.podeIrPara(novoStatus, pedido.getTipoEntrega())) {
            StatusPedido esperado = atual.proximo(pedido.getTipoEntrega());
            throw new TransicaoInvalidaException("Transição inválida: " + atual.getDescricao() + " → "
                    + novoStatus.getDescricao() + ". Próximo status permitido: "
                    + (esperado != null ? esperado.getDescricao() : "nenhum") + ".");
        }

        LocalDateTime agora = agora();
        String usuario = usuarioLogado.username();

        if (novoStatus.ehSaida()) {
            // saida obrigatoriamente registra data/hora e responsavel
            pedido.setDataHoraSaida(agora);
            pedido.setSaidaRegistradaPor(usuario);
        }
        if (novoStatus == StatusPedido.FINALIZADO) {
            pedido.setDataHoraFinalizacao(agora);
            pedido.setFinalizadoPor(usuario);
        }
        pedido.setStatus(novoStatus);
        pedido.getHistorico().add(new HistoricoStatus(novoStatus, agora, usuario));

        return pedidoRepository.save(pedido);
    }

    /** Atalho para mover para o proximo status do fluxo. */
    public Pedido avancarStatus(String id) {
        Pedido pedido = buscarPorId(id);
        StatusPedido proximo = pedido.getProximoStatus();
        if (proximo == null) {
            throw new TransicaoInvalidaException("Este pedido não tem próximo status.");
        }
        return atualizarStatus(id, proximo);
    }

    /**
     * Cancela o pedido. Exclusivo do GERENTE (inclusive em relacao ao ADMIN),
     * exige justificativa e so e permitido antes da saida para entrega/retirada.
     * Os dados do pedido sao mantidos (nada e apagado), so o status muda.
     */
    @PreAuthorize("hasRole('GERENTE')")
    public Pedido cancelar(String id, String justificativa) {
        String motivo = validarJustificativa(justificativa, true);
        Pedido pedido = buscarPorId(id);
        StatusPedido atual = pedido.getStatus();

        if (atual == StatusPedido.CANCELADO) {
            throw new TransicaoInvalidaException("Este pedido já está cancelado.");
        }
        if (atual == null || !atual.permiteCancelamentoOuDesconto()) {
            throw new TransicaoInvalidaException("Pedido que já saiu para entrega/retirada não pode ser cancelado.");
        }

        LocalDateTime agora = agora();
        String usuario = usuarioLogado.username();
        pedido.setStatusAntesDoCancelamento(atual);
        pedido.setStatus(StatusPedido.CANCELADO);
        pedido.setCanceladoPor(usuario);
        pedido.setDataHoraCancelamento(agora);
        pedido.setJustificativaCancelamento(motivo);
        pedido.getHistorico().add(new HistoricoStatus(StatusPedido.CANCELADO, agora, usuario));

        return pedidoRepository.save(pedido);
    }

    /**
     * Aplica desconto percentual (maximo 20%) sobre o subtotal. Exclusivo do GERENTE.
     * Um novo desconto substitui o anterior (nao acumula), entao o total nunca passa de 20%.
     */
    @PreAuthorize("hasRole('GERENTE')")
    public Pedido aplicarDesconto(String id, BigDecimal percentual, String justificativa) {
        if (percentual == null) {
            throw new RegraNegocioException("Informe o percentual de desconto.");
        }
        if (percentual.compareTo(BigDecimal.ZERO) <= 0 || percentual.compareTo(DESCONTO_MAXIMO) > 0) {
            throw new RegraNegocioException("O desconto deve ser maior que 0% e no máximo " + DESCONTO_MAXIMO + "%.");
        }
        if (percentual.stripTrailingZeros().scale() > 2) {
            throw new RegraNegocioException("Use no máximo duas casas decimais no percentual.");
        }
        String motivo = validarJustificativa(justificativa, false);

        Pedido pedido = buscarPorId(id);
        StatusPedido atual = pedido.getStatus();
        if (atual == StatusPedido.CANCELADO) {
            throw new TransicaoInvalidaException("Pedido cancelado não pode receber desconto.");
        }
        if (atual == null || !atual.permiteCancelamentoOuDesconto()) {
            throw new TransicaoInvalidaException("Desconto só pode ser aplicado antes da saída para entrega/retirada.");
        }

        pedido.setPercentualDesconto(percentual.setScale(2, RoundingMode.HALF_UP));
        recalcularValores(pedido);
        pedido.setDescontoAplicadoPor(usuarioLogado.username());
        pedido.setDataHoraDesconto(agora());
        pedido.setJustificativaDesconto(motivo);

        return pedidoRepository.save(pedido);
    }

    // ================== calculos e validacoes ==================

    /**
     * subtotal = soma dos itens; desconto = subtotal * percentual; total = subtotal - desconto
     */
    void recalcularValores(Pedido pedido) {
        BigDecimal subtotal = BigDecimal.ZERO;
        for (ItemPedido item : pedido.getItens()) {
            item.setSubtotal(item.getPrecoUnitario().multiply(BigDecimal.valueOf(item.getQuantidade()))
                    .setScale(2, RoundingMode.HALF_UP));
            subtotal = subtotal.add(item.getSubtotal());
        }
        BigDecimal percentual = pedido.getPercentualDesconto() != null ? pedido.getPercentualDesconto() : BigDecimal.ZERO;
        BigDecimal desconto = subtotal.multiply(percentual)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        pedido.setSubtotal(subtotal.setScale(2, RoundingMode.HALF_UP));
        pedido.setPercentualDesconto(percentual);
        pedido.setValorDesconto(desconto);
        pedido.setValorTotal(subtotal.subtract(desconto).setScale(2, RoundingMode.HALF_UP));
    }

    private List<ItemPedido> montarItens(List<ItemPedidoRequest> itensRequest) {
        if (itensRequest == null || itensRequest.isEmpty()) {
            throw new RegraNegocioException("O pedido precisa ter pelo menos um item.");
        }
        if (itensRequest.size() > MAXIMO_ITENS) {
            throw new RegraNegocioException("O pedido pode ter no máximo " + MAXIMO_ITENS + " itens.");
        }

        List<ItemPedido> itens = new ArrayList<>();
        for (ItemPedidoRequest req : itensRequest) {
            if (req == null || req.produtoId() == null || req.produtoId().isBlank()) {
                throw new RegraNegocioException("Informe o produto de todos os itens.");
            }
            Integer quantidade = req.quantidade();
            if (quantidade == null || quantidade < 1 || quantidade > QUANTIDADE_MAXIMA_POR_ITEM) {
                throw new RegraNegocioException("Quantidade deve ser entre 1 e " + QUANTIDADE_MAXIMA_POR_ITEM + ".");
            }

            Produto produto = produtoRepository.findById(req.produtoId())
                    .orElseThrow(() -> new RegraNegocioException("Produto não encontrado: " + req.produtoId()));
            if (Boolean.FALSE.equals(produto.getAtivo())) {
                throw new RegraNegocioException("O produto '" + produto.getNome() + "' está inativo.");
            }
            if (produto.getPreco() == null || produto.getPreco() <= 0) {
                throw new RegraNegocioException("O produto '" + produto.getNome() + "' está sem preço cadastrado.");
            }

            BigDecimal preco = BigDecimal.valueOf(produto.getPreco()).setScale(2, RoundingMode.HALF_UP);
            itens.add(new ItemPedido(produto.getId(), produto.getNome(), quantidade, preco));
        }
        return itens;
    }

    private Cliente buscarCliente(String clienteId) {
        if (clienteId == null || clienteId.isBlank()) {
            throw new RegraNegocioException("Informe o cliente do pedido.");
        }
        return clienteRepository.findById(clienteId)
                .orElseThrow(() -> new RegraNegocioException("Cliente não encontrado."));
    }

    private TipoEntrega validarTipoEntrega(TipoEntrega tipoEntrega, Cliente cliente) {
        if (tipoEntrega == null) {
            throw new RegraNegocioException("Informe se o pedido é para ENTREGA ou RETIRADA.");
        }
        if (tipoEntrega == TipoEntrega.ENTREGA && !temEndereco(cliente)) {
            throw new RegraNegocioException("Cliente sem endereço cadastrado não pode receber pedido para entrega.");
        }
        return tipoEntrega;
    }

    private boolean temEndereco(Cliente cliente) {
        return (cliente.getLogradouro() != null && !cliente.getLogradouro().isBlank())
                || (cliente.getEndereco() != null && !cliente.getEndereco().isBlank());
    }

    private String validarObservacao(String observacao) {
        if (observacao == null || observacao.isBlank()) {
            return null;
        }
        String texto = observacao.trim();
        if (texto.length() > TAMANHO_MAXIMO_OBSERVACAO) {
            throw new RegraNegocioException("Observação pode ter no máximo " + TAMANHO_MAXIMO_OBSERVACAO + " caracteres.");
        }
        return texto;
    }

    private String validarJustificativa(String justificativa, boolean obrigatoria) {
        String texto = justificativa == null ? "" : justificativa.trim();
        if (texto.isEmpty()) {
            if (obrigatoria) {
                throw new RegraNegocioException("A justificativa é obrigatória.");
            }
            return null;
        }
        if (texto.length() < TAMANHO_MINIMO_JUSTIFICATIVA) {
            throw new RegraNegocioException("A justificativa deve ter pelo menos " + TAMANHO_MINIMO_JUSTIFICATIVA + " caracteres.");
        }
        if (texto.length() > TAMANHO_MAXIMO_JUSTIFICATIVA) {
            throw new RegraNegocioException("A justificativa pode ter no máximo " + TAMANHO_MAXIMO_JUSTIFICATIVA + " caracteres.");
        }
        return texto;
    }

    private LocalDateTime agora() {
        return LocalDateTime.now(clock);
    }
}
