package com.umc.pediupatrao.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StatusPedidoTest {

    @Test
    void fluxoDeEntrega() {
        assertEquals(StatusPedido.EM_PREPARACAO, StatusPedido.RECEBIDO.proximo(TipoEntrega.ENTREGA));
        assertEquals(StatusPedido.PRONTO, StatusPedido.EM_PREPARACAO.proximo(TipoEntrega.ENTREGA));
        assertEquals(StatusPedido.SAIU_PARA_ENTREGA, StatusPedido.PRONTO.proximo(TipoEntrega.ENTREGA));
        assertEquals(StatusPedido.FINALIZADO, StatusPedido.SAIU_PARA_ENTREGA.proximo(TipoEntrega.ENTREGA));
    }

    @Test
    void fluxoDeRetirada() {
        assertEquals(StatusPedido.RETIRADO, StatusPedido.PRONTO.proximo(TipoEntrega.RETIRADA));
        assertEquals(StatusPedido.FINALIZADO, StatusPedido.RETIRADO.proximo(TipoEntrega.RETIRADA));
    }

    @Test
    void naoPermitePularEtapas() {
        assertFalse(StatusPedido.EM_PREPARACAO.podeIrPara(StatusPedido.FINALIZADO, TipoEntrega.ENTREGA));
        assertFalse(StatusPedido.RECEBIDO.podeIrPara(StatusPedido.PRONTO, TipoEntrega.ENTREGA));
        assertFalse(StatusPedido.PRONTO.podeIrPara(StatusPedido.RETIRADO, TipoEntrega.ENTREGA));
        assertFalse(StatusPedido.PRONTO.podeIrPara(StatusPedido.EM_PREPARACAO, TipoEntrega.ENTREGA));
    }

    @Test
    void estadosFinaisNaoTemProximo() {
        assertNull(StatusPedido.FINALIZADO.proximo(TipoEntrega.ENTREGA));
        assertNull(StatusPedido.CANCELADO.proximo(TipoEntrega.ENTREGA));
        assertTrue(StatusPedido.CANCELADO.ehFinal());
    }

    @Test
    void cancelamentoSoAntesDaSaida() {
        assertTrue(StatusPedido.RECEBIDO.permiteCancelamentoOuDesconto());
        assertTrue(StatusPedido.EM_PREPARACAO.permiteCancelamentoOuDesconto());
        assertTrue(StatusPedido.PRONTO.permiteCancelamentoOuDesconto());
        assertFalse(StatusPedido.SAIU_PARA_ENTREGA.permiteCancelamentoOuDesconto());
        assertFalse(StatusPedido.RETIRADO.permiteCancelamentoOuDesconto());
        assertFalse(StatusPedido.FINALIZADO.permiteCancelamentoOuDesconto());
        assertFalse(StatusPedido.CANCELADO.permiteCancelamentoOuDesconto());
    }
}
