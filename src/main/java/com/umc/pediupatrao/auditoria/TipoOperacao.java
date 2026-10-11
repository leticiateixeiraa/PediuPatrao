package com.umc.pediupatrao.auditoria;

public enum TipoOperacao {
    INCLUSAO_PEDIDO("INCLUSÃO DE PEDIDO"),
    ALTERACAO_PEDIDO("ALTERAÇÃO DE PEDIDO"),
    MUDANCA_STATUS_PEDIDO("MUDANÇA DE STATUS"),
    DESCONTO_PEDIDO("DESCONTO EM PEDIDO"),
    CANCELAMENTO_PEDIDO("CANCELAMENTO DE PEDIDO"),
    INCLUSAO_CLIENTE("INCLUSÃO DE CLIENTE"),
    ALTERACAO_CLIENTE("ALTERAÇÃO DE CLIENTE"),
    EXCLUSAO_CLIENTE("EXCLUSÃO DE CLIENTE"),
    INCLUSAO_USUARIO("INCLUSÃO DE USUÁRIO"),
    ALTERACAO_USUARIO("ALTERAÇÃO DE USUÁRIO"),
    EXCLUSAO_USUARIO("EXCLUSÃO DE USUÁRIO");

    private final String descricao;

    TipoOperacao(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
