package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.UsuarioRepository;
import com.umc.pediupatrao.security.UsuarioLogado;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Gestao de usuarios. Todas as operacoes de escrita sao exclusivas do ADMIN
 * (alem da regra de URL no SecurityConfig, o @PreAuthorize garante no service).
 */
@Service
public class UsuarioService {

    private static final int TAMANHO_MINIMO_SENHA = 6;

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final UsuarioLogado usuarioLogado;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
                          UsuarioLogado usuarioLogado) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.usuarioLogado = usuarioLogado;
    }

    // Busca um usuário por ID
    public Optional<Usuario> buscarPorId(String id) {
        return usuarioRepository.findById(id);
    }

    // Busca um usuário por username
    public Optional<Usuario> buscarPorUsername(String username) {
        return usuarioRepository.findByUsername(username);
    }

    // Lista todos os usuários
    public List<Usuario> listarTodos() {
        return usuarioRepository.findAll();
    }

    // Cria um novo usuário (criptografando a senha)
    @PreAuthorize("hasRole('ADMIN')")
    public Usuario salvarUsuario(Usuario usuario) {
        String username = normalizarUsername(usuario.getUsername());
        if (usuarioRepository.existsByUsername(username)) {
            throw new RegraNegocioException("Já existe um usuário com o username '" + username + "'.");
        }
        validarSenha(usuario.getPassword());

        Usuario novo = new Usuario();
        novo.setUsername(username);
        novo.setPassword(passwordEncoder.encode(usuario.getPassword()));
        novo.setRole(validarPerfil(usuario.getRole()).name());
        return usuarioRepository.save(novo);
    }

    // Atualiza o usuário (username, perfil e opcionalmente a senha)
    @PreAuthorize("hasRole('ADMIN')")
    public Usuario atualizarUsuario(String id, Usuario usuarioAtualizado) {
        Usuario existente = usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));

        String novoUsername = normalizarUsername(usuarioAtualizado.getUsername());
        if (!novoUsername.equals(existente.getUsername()) && usuarioRepository.existsByUsername(novoUsername)) {
            throw new RegraNegocioException("Já existe um usuário com o username '" + novoUsername + "'.");
        }

        Perfil perfilAtual = existente.getPerfil();
        Perfil novoPerfil = validarPerfil(usuarioAtualizado.getRole());
        boolean ehOProprioUsuario = existente.getUsername().equals(usuarioLogado.username());

        if (ehOProprioUsuario && novoPerfil != perfilAtual) {
            // impede autoelevacao / autorrebaixamento pela propria conta
            throw new RegraNegocioException("Não é permitido alterar o próprio perfil.");
        }
        if (perfilAtual == Perfil.ADMIN && novoPerfil != Perfil.ADMIN) {
            garantirQueNaoEhUltimoAdmin();
        }

        existente.setUsername(novoUsername);
        existente.setRole(novoPerfil.name());

        // Só atualiza a senha se uma nova foi informada
        if (usuarioAtualizado.getPassword() != null && !usuarioAtualizado.getPassword().isBlank()) {
            validarSenha(usuarioAtualizado.getPassword());
            existente.setPassword(passwordEncoder.encode(usuarioAtualizado.getPassword()));
        }

        return usuarioRepository.save(existente);
    }

    // Deleta um usuário pelo ID
    @PreAuthorize("hasRole('ADMIN')")
    public void deletarUsuario(String id) {
        Usuario existente = usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));

        if (existente.getUsername().equals(usuarioLogado.username())) {
            throw new RegraNegocioException("Você não pode excluir o próprio usuário.");
        }
        if (existente.getPerfil() == Perfil.ADMIN) {
            garantirQueNaoEhUltimoAdmin();
        }
        usuarioRepository.deleteById(id);
    }

    private void garantirQueNaoEhUltimoAdmin() {
        if (usuarioRepository.countByRole(Perfil.ADMIN.name()) <= 1) {
            throw new RegraNegocioException("O sistema precisa ter pelo menos um ADMIN.");
        }
    }

    private Perfil validarPerfil(String role) {
        if (!Perfil.valido(role)) {
            throw new RegraNegocioException("Perfil inválido. Use ADMIN, GERENTE ou ATENDENTE.");
        }
        return Perfil.valueOf(role.trim().toUpperCase());
    }

    private String normalizarUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new RegraNegocioException("Informe o username.");
        }
        String normalizado = username.trim();
        if (!normalizado.matches("[A-Za-z0-9._-]{3,30}")) {
            throw new RegraNegocioException("Username deve ter de 3 a 30 caracteres (letras, números, ponto, hífen ou _).");
        }
        return normalizado;
    }

    private void validarSenha(String senha) {
        if (senha == null || senha.length() < TAMANHO_MINIMO_SENHA) {
            throw new RegraNegocioException("A senha deve ter pelo menos " + TAMANHO_MINIMO_SENHA + " caracteres.");
        }
    }
}
