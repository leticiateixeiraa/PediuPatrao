package com.umc.pediupatrao.entity;

/**
 * Fluxo do pedido:
 *
 * RECEBIDO -> EM_PREPARACAO -> PRONTO -> SAIU_PARA_ENTREGA (entrega) -> FINALIZADO
 *                                     -> RETIRADO (retirada)        -> FINALIZADO
 *
 * CANCELADO e excepcional: so pelo GERENTE e antes da saida (ver PedidoService.cancelar).
 * FINALIZADO e CANCELADO sao estados finais, nada muda depois deles.
 */
public enum StatusPedido {
    RECEBIDO("Recebido"),
    EM_PREPARACAO("Em preparação"),
    PRONTO("Pronto"),
    SAIU_PARA_ENTREGA("Saiu para entrega"),
    RETIRADO("Retirado"),
    FINALIZADO("Finalizado"),
    CANCELADO("Cancelado");

    private final String descricao;

    StatusPedido(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    /**
     * Proximo status no fluxo normal (sem cancelamento). Retorna null quando nao ha proximo.
     */
    public StatusPedido proximo(TipoEntrega tipoEntrega) {
        return switch (this) {
            case RECEBIDO -> EM_PREPARACAO;
            case EM_PREPARACAO -> PRONTO;
            case PRONTO -> tipoEntrega == TipoEntrega.RETIRADA ? RETIRADO : SAIU_PARA_ENTREGA;
            case SAIU_PARA_ENTREGA, RETIRADO -> FINALIZADO;
            case FINALIZADO, CANCELADO -> null;
        };
    }

    public boolean podeIrPara(StatusPedido destino, TipoEntrega tipoEntrega) {
        return destino != null && destino == proximo(tipoEntrega);
    }

    /** Status que representam a saida do pedido da loja. */
    public boolean ehSaida() {
        return this == SAIU_PARA_ENTREGA || this == RETIRADO;
    }

    /** Ja saiu para entrega/retirada (ou terminou). */
    public boolean jaSaiu() {
        return this == SAIU_PARA_ENTREGA || this == RETIRADO || this == FINALIZADO;
    }

    public boolean ehFinal() {
        return this == FINALIZADO || this == CANCELADO;
    }

    /** Cancelamento e desconto so enquanto o pedido ainda esta na loja. */
    public boolean permiteCancelamentoOuDesconto() {
        return this == RECEBIDO || this == EM_PREPARACAO || this == PRONTO;
    }
}
