package com.umc.pediupatrao.dashboard;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Tela do dashboard. A pagina so monta a estrutura; os dados vem da API /api/dashboard
 * (carregados via JavaScript em static/js/dashboard.js).
 */
@Controller
public class DashboardPaginaController {

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("content", "dashboard :: content");
        return "layout";
    }
}
