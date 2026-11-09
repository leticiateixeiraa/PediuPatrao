package com.umc.pediupatrao.repository;

import com.umc.pediupatrao.entity.Pedido;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface PedidoRepository extends MongoRepository<Pedido, String> {
    List<Pedido> findByClienteId(String clienteId);

    @Query("{ 'dataHoraEntrada': { '$gte': ?0, '$lt': ?1 } }")
    List<Pedido> buscarPorDataHoraEntradaNoIntervalo(
            LocalDateTime inicio, LocalDateTime fimExclusivo);
}
