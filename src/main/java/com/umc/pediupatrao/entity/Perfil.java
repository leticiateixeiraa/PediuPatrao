package com.umc.pediupatrao.entity;

/**
 * Perfis de acesso do sistema.
 * ADMIN    - administra usuarios, consulta pedidos e auditoria
 * GERENTE  - gerencia pedidos, desconto, cancelamento, consulta clientes e auditoria
 * ATENDENTE - cadastra/atualiza clientes, registra e acompanha pedidos
 */
public enum Perfil {
    ADMIN,
    GERENTE,
    ATENDENTE;

    /**
     * Converte o texto salvo no banco para o perfil.
     * Usuarios antigos com role "USER" (ou vazia) caem como ATENDENTE,
     * que e o perfil com menos privilegios.
     */
    public static Perfil deTexto(String valor) {
        if (valor == null || valor.isBlank()) {
            return ATENDENTE;
        }
        try {
            return Perfil.valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ATENDENTE;
        }
    }

    public static boolean valido(String valor) {
        if (valor == null) {
            return false;
        }
        for (Perfil p : values()) {
            if (p.name().equals(valor.trim().toUpperCase())) {
                return true;
            }
        }
        return false;
    }
}
