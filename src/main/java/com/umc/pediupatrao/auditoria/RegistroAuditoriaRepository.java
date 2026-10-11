package com.umc.pediupatrao.auditoria;

import java.util.Optional;
import org.springframework.data.repository.Repository;

/**
 * Repositorio "somente inclusao": estende Repository (e nao MongoRepository)
 * e expoe so insert e consultas. Nao existe save/update/delete aqui, entao
 * nenhuma parte do sistema consegue alterar ou apagar um registro de auditoria.
 * As consultas com filtro ficam no AuditoriaService (MongoTemplate).
 */
public interface RegistroAuditoriaRepository extends Repository<RegistroAuditoria, String> {

    // insert falha se o id ja existir, ou seja, nao sobrescreve registro antigo
    <S extends RegistroAuditoria> S insert(S registro);

    Optional<RegistroAuditoria> findById(String id);

    long count();
}
