package com.umc.pediupatrao.dashboard;

import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import java.time.LocalDate;

/**
 * Filtros recebidos da tela/API. Todos opcionais:
 * sem datas, o periodo padrao e dos ultimos 30 dias.
 */
public record FiltroDashboard(LocalDate dataInicio,
                              LocalDate dataFim,
                              StatusPedido status,
                              TipoEntrega tipoEntrega) {

    public static FiltroDashboard vazio() {
        return new FiltroDashboard(null, null, null, null);
    }
}
