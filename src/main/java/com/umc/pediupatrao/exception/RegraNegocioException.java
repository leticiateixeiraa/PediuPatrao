package com.umc.pediupatrao.exception;

/**
 * Violacao de regra de negocio (dados invalidos, operacao nao permitida no estado atual etc).
 * A API devolve 422.
 */
public class RegraNegocioException extends RuntimeException {

    public RegraNegocioException(String mensagem) {
        super(mensagem);
    }
}
