package com.umc.pediupatrao.service;

import com.umc.pediupatrao.auditoria.Alteracoes;
import com.umc.pediupatrao.auditoria.AuditoriaService;
import com.umc.pediupatrao.auditoria.MascaraDados;
import com.umc.pediupatrao.auditoria.TipoOperacao;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
public class ClienteService {

    static final String ENTIDADE = "CLIENTE";

    private final ClienteRepository clienteRepository;
    private final AuditoriaService auditoriaService;

    public ClienteService(ClienteRepository clienteRepository, AuditoriaService auditoriaService) {
        this.clienteRepository = clienteRepository;
        this.auditoriaService = auditoriaService;
    }

    public Cliente novoCliente(Cliente cliente) {
        cliente.setId(null);
        return salvar(cliente);
    }

    public List<Cliente> listarClientes() {
        return clienteRepository.findAll();
    }

    public Optional<Cliente> buscarPorId(String id) {
        return clienteRepository.findById(id);
    }

    // Método para excluir cliente (fica registrado na auditoria)
    public void excluir(String id) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente não encontrado."));
        clienteRepository.deleteById(id);
        auditoriaService.registrar(TipoOperacao.EXCLUSAO_CLIENTE, ENTIDADE, id,
                new Alteracoes().campo("nome", cliente.getNome(), null), null);
    }

    // Método para salvar um novo cliente ou atualizar um cliente existente
    public Cliente salvar(Cliente cliente) {
        validar(cliente);

        // Se o cliente não tem ID (novo cliente), salva como novo
        if (cliente.getId() == null || cliente.getId().isBlank()) {
            cliente.setId(null);
            Cliente salvo = clienteRepository.save(cliente);
            auditoriaService.registrar(TipoOperacao.INCLUSAO_CLIENTE, ENTIDADE, salvo.getId(),
                    compararCampos(new Cliente(), salvo), null);
            return salvo;
        }

        // Se já tem ID, compara com o que está no banco antes de salvar
        Cliente anterior = clienteRepository.findById(cliente.getId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente não encontrado para atualização."));

        Alteracoes alteracoes = compararCampos(anterior, cliente);
        Cliente salvo = clienteRepository.save(cliente);

        if (!alteracoes.vazia()) {
            auditoriaService.registrar(TipoOperacao.ALTERACAO_CLIENTE, ENTIDADE, salvo.getId(), alteracoes, null);
        }
        return salvo;
    }

    /**
     * Campo a campo: anterior x novo. Telefone vai mascarado
     * (a auditoria precisa saber que mudou, nao do numero completo).
     */
    Alteracoes compararCampos(Cliente anterior, Cliente novo) {
        return new Alteracoes()
                .campo("nome", anterior.getNome(), novo.getNome())
                .campoMascarado("telefone", anterior.getTelefone(), novo.getTelefone(), MascaraDados::telefone)
                .campo("cep", anterior.getCep(), novo.getCep())
                .campo("logradouro", anterior.getLogradouro(), novo.getLogradouro())
                .campo("numero", anterior.getNumero(), novo.getNumero())
                .campo("complemento", anterior.getComplemento(), novo.getComplemento())
                .campo("bairro", anterior.getBairro(), novo.getBairro())
                .campo("cidade", anterior.getCidade(), novo.getCidade())
                .campo("estado", anterior.getEstado(), novo.getEstado())
                .campo("endereco", anterior.getEndereco(), novo.getEndereco());
    }

    private void validar(Cliente cliente) {
        if (cliente == null) {
            throw new RegraNegocioException("Dados do cliente não informados.");
        }
        if (cliente.getNome() == null || cliente.getNome().trim().length() < 2) {
            throw new RegraNegocioException("Informe o nome do cliente.");
        }
        if (cliente.getNome().length() > 100) {
            throw new RegraNegocioException("Nome pode ter no máximo 100 caracteres.");
        }
        String digitosTelefone = cliente.getTelefone() == null ? "" : cliente.getTelefone().replaceAll("\\D", "");
        if (digitosTelefone.length() < 10 || digitosTelefone.length() > 11) {
            throw new RegraNegocioException("Telefone inválido. Informe DDD + número.");
        }
        if (cliente.getCep() != null && !cliente.getCep().isBlank()
                && cliente.getCep().replaceAll("\\D", "").length() != 8) {
            throw new RegraNegocioException("CEP inválido.");
        }
        if (cliente.getEstado() != null && !cliente.getEstado().isBlank()) {
            if (!cliente.getEstado().trim().matches("[A-Za-z]{2}")) {
                throw new RegraNegocioException("Estado deve ser a sigla com 2 letras (ex: SP).");
            }
            cliente.setEstado(cliente.getEstado().trim().toUpperCase());
        }
        cliente.setNome(cliente.getNome().trim());
    }
}
