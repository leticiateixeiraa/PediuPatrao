package com.umc.pediupatrao.security;

import com.umc.pediupatrao.entity.Perfil;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Le o usuario autenticado do contexto do Spring Security.
 * Usado pelos services para gravar "quem fez" (responsavel e auditoria).
 */
@Component
public class UsuarioLogado {

    public String username() {
        Authentication auth = autenticacao();
        return auth != null ? auth.getName() : "sistema";
    }

    public Perfil perfil() {
        Authentication auth = autenticacao();
        if (auth == null) {
            return null;
        }
        for (GrantedAuthority authority : auth.getAuthorities()) {
            String nome = authority.getAuthority();
            if (nome != null && nome.startsWith("ROLE_") && Perfil.valido(nome.substring(5))) {
                return Perfil.valueOf(nome.substring(5));
            }
        }
        return null;
    }

    public boolean temPerfil(Perfil perfil) {
        return perfil != null && perfil == perfil();
    }

    private Authentication autenticacao() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return auth;
    }
}
