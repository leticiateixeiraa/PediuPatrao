package com.umc.pediupatrao.auditoria;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Monta a lista de campos alterados comparando valor anterior x novo.
 * So entra na lista o que realmente mudou.
 *
 * Ex: new Alteracoes().campo("logradouro", antigo.getLogradouro(), novo.getLogradouro())
 */
public class Alteracoes {

    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final List<AlteracaoCampo> lista = new ArrayList<>();

    public Alteracoes campo(String nome, Object anterior, Object novo) {
        String a = formatar(anterior);
        String n = formatar(novo);
        if (!Objects.equals(a, n)) {
            lista.add(new AlteracaoCampo(nome, a, n));
        }
        return this;
    }

    /**
     * Para dados pessoais: compara o valor real, mas grava mascarado.
     */
    public Alteracoes campoMascarado(String nome, String anterior, String novo, Function<String, String> mascara) {
        String a = formatar(anterior);
        String n = formatar(novo);
        if (!Objects.equals(a, n)) {
            lista.add(new AlteracaoCampo(nome, mascara.apply(a), mascara.apply(n)));
        }
        return this;
    }

    /** Campo que passou a existir (inclusao). */
    public Alteracoes novo(String nome, Object valor) {
        return campo(nome, null, valor);
    }

    public List<AlteracaoCampo> lista() {
        return lista;
    }

    public boolean vazia() {
        return lista.isEmpty();
    }

    static String formatar(Object valor) {
        if (valor == null) {
            return null;
        }
        if (valor instanceof String texto) {
            return texto.isBlank() ? null : texto.trim();
        }
        if (valor instanceof BigDecimal numero) {
            return numero.setScale(2, RoundingMode.HALF_UP).toPlainString();
        }
        if (valor instanceof LocalDateTime data) {
            return data.format(FORMATO_DATA);
        }
        if (valor instanceof Enum<?> e) {
            return e.name();
        }
        if (valor instanceof Collection<?> colecao) {
            return colecao.isEmpty() ? null
                    : colecao.stream().map(String::valueOf).collect(Collectors.joining("; "));
        }
        return String.valueOf(valor);
    }
}
