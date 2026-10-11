// Alternar sidebar em telas pequenas
// (o script agora e carregado no <head>, entao espera o DOM)
document.addEventListener('DOMContentLoaded', () => {
    document.querySelector('.toggle-btn')?.addEventListener('click', () => {
        document.getElementById('sidebar').classList.toggle('active');
    });
});

// Alternar submenu
function toggleSubmenu(event, element) {
    event.preventDefault();
    element.parentElement.classList.toggle('open');
}

// ===== chamadas para a API com o token CSRF =====
function csrfHeaders() {
    const token = document.querySelector('meta[name="_csrf"]')?.content;
    const header = document.querySelector('meta[name="_csrf_header"]')?.content;
    return token && header ? { [header]: token } : {};
}

async function apiFetch(url, options = {}) {
    const headers = Object.assign({ 'Accept': 'application/json' }, csrfHeaders(), options.headers || {});
    if (options.body && typeof options.body !== 'string') {
        options.body = JSON.stringify(options.body);
        headers['Content-Type'] = 'application/json';
    }
    const response = await fetch(url, Object.assign({}, options, { headers }));
    let data = null;
    const texto = await response.text();
    if (texto) {
        try { data = JSON.parse(texto); } catch (e) { data = texto; }
    }
    if (!response.ok) {
        const msg = (data && data.erro) ? data.erro : ('Erro ' + response.status);
        const err = new Error(msg);
        err.status = response.status;
        err.data = data;
        throw err;
    }
    return data;
}

// evita injetar HTML vindo do banco quando monta tabela com innerHTML
function escapeHtml(valor) {
    if (valor === null || valor === undefined) return '';
    return String(valor)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}
