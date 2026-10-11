package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Cria um usuario de cada perfil na primeira execucao, pra facilitar os testes.
 * As senhas iniciais devem ser trocadas depois pela tela de usuarios.
 */
@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    CommandLineRunner initDatabase(UsuarioRepository repo, ProdutoRepository produtoRepo, PasswordEncoder encoder) {
        return args -> {
            criarSeNaoExistir(repo, encoder, "admin", "teste123", Perfil.ADMIN);
            criarSeNaoExistir(repo, encoder, "gerente", "teste123", Perfil.GERENTE);
            criarSeNaoExistir(repo, encoder, "atendente01", "teste123", Perfil.ATENDENTE);

            // cardapio basico pra conseguir lancar pedido numa base nova
            if (produtoRepo.count() == 0) {
                produtoRepo.save(produto("Pizza Calabresa", "Pizza", 45.90));
                produtoRepo.save(produto("Pizza Mussarela", "Pizza", 42.00));
                produtoRepo.save(produto("Pizza Portuguesa", "Pizza", 52.50));
                produtoRepo.save(produto("Esfiha de Carne", "Esfiha", 6.50));
                produtoRepo.save(produto("Refrigerante 2L", "Bebida", 12.00));
                log.info("Produtos iniciais cadastrados");
            }
        };
    }

    private Produto produto(String nome, String tipo, double preco) {
        Produto p = new Produto();
        p.setNome(nome);
        p.setTipo(tipo);
        p.setPreco(preco);
        p.setAtivo(true);
        return p;
    }

    private void criarSeNaoExistir(UsuarioRepository repo, PasswordEncoder encoder,
                                   String username, String senha, Perfil perfil) {
        repo.findByUsername(username).ifPresentOrElse(existente -> {
            // o admin antigo era criado sem role, entao corrige aqui
            if (existente.getRole() == null || existente.getRole().isBlank()) {
                existente.setRole(perfil.name());
                repo.save(existente);
            }
        }, () -> {
            Usuario user = new Usuario();
            user.setUsername(username);
            user.setPassword(encoder.encode(senha));
            user.setRole(perfil.name());
            repo.save(user);
            log.info("Usuario inicial criado: {} ({})", username, perfil);
        });
    }
}
