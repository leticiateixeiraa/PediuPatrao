package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.auditoria.AlteracaoCampo;
import com.umc.pediupatrao.auditoria.Alteracoes;
import com.umc.pediupatrao.auditoria.AuditoriaService;
import com.umc.pediupatrao.auditoria.TipoOperacao;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClienteServiceTest {

    @Mock
    private ClienteRepository clienteRepository;
    @Mock
    private AuditoriaService auditoriaService;

    private ClienteService service;

    @BeforeEach
    void setUp() {
        service = new ClienteService(clienteRepository, auditoriaService);
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void auditaSomenteOsCamposAlteradosComValorAnteriorENovo() {
        Cliente antigo = cliente("c1", "Rua das Flores", "(11) 98765-4321");
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(antigo));

        Cliente novo = cliente("c1", "Av. Brasil", "(11) 98765-4321");
        service.salvar(novo);

        ArgumentCaptor<Alteracoes> captor = ArgumentCaptor.forClass(Alteracoes.class);
        verify(auditoriaService).registrar(eq(TipoOperacao.ALTERACAO_CLIENTE), eq("CLIENTE"), eq("c1"),
                captor.capture(), isNull());

        List<AlteracaoCampo> campos = captor.getValue().lista();
        assertEquals(1, campos.size());
        assertEquals("logradouro", campos.get(0).getCampo());
        assertEquals("Rua das Flores", campos.get(0).getValorAnterior());
        assertEquals("Av. Brasil", campos.get(0).getValorNovo());
    }

    @Test
    void telefoneAlteradoVaiMascaradoParaAuditoria() {
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(cliente("c1", "Rua A", "(11) 98765-4321")));

        service.salvar(cliente("c1", "Rua A", "(11) 91234-5678"));

        ArgumentCaptor<Alteracoes> captor = ArgumentCaptor.forClass(Alteracoes.class);
        verify(auditoriaService).registrar(eq(TipoOperacao.ALTERACAO_CLIENTE), anyString(), anyString(),
                captor.capture(), isNull());
        AlteracaoCampo telefone = captor.getValue().lista().get(0);
        assertEquals("telefone", telefone.getCampo());
        assertEquals("(11) *****-4321", telefone.getValorAnterior());
        assertEquals("(11) *****-5678", telefone.getValorNovo());
    }

    @Test
    void semAlteracaoNaoGeraAuditoria() {
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(cliente("c1", "Rua A", "(11) 98765-4321")));

        service.salvar(cliente("c1", "Rua A", "(11) 98765-4321"));

        verify(auditoriaService, never()).registrar(any(), any(), any(), any(), any());
    }

    @Test
    void inclusaoDeClienteEAuditada() {
        Cliente novo = cliente(null, "Rua B", "(11) 3333-4444");
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(inv -> {
            Cliente c = inv.getArgument(0);
            c.setId("novo-id");
            return c;
        });

        service.salvar(novo);

        verify(auditoriaService).registrar(eq(TipoOperacao.INCLUSAO_CLIENTE), eq("CLIENTE"), eq("novo-id"), any(), isNull());
    }

    @Test
    void validaTelefone() {
        assertThrows(RegraNegocioException.class, () -> service.salvar(cliente(null, "Rua", "1234")));
    }

    private Cliente cliente(String id, String logradouro, String telefone) {
        Cliente c = new Cliente();
        c.setId(id);
        c.setNome("João da Silva");
        c.setTelefone(telefone);
        c.setLogradouro(logradouro);
        c.setNumero("100");
        c.setCidade("São Paulo");
        c.setEstado("SP");
        return c;
    }
}
