// Acoes dos pedidos (usadas na lista e no detalhe).
// Os botoes so aparecem para quem tem o perfil, mas a validacao
// de verdade acontece no backend (SecurityConfig + PedidoService).

async function executarAcaoPedido(promessa, mensagemSucesso) {
    try {
        await promessa;
        alert(mensagemSucesso);
        window.location.reload();
    } catch (e) {
        alert('Não foi possível concluir: ' + e.message);
    }
}

function avancarPedido(id, proximoDescricao) {
    if (!confirm('Mover o pedido para "' + proximoDescricao + '"?')) return;
    executarAcaoPedido(
        apiFetch('/api/pedidos/' + encodeURIComponent(id) + '/avancar', { method: 'PUT' }),
        'Status atualizado.'
    );
}

function cancelarPedido(id) {
    const justificativa = prompt('Justificativa do cancelamento (mínimo 10 caracteres):');
    if (justificativa === null) return;
    executarAcaoPedido(
        apiFetch('/api/pedidos/' + encodeURIComponent(id) + '/cancelamento', {
            method: 'POST',
            body: { justificativa: justificativa }
        }),
        'Pedido cancelado.'
    );
}

function descontoPedido(id) {
    const valor = prompt('Percentual de desconto (máximo 20%):');
    if (valor === null) return;
    const percentual = Number(String(valor).replace('%', '').replace(',', '.').trim());
    if (!Number.isFinite(percentual)) {
        alert('Informe um número válido.');
        return;
    }
    const justificativa = prompt('Motivo do desconto (opcional):') || '';
    executarAcaoPedido(
        apiFetch('/api/pedidos/' + encodeURIComponent(id) + '/desconto', {
            method: 'POST',
            body: { percentual: percentual, justificativa: justificativa }
        }),
        'Desconto aplicado.'
    );
}
