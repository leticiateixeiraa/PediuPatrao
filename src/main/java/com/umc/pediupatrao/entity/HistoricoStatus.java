package com.umc.pediupatrao.entity;

import java.time.LocalDateTime;

/**
 * Cada mudanca de status fica guardada no proprio pedido (linha do tempo).
 */
public class HistoricoStatus {

    private StatusPedido status;
    private LocalDateTime dataHora;
    private String usuario;

    public HistoricoStatus() {
    }

    public HistoricoStatus(StatusPedido status, LocalDateTime dataHora, String usuario) {
        this.status = status;
        this.dataHora = dataHora;
        this.usuario = usuario;
    }

    public StatusPedido getStatus() { return status; }
    public void setStatus(StatusPedido status) { this.status = status; }

    public LocalDateTime getDataHora() { return dataHora; }
    public void setDataHora(LocalDateTime dataHora) { this.dataHora = dataHora; }

    public String getUsuario() { return usuario; }
    public void setUsuario(String usuario) { this.usuario = usuario; }
}
