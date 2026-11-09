// Dashboard administrativo - consome GET /api/dashboard (RF06)
(function () {
    const INTERVALO_ATUALIZACAO_MS = 60000;

    const CORES_STATUS = {
        RECEBIDO: '#6c757d',
        EM_PREPARACAO: '#ffc107',
        PRONTO: '#0dcaf0',
        SAIU_PARA_ENTREGA: '#0d6efd',
        RETIRADO: '#6610f2',
        FINALIZADO: '#198754',
        CANCELADO: '#dc3545'
    };

    const moeda = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
    const numero = new Intl.NumberFormat('pt-BR');
    const el = id => document.getElementById(id);

    let graficos = {};
    let carregando = false;
    let timer = null;

    // ---------- datas ----------
    function isoLocal(data) {
        const y = data.getFullYear();
        const m = String(data.getMonth() + 1).padStart(2, '0');
        const d = String(data.getDate()).padStart(2, '0');
        return `${y}-${m}-${d}`;
    }

    function definirPeriodo(dias) {
        const fim = new Date();
        const inicio = new Date();
        inicio.setDate(fim.getDate() - (dias - 1));
        el('fDataInicio').value = isoLocal(inicio);
        el('fDataFim').value = isoLocal(fim);
    }

    function formatarDataHora(texto) {
        if (!texto) return '—';
        const d = new Date(texto);
        if (isNaN(d)) return texto;
        return d.toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' }) + ' '
            + d.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
    }

    // ---------- filtros (RF05) ----------
    function montarQuery() {
        const params = new URLSearchParams();
        const campos = {
            dataInicio: el('fDataInicio').value,
            dataFim: el('fDataFim').value,
            status: el('fStatus').value,
            tipoEntrega: el('fTipo').value,
            agrupamento: el('fAgrupamento').value
        };
        Object.entries(campos).forEach(([k, v]) => { if (v) params.set(k, v); });
        params.set('limiteProdutos', '5');
        params.set('limiteRecentes', '8');
        return params.toString();
    }

    function validarFiltros() {
        const ini = el('fDataInicio').value;
        const fim = el('fDataFim').value;
        if (ini && fim && ini > fim) {
            mostrarErro('A data inicial não pode ser posterior à data final.');
            return false;
        }
        return true;
    }

    // ---------- carga (RF06 / RF07) ----------
    async function carregar() {
        if (carregando || !validarFiltros()) return;
        carregando = true;
        esconderErro();
        el('carregando').classList.remove('d-none');
        el('btnAtualizar').disabled = true;

        try {
            const dados = await apiFetch('/api/dashboard?' + montarQuery());
            renderizar(dados);
            const hora = dados.geradoEm ? new Date(dados.geradoEm) : new Date();
            el('ultimaAtualizacao').textContent = 'Atualizado às ' + hora.toLocaleTimeString('pt-BR');
        } catch (e) {
            if (e.status === 401) {
                window.location.href = '/login';
                return;
            }
            mostrarErro(e.status === 403
                ? 'Seu perfil não tem acesso ao dashboard.'
                : 'Não foi possível carregar o dashboard: ' + e.message);
        } finally {
            carregando = false;
            el('carregando').classList.add('d-none');
            el('btnAtualizar').disabled = false;
        }
    }

    function mostrarErro(msg) {
        const box = el('erroDashboard');
        box.textContent = msg;
        box.classList.remove('d-none');
    }

    function esconderErro() {
        el('erroDashboard').classList.add('d-none');
    }

    // ---------- renderizacao ----------
    function renderizar(d) {
        renderizarIndicadores(d.resumo || {});
        renderizarVendas(d.vendasPorPeriodo || []);
        renderizarStatus(d.situacaoPedidos || []);
        renderizarProdutos(d.produtosMaisVendidos || []);
        renderizarTipo(d.vendasPorTipoEntrega || []);
        renderizarRecentes(d.pedidosRecentes || []);
    }

    function renderizarIndicadores(r) {
        el('kpiFaturamento').textContent = moeda.format(r.faturamento || 0);
        el('kpiPedidos').textContent = numero.format(r.totalPedidos || 0);
        el('kpiPedidosSub').textContent = numero.format(r.pedidosValidos || 0) + ' válidos';
        el('kpiTicket').textContent = moeda.format(r.ticketMedio || 0);
        el('kpiItens').textContent = numero.format(r.itensVendidos || 0) + ' itens vendidos';
        el('kpiAndamento').textContent = numero.format(r.pedidosEmAndamento || 0);
        el('kpiCancelados').textContent = numero.format(r.pedidosCancelados || 0);
        el('kpiTaxaCancelamento').textContent = String(r.taxaCancelamento || 0).replace('.', ',') + '% dos pedidos';
        el('kpiDescontos').textContent = moeda.format(r.totalDescontos || 0);
    }

    function criarOuAtualizar(nome, canvasId, config) {
        if (typeof Chart === 'undefined') {
            mostrarErro('A biblioteca de gráficos não carregou (verifique a conexão com a internet).');
            return;
        }
        if (graficos[nome]) {
            graficos[nome].data = config.data;
            graficos[nome].update();
        } else {
            graficos[nome] = new Chart(el(canvasId), config);
        }
    }

    function renderizarVendas(lista) {
        criarOuAtualizar('vendas', 'graficoVendas', {
            type: 'bar',
            data: {
                labels: lista.map(v => v.rotulo),
                datasets: [
                    {
                        type: 'line',
                        label: 'Faturamento (R$)',
                        data: lista.map(v => Number(v.faturamento)),
                        borderColor: '#0d6efd',
                        backgroundColor: 'rgba(13,110,253,.15)',
                        fill: true,
                        tension: .3,
                        yAxisID: 'y'
                    },
                    {
                        label: 'Pedidos',
                        data: lista.map(v => v.quantidadePedidos),
                        backgroundColor: 'rgba(255,193,7,.6)',
                        yAxisID: 'y1'
                    }
                ]
            },
            options: {
                maintainAspectRatio: false,
                interaction: { mode: 'index', intersect: false },
                scales: {
                    y: { beginAtZero: true, position: 'left', ticks: { callback: v => moeda.format(v) } },
                    y1: { beginAtZero: true, position: 'right', grid: { drawOnChartArea: false }, ticks: { precision: 0 } }
                },
                plugins: {
                    tooltip: {
                        callbacks: {
                            label: c => c.dataset.yAxisID === 'y'
                                ? ' Faturamento: ' + moeda.format(c.parsed.y)
                                : ' Pedidos: ' + c.parsed.y
                        }
                    }
                }
            }
        });
    }

    function renderizarStatus(lista) {
        const comPedidos = lista.filter(s => s.quantidade > 0);
        criarOuAtualizar('status', 'graficoStatus', {
            type: 'doughnut',
            data: {
                labels: comPedidos.map(s => s.descricao),
                datasets: [{
                    data: comPedidos.map(s => s.quantidade),
                    backgroundColor: comPedidos.map(s => CORES_STATUS[s.status] || '#adb5bd')
                }]
            },
            options: {
                maintainAspectRatio: false,
                plugins: {
                    legend: { position: 'bottom' },
                    tooltip: {
                        callbacks: {
                            label: c => {
                                const s = comPedidos[c.dataIndex];
                                return ` ${s.descricao}: ${s.quantidade} (${String(s.percentual).replace('.', ',')}%)`;
                            }
                        }
                    }
                }
            }
        });
    }

    function renderizarProdutos(lista) {
        criarOuAtualizar('produtos', 'graficoProdutos', {
            type: 'bar',
            data: {
                labels: lista.map(p => p.nome),
                datasets: [{ label: 'Quantidade', data: lista.map(p => p.quantidade), backgroundColor: '#198754' }]
            },
            options: {
                indexAxis: 'y',
                maintainAspectRatio: false,
                plugins: { legend: { display: false } },
                scales: { x: { beginAtZero: true, ticks: { precision: 0 } } }
            }
        });

        el('tabelaProdutos').innerHTML = lista.length === 0
            ? '<tr><td colspan="4" class="text-center text-muted py-3">Nenhuma venda no período.</td></tr>'
            : lista.map(p => `
                <tr>
                    <td>${p.posicao}º</td>
                    <td>${escapeHtml(p.nome)}</td>
                    <td class="text-end">${numero.format(p.quantidade)}</td>
                    <td class="text-end">${moeda.format(p.faturamento)}
                        <div class="text-muted small">${String(p.participacaoFaturamento).replace('.', ',')}%</div>
                    </td>
                </tr>`).join('');
    }

    function renderizarTipo(lista) {
        criarOuAtualizar('tipo', 'graficoTipo', {
            type: 'bar',
            data: {
                labels: lista.map(t => t.tipoEntrega === 'ENTREGA' ? 'Entrega' : 'Retirada'),
                datasets: [{
                    label: 'Faturamento (R$)',
                    data: lista.map(t => Number(t.faturamento)),
                    backgroundColor: ['#0d6efd', '#6610f2']
                }]
            },
            options: {
                maintainAspectRatio: false,
                plugins: {
                    legend: { display: false },
                    tooltip: {
                        callbacks: {
                            label: c => ` ${moeda.format(c.parsed.y)} em ${lista[c.dataIndex].quantidadePedidos} pedido(s)`
                        }
                    }
                },
                scales: { y: { beginAtZero: true, ticks: { callback: v => moeda.format(v) } } }
            }
        });
    }

    function renderizarRecentes(lista) {
        el('tabelaRecentes').innerHTML = lista.length === 0
            ? '<tr><td colspan="5" class="text-center text-muted py-4">Nenhum pedido no período.</td></tr>'
            : lista.map(p => `
                <tr>
                    <td class="ps-3">${formatarDataHora(p.dataHoraEntrada)}</td>
                    <td>${escapeHtml(p.clienteNome) || '—'}</td>
                    <td>${p.tipoEntrega === 'RETIRADA' ? 'Retirada' : (p.tipoEntrega === 'ENTREGA' ? 'Entrega' : '—')}</td>
                    <td><span class="badge" style="background:${CORES_STATUS[p.status] || '#adb5bd'}">${escapeHtml(p.statusDescricao)}</span></td>
                    <td class="text-end pe-3">${moeda.format(p.valorTotal || 0)}</td>
                </tr>`).join('');
    }

    // ---------- atualizacao automatica (RF07) ----------
    function configurarAutoAtualizacao() {
        clearInterval(timer);
        if (el('autoAtualizar').checked) {
            timer = setInterval(() => {
                if (!document.hidden) carregar();
            }, INTERVALO_ATUALIZACAO_MS);
        }
    }

    // ---------- eventos ----------
    document.addEventListener('DOMContentLoaded', () => {
        definirPeriodo(30);

        el('formFiltros').addEventListener('submit', ev => { ev.preventDefault(); carregar(); });
        el('btnAtualizar').addEventListener('click', carregar);
        el('btnLimpar').addEventListener('click', () => {
            definirPeriodo(30);
            el('fStatus').value = '';
            el('fTipo').value = '';
            el('fAgrupamento').value = 'DIA';
            carregar();
        });
        document.querySelectorAll('.atalho-periodo').forEach(b =>
            b.addEventListener('click', () => { definirPeriodo(Number(b.dataset.dias)); carregar(); }));
        el('fAgrupamento').addEventListener('change', carregar);
        el('autoAtualizar').addEventListener('change', configurarAutoAtualizacao);

        carregar();
        configurarAutoAtualizacao();
    });
})();
