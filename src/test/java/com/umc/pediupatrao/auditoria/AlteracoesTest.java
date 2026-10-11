package com.umc.pediupatrao.auditoria;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AlteracoesTest {

    @Test
    void ignoraCamposIguais() {
        Alteracoes a = new Alteracoes()
                .campo("nome", "Ana", "Ana")
                .campo("valor", new BigDecimal("10"), new BigDecimal("10.00"))
                .campo("obs", "", null);
        assertTrue(a.vazia());
    }

    @Test
    void registraAnteriorENovo() {
        Alteracoes a = new Alteracoes().campo("bairro", "Centro", "Jardins");
        assertEquals(1, a.lista().size());
        assertEquals("Centro", a.lista().get(0).getValorAnterior());
        assertEquals("Jardins", a.lista().get(0).getValorNovo());
    }

    @Test
    void mascaraTelefone() {
        assertEquals("(11) *****-4321", MascaraDados.telefone("(11) 98765-4321"));
        assertEquals("(11) *****-4444", MascaraDados.telefone("1133334444"));
        assertNull(MascaraDados.telefone(null));
    }
}
