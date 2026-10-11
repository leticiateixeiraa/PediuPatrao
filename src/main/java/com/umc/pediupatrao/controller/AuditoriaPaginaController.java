package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.auditoria.AuditoriaService;
import com.umc.pediupatrao.auditoria.FiltroAuditoria;
import com.umc.pediupatrao.auditoria.RegistroAuditoria;
import com.umc.pediupatrao.auditoria.TipoOperacao;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Tela de consulta da auditoria (ADMIN e GERENTE).
 */
@Controller
public class AuditoriaPaginaController {

    private static final int TAMANHO_PAGINA = 20;

    private final AuditoriaService auditoriaService;

    public AuditoriaPaginaController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @GetMapping("/auditoria")
    public String auditoria(
            @RequestParam(required = false) String entidade,
            @RequestParam(required = false) String entidadeId,
            @RequestParam(required = false) TipoOperacao operacao,
            @RequestParam(required = false) String usuario,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(defaultValue = "0") int pagina,
            Model model) {

        FiltroAuditoria filtro = new FiltroAuditoria(entidade, entidadeId, operacao, usuario,
                dataInicio != null ? dataInicio.atStartOfDay() : null,
                dataFim != null ? dataFim.atTime(23, 59, 59) : null);
        Page<RegistroAuditoria> page = auditoriaService.consultar(filtro, pagina, TAMANHO_PAGINA);

        model.addAttribute("registros", page.getContent());
        model.addAttribute("pagina", page.getNumber());
        model.addAttribute("totalPaginas", page.getTotalPages());
        model.addAttribute("totalRegistros", page.getTotalElements());
        model.addAttribute("operacoes", TipoOperacao.values());

        // devolve os filtros para manter o formulario preenchido
        model.addAttribute("fEntidade", entidade);
        model.addAttribute("fEntidadeId", entidadeId);
        model.addAttribute("fOperacao", operacao);
        model.addAttribute("fUsuario", usuario);
        model.addAttribute("fDataInicio", dataInicio);
        model.addAttribute("fDataFim", dataFim);

        model.addAttribute("content", "auditoria/lista :: content");
        return "layout";
    }
}
