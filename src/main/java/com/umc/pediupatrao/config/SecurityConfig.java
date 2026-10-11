package com.umc.pediupatrao.config;

import com.umc.pediupatrao.service.UsuarioDetailsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.Principal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity // libera o uso de @PreAuthorize nos services
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private static final String ADMIN = "ADMIN";
    private static final String GERENTE = "GERENTE";
    private static final String ATENDENTE = "ATENDENTE";

    private final UsuarioDetailsService usuarioDetailsService;

    public SecurityConfig(UsuarioDetailsService usuarioDetailsService) {
        this.usuarioDetailsService = usuarioDetailsService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(usuarioDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF fica ligado (padrao do Spring). Os formularios Thymeleaf recebem o token
                // automaticamente e as chamadas fetch mandam o header X-CSRF-TOKEN (ver script.js)
                .csrf(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/error", "/css/**", "/dist/**", "/plugins/**", "/js/**", "/images/**").permitAll()

                        // ===== usuarios: so ADMIN =====
                        .requestMatchers("/usuarios/**", "/api/usuarios/**", "/admin").hasRole(ADMIN)

                        // ===== auditoria: ADMIN e GERENTE, somente leitura =====
                        .requestMatchers(HttpMethod.GET, "/auditoria/**", "/api/auditoria/**").hasAnyRole(ADMIN, GERENTE)
                        .requestMatchers("/auditoria/**", "/api/auditoria/**").denyAll()

                        // ===== pedidos =====
                        // cancelamento e desconto: exclusivo do GERENTE (nem o ADMIN pode)
                        .requestMatchers(HttpMethod.POST, "/api/pedidos/*/cancelamento", "/api/pedidos/*/desconto").hasRole(GERENTE)
                        .requestMatchers(HttpMethod.GET, "/api/pedidos/**").hasAnyRole(ADMIN, GERENTE, ATENDENTE)
                        .requestMatchers(HttpMethod.POST, "/api/pedidos").hasAnyRole(GERENTE, ATENDENTE)
                        .requestMatchers(HttpMethod.PUT, "/api/pedidos/**").hasAnyRole(GERENTE, ATENDENTE)
                        .requestMatchers("/api/pedidos/**").denyAll()
                        .requestMatchers("/pedidos/novo", "/pedidos/*/editar").hasAnyRole(GERENTE, ATENDENTE)
                        .requestMatchers("/pedidos/**").hasAnyRole(ADMIN, GERENTE, ATENDENTE)

                        // ===== clientes =====
                        .requestMatchers(HttpMethod.GET, "/api/clientes/**").hasAnyRole(GERENTE, ATENDENTE)
                        .requestMatchers(HttpMethod.POST, "/api/clientes").hasRole(ATENDENTE)
                        .requestMatchers(HttpMethod.PUT, "/api/clientes/**").hasRole(ATENDENTE)
                        .requestMatchers(HttpMethod.DELETE, "/api/clientes/**").hasRole(GERENTE)
                        .requestMatchers("/api/clientes/**").denyAll()
                        .requestMatchers("/clientes/novo", "/clientes/salvar", "/clientes/editar/**").hasRole(ATENDENTE)
                        .requestMatchers("/clientes/**").hasAnyRole(GERENTE, ATENDENTE)

                        // ===== produtos: todos consultam, ADMIN/GERENTE alteram =====
                        .requestMatchers(HttpMethod.GET, "/api/produtos/**", "/produtos/**").authenticated()
                        .requestMatchers("/api/produtos/**").hasAnyRole(ADMIN, GERENTE)

                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(apiAuthenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler())
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .permitAll()
                        .successHandler(authenticationSuccessHandler())
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                );

        return http.build();
    }

    @Bean
    public AuthenticationSuccessHandler authenticationSuccessHandler() {
        return (request, response, authentication) -> {
            response.sendRedirect("/"); // Redireciona para a página inicial após login bem-sucedido
        };
    }

    /**
     * Sem login: API responde 401 em JSON, paginas vao para a tela de login.
     */
    private AuthenticationEntryPoint apiAuthenticationEntryPoint() {
        return (request, response, authException) -> {
            if (ehApi(request)) {
                escreverJson(response, HttpServletResponse.SC_UNAUTHORIZED, "Não autenticado");
            } else {
                response.sendRedirect("/login");
            }
        };
    }

    /**
     * Logado mas sem permissao: registra a tentativa no log e devolve 403.
     */
    private AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            Principal usuario = request.getUserPrincipal();
            log.warn("Acesso negado: usuario={} {} {}",
                    usuario != null ? usuario.getName() : "anonimo",
                    request.getMethod(), request.getRequestURI());

            if (ehApi(request)) {
                escreverJson(response, HttpServletResponse.SC_FORBIDDEN,
                        "Seu perfil não tem permissão para esta operação");
            } else {
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
            }
        };
    }

    private static boolean ehApi(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/");
    }

    private static void escreverJson(HttpServletResponse response, int status, String mensagem) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"status\":" + status + ",\"erro\":\"" + mensagem + "\"}");
    }
}
