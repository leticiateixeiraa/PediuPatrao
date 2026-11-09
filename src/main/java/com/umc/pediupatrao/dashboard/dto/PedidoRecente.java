package com.umc.pediupatrao.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** RF03 - ultimos pedidos para acompanhar a operacao. */
public record PedidoRecente(String id,
                            String clienteNome,
                            String status,
                            String statusDescricao,
                            String tipoEntrega,
                            BigDecimal valorTotal,
                            LocalDateTime dataHoraEntrada,
                            String registradoPor) {
}
