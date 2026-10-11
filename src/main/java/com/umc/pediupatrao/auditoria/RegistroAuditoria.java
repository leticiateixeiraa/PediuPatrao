package com.umc.pediupatrao.auditoria;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Evento de auditoria gravado na colecao "auditoria".
 * A classe nao tem setters de proposito: depois de criado o registro nao muda.
 */
@Document(collection = "auditoria")
public class RegistroAuditoria {

    @Id
    private final String id;
    private final LocalDateTime dataHora;
    private final String usuario;
    private final String perfil;
    private final TipoOperacao operacao;
    private final String entidade;
    private final String entidadeId;
    private final List<AlteracaoCampo> alteracoes;
    private final String justificativa;

    @PersistenceCreator
    public RegistroAuditoria(String id, LocalDateTime dataHora, String usuario, String perfil,
                             TipoOperacao operacao, String entidade, String entidadeId,
                             List<AlteracaoCampo> alteracoes, String justificativa) {
        this.id = id;
        this.dataHora = dataHora;
        this.usuario = usuario;
        this.perfil = perfil;
        this.operacao = operacao;
        this.entidade = entidade;
        this.entidadeId = entidadeId;
        this.alteracoes = alteracoes == null ? List.of() : List.copyOf(alteracoes);
        this.justificativa = justificativa;
    }

    public String getId() { return id; }
    public LocalDateTime getDataHora() { return dataHora; }
    public String getUsuario() { return usuario; }
    public String getPerfil() { return perfil; }
    public TipoOperacao getOperacao() { return operacao; }
    public String getEntidade() { return entidade; }
    public String getEntidadeId() { return entidadeId; }
    public List<AlteracaoCampo> getAlteracoes() { return alteracoes; }
    public String getJustificativa() { return justificativa; }
}
