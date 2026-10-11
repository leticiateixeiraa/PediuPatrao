package com.umc.pediupatrao.entity;

import java.math.BigDecimal;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

/**
 * Item do pedido. Nome e preco sao copiados do produto no momento do registro,
 * assim o historico nao muda se o produto for alterado depois.
 */
public class ItemPedido {

    private String produtoId;
    private String nomeProduto;
    private int quantidade;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal precoUnitario;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal subtotal;

    public ItemPedido() {
    }

    public ItemPedido(String produtoId, String nomeProduto, int quantidade, BigDecimal precoUnitario) {
        this.produtoId = produtoId;
        this.nomeProduto = nomeProduto;
        this.quantidade = quantidade;
        this.precoUnitario = precoUnitario;
        this.subtotal = precoUnitario.multiply(BigDecimal.valueOf(quantidade));
    }

    public String getProdutoId() { return produtoId; }
    public void setProdutoId(String produtoId) { this.produtoId = produtoId; }

    public String getNomeProduto() { return nomeProduto; }
    public void setNomeProduto(String nomeProduto) { this.nomeProduto = nomeProduto; }

    public int getQuantidade() { return quantidade; }
    public void setQuantidade(int quantidade) { this.quantidade = quantidade; }

    public BigDecimal getPrecoUnitario() { return precoUnitario; }
    public void setPrecoUnitario(BigDecimal precoUnitario) { this.precoUnitario = precoUnitario; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    @Override
    public String toString() {
        return quantidade + "x " + nomeProduto + " (R$ " + precoUnitario + ")";
    }
}
