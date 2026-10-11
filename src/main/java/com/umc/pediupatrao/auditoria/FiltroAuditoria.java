package com.umc.pediupatrao.auditoria;

import java.time.LocalDateTime;

public record FiltroAuditoria(String entidade,
                              String entidadeId,
                              TipoOperacao operacao,
                              String usuario,
                              LocalDateTime inicio,
                              LocalDateTime fim) {
}
