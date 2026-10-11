package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.auditoria.AuditoriaService;
import com.umc.pediupatrao.auditoria.FiltroAuditoria;
import com.umc.pediupatrao.auditoria.RegistroAuditoria;
import com.umc.pediupatrao.auditoria.TipoOperacao;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta da auditoria - somente leitura (GET), ADMIN e GERENTE.
 * Nao existe POST/PUT/DELETE aqui e o SecurityConfig nega qualquer outro metodo.
 */
@RestController
@RequestMapping("/api/auditoria")
public class AuditoriaController {

    private final AuditoriaService auditoriaService;

    public AuditoriaController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    // ex: /api/auditoria?entidade=CLIENTE&operacao=ALTERACAO_CLIENTE&pagina=0
    @GetMapping
    public Map<String, Object> consultar(
            @RequestParam(required = false) String entidade,
            @RequestParam(required = false) String entidadeId,
            @RequestParam(required = false) TipoOperacao operacao,
            @RequestParam(required = false) String usuario,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho) {

        FiltroAuditoria filtro = new FiltroAuditoria(entidade, entidadeId, operacao, usuario,
                dataInicio != null ? dataInicio.atStartOfDay() : null,
                dataFim != null ? dataFim.atTime(23, 59, 59) : null);
        Page<RegistroAuditoria> page = auditoriaService.consultar(filtro, pagina, tamanho);

        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("registros", page.getContent());
        resposta.put("pagina", page.getNumber());
        resposta.put("tamanho", page.getSize());
        resposta.put("totalRegistros", page.getTotalElements());
        resposta.put("totalPaginas", page.getTotalPages());
        return resposta;
    }

    @GetMapping("/{id}")
    public RegistroAuditoria buscarPorId(@PathVariable String id) {
        return auditoriaService.buscarPorId(id);
    }
}
