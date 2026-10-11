package com.umc.pediupatrao.dto;

import java.math.BigDecimal;

/**
 * percentual: de 0,01 ate 20 (%). justificativa e opcional mas vai para a auditoria.
 */
public record DescontoRequest(BigDecimal percentual, String justificativa) {
}
