package com.umc.pediupatrao.dto;

import com.umc.pediupatrao.entity.TipoEntrega;
import java.util.List;

/**
 * Dados aceitos na criacao/alteracao do pedido.
 * Nao tem preco nem total de proposito: o servidor calcula tudo a partir dos produtos.
 */
public record PedidoRequest(String clienteId,
                            TipoEntrega tipoEntrega,
                            String observacao,
                            List<ItemPedidoRequest> itens) {
}
