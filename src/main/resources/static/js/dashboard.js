(() => {
    const form = document.getElementById('dashboardFilters');
    if (!form) return;

    const startInput = document.getElementById('dashboardStart');
    const endInput = document.getElementById('dashboardEnd');
    const errorBox = document.getElementById('dashboardError');
    const loading = document.getElementById('dashboardLoading');
    const updated = document.getElementById('dashboardUpdated');
    const content = document.getElementById('dashboardContent');
    const currency = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
    const integer = new Intl.NumberFormat('pt-BR');
    let chart;

    function setDefaultDates() {
        const today = new Date();
        const localDate = (date) => {
            const year = date.getFullYear();
            const month = String(date.getMonth() + 1).padStart(2, '0');
            const day = String(date.getDate()).padStart(2, '0');
            return `${year}-${month}-${day}`;
        };
        startInput.value = localDate(new Date(today.getFullYear(), today.getMonth(), 1));
        endInput.value = localDate(today);
    }

    function showEmptyRow(columns, message) {
        return `<tr><td colspan="${columns}" class="text-center text-muted py-4">${message}</td></tr>`;
    }

    function render(data) {
        const metrics = data.indicadores;
        document.getElementById('metricRevenue').textContent = currency.format(metrics.faturamento);
        document.getElementById('metricOrders').textContent = integer.format(metrics.totalPedidos);
        document.getElementById('metricCompleted').textContent =
            `${integer.format(metrics.pedidosFinalizados)} finalizados`;
        document.getElementById('metricInProgress').textContent = integer.format(metrics.pedidosEmAndamento);
        document.getElementById('metricTicket').textContent = currency.format(metrics.ticketMedio);
        document.getElementById('metricCanceled').textContent =
            `${integer.format(metrics.pedidosCancelados)} cancelados`;

        document.getElementById('orderStatusRows').innerHTML = data.pedidosPorStatus
            .map((item) => `<tr><td>${escapeHtml(item.descricao)}</td><td class="text-end fw-semibold">${integer.format(item.quantidade)}</td></tr>`)
            .join('');

        document.getElementById('topProductRows').innerHTML = data.produtosMaisVendidos.length
            ? data.produtosMaisVendidos.map((item) =>
                `<tr><td>${escapeHtml(item.nomeProduto)}</td><td class="text-end">${integer.format(item.quantidadeVendida)}</td></tr>`
            ).join('')
            : showEmptyRow(2, 'Nenhuma venda finalizada neste período.');

        document.getElementById('recentOrderRows').innerHTML = data.pedidosRecentes.length
            ? data.pedidosRecentes.map((item) =>
                `<tr>
                    <td><a href="/pedidos/${encodeURIComponent(item.id)}" class="fw-semibold text-decoration-none">#${escapeHtml(item.id)}</a></td>
                    <td>${escapeHtml(item.clienteNome || 'Cliente não informado')}</td>
                    <td><span class="dashboard-status">${escapeHtml(item.statusDescricao)}</span></td>
                    <td class="text-end">${currency.format(item.valorTotal)}</td>
                </tr>`
            ).join('')
            : showEmptyRow(4, 'Nenhum pedido encontrado neste período.');

        renderChart(data.vendasPorPeriodo);
        updated.textContent = `Atualizado em ${new Intl.DateTimeFormat('pt-BR', {
            dateStyle: 'short',
            timeStyle: 'short'
        }).format(new Date())}`;
    }

    function renderChart(series) {
        if (chart) chart.destroy();
        chart = new Chart(document.getElementById('salesChart').getContext('2d'), {
            type: 'line',
            data: {
                labels: series.map((item) => item.periodo),
                datasets: [{
                    label: 'Faturamento',
                    data: series.map((item) => item.faturamento),
                    borderColor: '#5b5bd6',
                    backgroundColor: 'rgba(91, 91, 214, 0.12)',
                    borderWidth: 3,
                    pointRadius: 3,
                    pointBackgroundColor: '#5b5bd6',
                    fill: true,
                    tension: 0.25
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                legend: { display: false },
                tooltips: {
                    callbacks: {
                        label: (tooltip) => currency.format(tooltip.yLabel)
                    }
                },
                scales: {
                    yAxes: [{
                        ticks: {
                            beginAtZero: true,
                            callback: (value) => currency.format(value)
                        },
                        gridLines: { color: 'rgba(120, 130, 150, 0.12)' }
                    }],
                    xAxes: [{ gridLines: { display: false } }]
                }
            }
        });
    }

    async function loadDashboard() {
        errorBox.classList.add('d-none');
        loading.classList.remove('d-none');
        content.classList.add('opacity-50');
        try {
            const params = new URLSearchParams({ inicio: startInput.value, fim: endInput.value });
            const data = await apiFetch(`/api/dashboard?${params.toString()}`);
            render(data);
        } catch (error) {
            errorBox.textContent = `Não foi possível carregar o dashboard: ${error.message}`;
            errorBox.classList.remove('d-none');
        } finally {
            loading.classList.add('d-none');
            content.classList.remove('opacity-50');
        }
    }

    form.addEventListener('submit', (event) => {
        event.preventDefault();
        if (!form.reportValidity()) return;
        if (startInput.value > endInput.value) {
            errorBox.textContent = 'A data inicial não pode ser posterior à data final.';
            errorBox.classList.remove('d-none');
            return;
        }
        loadDashboard();
    });

    setDefaultDates();
    loadDashboard();
    window.setInterval(() => {
        if (!document.hidden) loadDashboard();
    }, 60_000);
})();
