package com.umc.pediupatrao.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

@Document(collection = "pedidos")
public class Pedido {

    @Id
    private String id;

    // evita que duas pessoas alterem o mesmo pedido ao mesmo tempo
    @Version
    private Long versao;

    private String clienteId;
    private String clienteNome;
    private TipoEntrega tipoEntrega;
    private String observacao;

    private List<ItemPedido> itens = new ArrayList<>();

    // ===== valores (sempre calculados no servidor) =====
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal percentualDesconto = BigDecimal.ZERO;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal valorDesconto = BigDecimal.ZERO;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal valorTotal = BigDecimal.ZERO;

    private StatusPedido status;

    // ===== entrada =====
    private String registradoPor;
    private LocalDateTime dataHoraEntrada;

    // ===== saida (saiu para entrega ou retirado) =====
    private String saidaRegistradaPor;
    private LocalDateTime dataHoraSaida;

    // ===== finalizacao =====
    private String finalizadoPor;
    private LocalDateTime dataHoraFinalizacao;

    // ===== cancelamento (somente gerente) =====
    private String canceladoPor;
    private LocalDateTime dataHoraCancelamento;
    private String justificativaCancelamento;
    private StatusPedido statusAntesDoCancelamento;

    // ===== desconto (somente gerente) =====
    private String descontoAplicadoPor;
    private LocalDateTime dataHoraDesconto;
    private String justificativaDesconto;

    private List<HistoricoStatus> historico = new ArrayList<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Long getVersao() { return versao; }
    public void setVersao(Long versao) { this.versao = versao; }

    public String getClienteId() { return clienteId; }
    public void setClienteId(String clienteId) { this.clienteId = clienteId; }

    public String getClienteNome() { return clienteNome; }
    public void setClienteNome(String clienteNome) { this.clienteNome = clienteNome; }

    public TipoEntrega getTipoEntrega() { return tipoEntrega; }
    public void setTipoEntrega(TipoEntrega tipoEntrega) { this.tipoEntrega = tipoEntrega; }

    public String getObservacao() { return observacao; }
    public void setObservacao(String observacao) { this.observacao = observacao; }

    public List<ItemPedido> getItens() { return itens; }
    public void setItens(List<ItemPedido> itens) { this.itens = itens; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    public BigDecimal getPercentualDesconto() { return percentualDesconto; }
    public void setPercentualDesconto(BigDecimal percentualDesconto) { this.percentualDesconto = percentualDesconto; }

    public BigDecimal getValorDesconto() { return valorDesconto; }
    public void setValorDesconto(BigDecimal valorDesconto) { this.valorDesconto = valorDesconto; }

    public BigDecimal getValorTotal() { return valorTotal; }
    public void setValorTotal(BigDecimal valorTotal) { this.valorTotal = valorTotal; }

    public StatusPedido getStatus() { return status; }
    public void setStatus(StatusPedido status) { this.status = status; }

    public String getRegistradoPor() { return registradoPor; }
    public void setRegistradoPor(String registradoPor) { this.registradoPor = registradoPor; }

    public LocalDateTime getDataHoraEntrada() { return dataHoraEntrada; }
    public void setDataHoraEntrada(LocalDateTime dataHoraEntrada) { this.dataHoraEntrada = dataHoraEntrada; }

    public String getSaidaRegistradaPor() { return saidaRegistradaPor; }
    public void setSaidaRegistradaPor(String saidaRegistradaPor) { this.saidaRegistradaPor = saidaRegistradaPor; }

    public LocalDateTime getDataHoraSaida() { return dataHoraSaida; }
    public void setDataHoraSaida(LocalDateTime dataHoraSaida) { this.dataHoraSaida = dataHoraSaida; }

    public String getFinalizadoPor() { return finalizadoPor; }
    public void setFinalizadoPor(String finalizadoPor) { this.finalizadoPor = finalizadoPor; }

    public LocalDateTime getDataHoraFinalizacao() { return dataHoraFinalizacao; }
    public void setDataHoraFinalizacao(LocalDateTime dataHoraFinalizacao) { this.dataHoraFinalizacao = dataHoraFinalizacao; }

    public List<HistoricoStatus> getHistorico() { return historico; }
    public void setHistorico(List<HistoricoStatus> historico) { this.historico = historico; }

    public String getCanceladoPor() { return canceladoPor; }
    public void setCanceladoPor(String canceladoPor) { this.canceladoPor = canceladoPor; }

    public LocalDateTime getDataHoraCancelamento() { return dataHoraCancelamento; }
    public void setDataHoraCancelamento(LocalDateTime dataHoraCancelamento) { this.dataHoraCancelamento = dataHoraCancelamento; }

    public String getJustificativaCancelamento() { return justificativaCancelamento; }
    public void setJustificativaCancelamento(String justificativaCancelamento) { this.justificativaCancelamento = justificativaCancelamento; }

    public StatusPedido getStatusAntesDoCancelamento() { return statusAntesDoCancelamento; }
    public void setStatusAntesDoCancelamento(StatusPedido statusAntesDoCancelamento) { this.statusAntesDoCancelamento = statusAntesDoCancelamento; }

    public String getDescontoAplicadoPor() { return descontoAplicadoPor; }
    public void setDescontoAplicadoPor(String descontoAplicadoPor) { this.descontoAplicadoPor = descontoAplicadoPor; }

    public LocalDateTime getDataHoraDesconto() { return dataHoraDesconto; }
    public void setDataHoraDesconto(LocalDateTime dataHoraDesconto) { this.dataHoraDesconto = dataHoraDesconto; }

    public String getJustificativaDesconto() { return justificativaDesconto; }
    public void setJustificativaDesconto(String justificativaDesconto) { this.justificativaDesconto = justificativaDesconto; }

    /** Proximo status possivel no fluxo (usado nas telas para o botao "avancar"). */
    public StatusPedido getProximoStatus() {
        return status != null ? status.proximo(tipoEntrega) : null;
    }
}
