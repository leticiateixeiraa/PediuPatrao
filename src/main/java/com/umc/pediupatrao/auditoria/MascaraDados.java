package com.umc.pediupatrao.auditoria;

/**
 * Reduz a exposicao de dados pessoais na auditoria (LGPD - minimizacao).
 * Para saber que o telefone mudou nao e preciso guardar o numero inteiro.
 */
public final class MascaraDados {

    private MascaraDados() {
    }

    /** (11) 98765-4321 -> (11) *****-4321 */
    public static String telefone(String telefone) {
        if (telefone == null || telefone.isBlank()) {
            return null;
        }
        String digitos = telefone.replaceAll("\\D", "");
        if (digitos.length() < 6) {
            return "****";
        }
        String ddd = digitos.length() >= 10 ? "(" + digitos.substring(0, 2) + ") " : "";
        return ddd + "*****-" + digitos.substring(digitos.length() - 4);
    }
}
