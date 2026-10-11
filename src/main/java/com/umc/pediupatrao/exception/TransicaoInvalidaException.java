package com.umc.pediupatrao.exception;

/**
 * Tentativa de mudar o pedido para um status fora do fluxo permitido.
 * A API devolve 409 (conflito com o estado atual do pedido).
 */
public class TransicaoInvalidaException extends RegraNegocioException {

    public TransicaoInvalidaException(String mensagem) {
        super(mensagem);
    }
}
