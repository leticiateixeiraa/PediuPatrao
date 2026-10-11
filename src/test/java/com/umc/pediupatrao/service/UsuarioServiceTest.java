package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.auditoria.AuditoriaService;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.UsuarioRepository;
import com.umc.pediupatrao.security.UsuarioLogado;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private UsuarioLogado usuarioLogado;
    @Mock
    private AuditoriaService auditoriaService;

    private UsuarioService service;

    @BeforeEach
    void setUp() {
        service = new UsuarioService(usuarioRepository, passwordEncoder, usuarioLogado, auditoriaService);
        when(passwordEncoder.encode(anyString())).thenAnswer(inv -> "hash-" + inv.getArgument(0));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void adminNaoPodeAlterarOProprioPerfil() {
        Usuario admin = usuario("1", "admin", "ADMIN");
        when(usuarioRepository.findById("1")).thenReturn(Optional.of(admin));
        when(usuarioLogado.username()).thenReturn("admin");

        assertThrows(RegraNegocioException.class,
                () -> service.atualizarUsuario("1", usuario(null, "admin", "GERENTE")));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void naoAceitaPerfilInexistente() {
        when(usuarioRepository.existsByUsername("novo")).thenReturn(false);
        Usuario novo = usuario(null, "novo", "SUPERUSER");
        novo.setPassword("123456");

        assertThrows(RegraNegocioException.class, () -> service.salvarUsuario(novo));
    }

    @Test
    void naoRemoveOUltimoAdmin() {
        Usuario outroAdmin = usuario("2", "admin2", "ADMIN");
        when(usuarioRepository.findById("2")).thenReturn(Optional.of(outroAdmin));
        when(usuarioLogado.username()).thenReturn("admin");
        when(usuarioRepository.countByRole("ADMIN")).thenReturn(1L);

        assertThrows(RegraNegocioException.class, () -> service.deletarUsuario("2"));
    }

    @Test
    void criaUsuarioComSenhaCriptografadaEPerfilNormalizado() {
        Usuario novo = usuario(null, "atendente02", "atendente");
        novo.setPassword("segredo1");

        Usuario salvo = service.salvarUsuario(novo);

        assertEquals("ATENDENTE", salvo.getRole());
        assertEquals("hash-segredo1", salvo.getPassword());
    }

    private Usuario usuario(String id, String username, String role) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setUsername(username);
        u.setRole(role);
        return u;
    }
}
