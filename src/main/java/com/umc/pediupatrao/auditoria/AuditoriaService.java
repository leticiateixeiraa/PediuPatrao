package com.umc.pediupatrao.auditoria;

import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.security.UsuarioLogado;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Grava e consulta os registros de auditoria.
 * - registrar(): chamado pelos services de pedido, cliente e usuario
 * - consultar(): somente ADMIN e GERENTE
 * Nao existe metodo de alterar/excluir.
 */
@Service
public class AuditoriaService {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaService.class);

    // nomes de campo que nunca podem ir para a auditoria
    private static final Set<String> CAMPOS_PROIBIDOS = Set.of("senha", "password", "token", "credencial", "secret");

    private static final int TAMANHO_MAXIMO_PAGINA = 100;

    private final RegistroAuditoriaRepository repository;
    private final MongoTemplate mongoTemplate;
    private final UsuarioLogado usuarioLogado;
    private final Clock clock;

    public AuditoriaService(RegistroAuditoriaRepository repository, MongoTemplate mongoTemplate,
                            UsuarioLogado usuarioLogado, Clock clock) {
        this.repository = repository;
        this.mongoTemplate = mongoTemplate;
        this.usuarioLogado = usuarioLogado;
        this.clock = clock;
    }

    public RegistroAuditoria registrar(TipoOperacao operacao, String entidade, String entidadeId,
                                       Alteracoes alteracoes, String justificativa) {
        List<AlteracaoCampo> campos = alteracoes == null ? List.of()
                : alteracoes.lista().stream().filter(c -> !campoProibido(c.getCampo())).toList();

        Perfil perfil = usuarioLogado.perfil();
        RegistroAuditoria registro = new RegistroAuditoria(
                null,
                LocalDateTime.now(clock),
                usuarioLogado.username(),
                perfil != null ? perfil.name() : "SISTEMA",
                operacao,
                entidade,
                entidadeId,
                campos,
                justificativa);

        RegistroAuditoria salvo = repository.insert(registro);
        // no log vai so o basico, sem valores (podem ter dado pessoal)
        log.info("Auditoria: {} {} {} por {}", operacao, entidade, entidadeId, registro.getUsuario());
        return salvo;
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public Page<RegistroAuditoria> consultar(FiltroAuditoria filtro, int pagina, int tamanho) {
        int tamanhoValido = Math.min(Math.max(tamanho, 1), TAMANHO_MAXIMO_PAGINA);
        Pageable pageable = PageRequest.of(Math.max(pagina, 0), tamanhoValido,
                Sort.by(Sort.Direction.DESC, "dataHora"));

        Query query = new Query();
        if (filtro != null) {
            if (temValor(filtro.entidade())) {
                query.addCriteria(Criteria.where("entidade").is(filtro.entidade().trim().toUpperCase(Locale.ROOT)));
            }
            if (temValor(filtro.entidadeId())) {
                query.addCriteria(Criteria.where("entidadeId").is(filtro.entidadeId().trim()));
            }
            if (filtro.operacao() != null) {
                query.addCriteria(Criteria.where("operacao").is(filtro.operacao()));
            }
            if (temValor(filtro.usuario())) {
                query.addCriteria(Criteria.where("usuario").is(filtro.usuario().trim()));
            }
            if (filtro.inicio() != null || filtro.fim() != null) {
                Criteria data = Criteria.where("dataHora");
                if (filtro.inicio() != null) {
                    data = data.gte(filtro.inicio());
                }
                if (filtro.fim() != null) {
                    data = data.lte(filtro.fim());
                }
                query.addCriteria(data);
            }
        }

        // conta antes de aplicar a paginacao na query
        long total = mongoTemplate.count(query, RegistroAuditoria.class);
        List<RegistroAuditoria> registros = mongoTemplate.find(query.with(pageable), RegistroAuditoria.class);
        return PageableExecutionUtils.getPage(registros, pageable, () -> total);
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public RegistroAuditoria buscarPorId(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Registro de auditoria não encontrado."));
    }

    private static boolean campoProibido(String campo) {
        if (campo == null) {
            return false;
        }
        String nome = campo.toLowerCase(Locale.ROOT);
        return CAMPOS_PROIBIDOS.stream().anyMatch(nome::contains);
    }

    private static boolean temValor(String valor) {
        return valor != null && !valor.isBlank();
    }
}
