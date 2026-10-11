package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.CancelamentoRequest;
import com.umc.pediupatrao.dto.DescontoRequest;
import com.umc.pediupatrao.dto.PedidoRequest;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.service.PedidoService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    private final PedidoService pedidoService;

    public PedidoController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    // recebe so cliente, tipo, observacao e itens (produto + quantidade).
    // qualquer "valorTotal" enviado no JSON e ignorado
    @PostMapping
    public ResponseEntity<Pedido> criarPedido(@RequestBody PedidoRequest request) {
        Pedido criado = pedidoService.criarPedido(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(criado);
    }

    @GetMapping
    public List<Pedido> listarPedidos() {
        return pedidoService.listarPedidos();
    }

    @GetMapping("/{id}")
    public Pedido buscarPorId(@PathVariable String id) {
        return pedidoService.buscarPorId(id);
    }

    // altera itens/tipo/observacao enquanto o pedido esta RECEBIDO
    @PutMapping("/{id}")
    public Pedido alterarPedido(@PathVariable String id, @RequestBody PedidoRequest request) {
        return pedidoService.alterarPedido(id, request);
    }

    // ex: PUT /api/pedidos/{id}/status?status=EM_PREPARACAO
    @PutMapping("/{id}/status")
    public Pedido atualizarStatus(@PathVariable String id, @RequestParam StatusPedido status) {
        return pedidoService.atualizarStatus(id, status);
    }

    // somente GERENTE (SecurityConfig + @PreAuthorize no service)
    @PostMapping("/{id}/cancelamento")
    public Pedido cancelar(@PathVariable String id, @RequestBody CancelamentoRequest request) {
        return pedidoService.cancelar(id, request.justificativa());
    }

    // somente GERENTE, ate 20%
    @PostMapping("/{id}/desconto")
    public Pedido aplicarDesconto(@PathVariable String id, @RequestBody DescontoRequest request) {
        return pedidoService.aplicarDesconto(id, request.percentual(), request.justificativa());
    }

    // move para o proximo status do fluxo
    @PutMapping("/{id}/avancar")
    public Pedido avancarStatus(@PathVariable String id) {
        return pedidoService.avancarStatus(id);
    }
}
