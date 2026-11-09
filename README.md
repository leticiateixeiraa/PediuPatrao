# PediuPatrão – Prática M1 Parte 2

Evolução do sistema de pedidos da pizzaria (Java 21, Spring Boot 3.4, Spring Security, MongoDB e Thymeleaf).
Nessa etapa foram adicionados perfis de acesso, controle de entrada/saída dos pedidos, cancelamento e desconto
pelo gerente e auditoria das alterações de pedidos e clientes.

## Como executar

Pré-requisitos: **Java 21** (o Maven Wrapper já vem no projeto).

```bash
# Linux / Mac
./mvnw spring-boot:run

# Windows
mvnw.cmd spring-boot:run
```

Acesse http://localhost:8080

### Banco de dados

Por padrão a aplicação conecta ao MongoDB local em `localhost:27017`, usando uma base separada
(`pediupatrao_m1p2`) pra não misturar com os dados de outros projetos. Para usar Atlas ou outro servidor,
configure `MONGODB_URI` e `MONGODB_DATABASE`. No PowerShell:

```bash
$env:MONGODB_URI = "mongodb://localhost:27017"
$env:MONGODB_DATABASE = "pediupatrao_m1p2"
.\mvnw.cmd spring-boot:run
```

### Usuários iniciais

Na primeira execução o `DataInitializer` cria um usuário de cada perfil (senha `teste123` para todos):

| Usuário       | Perfil    |
|---------------|-----------|
| `admin`       | ADMIN     |
| `gerente`     | GERENTE   |
| `atendente01` | ATENDENTE |

Se a coleção de produtos estiver vazia, também são cadastrados alguns produtos (pizzas, esfiha e refrigerante)
pra já dar pra lançar pedidos.

### Testes

```bash
./mvnw test
```

Os testes não precisam de banco (usam Mockito e MockMvc):

- `StatusPedidoTest` – transições válidas/inválidas do fluxo
- `PedidoServiceTest` – cálculo do total, saída, cancelamento, limite de desconto
- `ClienteServiceTest` – auditoria só dos campos alterados, telefone mascarado
- `UsuarioServiceTest` – bloqueio de autoelevação, perfil inválido, último admin
- `SegurancaApiTest` – chamadas diretas nas APIs com cada perfil (403/401) e CSRF

## Requisitos implementados

### RF01 – Usuários e permissões

Três perfis (`enum Perfil`): **ADMIN**, **GERENTE** e **ATENDENTE**. O `UsuarioDetailsService` agora usa o perfil
do cadastro (antes todo mundo recebia `ROLE_USER`). Usuários antigos com role `USER` são tratados como ATENDENTE.

As permissões ficam no backend em duas camadas:

1. **`SecurityConfig`** – regras por URL e método HTTP
2. **`@PreAuthorize`** nos services (cancelamento, desconto, gestão de usuários e consulta de auditoria)

| Operação                                  | ADMIN | GERENTE | ATENDENTE |
|-------------------------------------------|:-----:|:-------:|:---------:|
| Gerenciar usuários / atribuir perfis      |   ✔   |         |           |
| Consultar pedidos                         |   ✔   |    ✔    |     ✔     |
| Registrar pedido / avançar status         |       |    ✔    |     ✔     |
| **Cancelar pedido / aplicar desconto**    |       |  **✔**  |           |
| Consultar clientes                        |       |    ✔    |     ✔     |
| Cadastrar / alterar clientes              |       |         |     ✔     |
| Excluir cliente                           |       |    ✔    |           |
| Consultar auditoria                       |   ✔   |    ✔    |           |

Outras mudanças de segurança:

- `/api/usuarios` deixou de ser público (antes qualquer um podia criar usuário, inclusive ADMIN)
- CSRF reativado: formulários Thymeleaf recebem o token automaticamente e as chamadas `fetch` usam
  `apiFetch()` (em `static/js/script.js`), que envia o header `X-CSRF-TOKEN`
- O admin não consegue alterar o próprio perfil nem se excluir, e o sistema sempre mantém pelo menos um ADMIN
- A API não devolve mais o hash da senha (`@JsonProperty(access = WRITE_ONLY)`)
- Sem login a API responde `401`; sem permissão responde `403` em JSON e a tentativa fica no log
- Logout virou POST (botão "Sair" no menu)

### RF02 – Cadastro de pedidos

`Pedido` agora tem itens (`ItemPedido`: produto, quantidade, preço unitário, subtotal), subtotal, percentual e
valor de desconto, valor total, tipo (ENTREGA/RETIRADA), observação, quem registrou e data/hora de entrada.

O JSON de criação (`PedidoRequest`) só aceita cliente, tipo, observação e `produtoId + quantidade`.
O preço vem do cadastro do produto e o total é calculado no `PedidoService` com `BigDecimal`;
se a requisição mandar `valorTotal` ele é ignorado.

Validações: cliente existente, pelo menos um item, quantidade de 1 a 50, produto ativo e com preço,
entrega só para cliente com endereço.

### RF03 – Entrada, saída e transições

```
RECEBIDO → EM_PREPARACAO → PRONTO → SAIU_PARA_ENTREGA → FINALIZADO   (entrega)
                                  → RETIRADO          → FINALIZADO   (retirada)
CANCELADO: só GERENTE, antes da saída
```

- As regras estão em `StatusPedido` (`proximo`, `podeIrPara`, `permiteCancelamentoOuDesconto`) e são aplicadas no `PedidoService`
- Pular etapa (ex.: finalizar pedido em preparação) retorna **409** com o próximo status permitido
- Depois de CANCELADO ou FINALIZADO nada mais muda
- Na entrada grava `registradoPor` e `dataHoraEntrada`; na saída grava `saidaRegistradaPor` e `dataHoraSaida`
- Cada mudança fica no `historico` do pedido (linha do tempo na tela de detalhe)
- Itens só podem ser alterados enquanto o pedido está RECEBIDO

### RF04 – Cancelamento e desconto

- `POST /api/pedidos/{id}/cancelamento` – exige justificativa (mín. 10 caracteres), só antes da saída.
  O pedido não é apagado: mantém itens e valores e guarda quem cancelou, quando, o motivo e o status anterior
- `POST /api/pedidos/{id}/desconto` – percentual de 0,01% a 20%, só antes da saída. Um novo desconto
  substitui o anterior (não acumula), então o desconto total nunca passa de 20%
- As duas operações são exclusivas do GERENTE (ADMIN também recebe 403) e vão para a auditoria

### RF05 – Auditoria

Pacote `auditoria`: `RegistroAuditoria` (coleção `auditoria` no MongoDB), `RegistroAuditoriaRepository` e `AuditoriaService`.

Cada registro guarda usuário, perfil, data/hora, tipo de operação, entidade + id, campos alterados com valor
anterior e novo, e justificativa quando existe.

Operações auditadas:

- Pedidos: inclusão, alteração de itens, mudança de status (com dados da saída), desconto, cancelamento
- Clientes: inclusão, alteração (só os campos que mudaram: endereço, telefone, nome...) e exclusão
- Usuários: criação, troca de perfil e exclusão

Integridade e proteção de dados:

- O repositório estende `Repository` e só expõe `insert` e consultas (não tem save/update/delete)
- A entidade não tem setters
- `/api/auditoria` só aceita GET; qualquer outro método é negado no `SecurityConfig`
- Consulta (tela `/auditoria` e `GET /api/auditoria`) só para ADMIN e GERENTE, com filtros por entidade,
  operação, usuário, id e período
- Campos com nome de senha/token/credencial são descartados antes de gravar
- Telefone é gravado mascarado, ex.: `(11) *****-4321` (dá pra ver que mudou sem expor o número)
- O log da aplicação só registra operação, entidade e usuário, sem os valores

## Principais endpoints

| Método | URL                                   | Perfis              |
|--------|---------------------------------------|---------------------|
| GET    | `/api/pedidos`, `/api/pedidos/{id}`   | todos               |
| POST   | `/api/pedidos`                        | GERENTE, ATENDENTE  |
| PUT    | `/api/pedidos/{id}`                   | GERENTE, ATENDENTE  |
| PUT    | `/api/pedidos/{id}/avancar`           | GERENTE, ATENDENTE  |
| PUT    | `/api/pedidos/{id}/status?status=X`   | GERENTE, ATENDENTE  |
| POST   | `/api/pedidos/{id}/cancelamento`      | GERENTE             |
| POST   | `/api/pedidos/{id}/desconto`          | GERENTE             |
| GET    | `/api/auditoria`                      | ADMIN, GERENTE      |
| GET    | `/api/dashboard?inicio=AAAA-MM-DD&fim=AAAA-MM-DD` | ADMIN, GERENTE |
| *      | `/api/usuarios/**`                    | ADMIN               |

Erros da API seguem o formato `{"status": 422, "erro": "mensagem", "dataHora": "..."}`
(422 regra de negócio, 409 transição inválida, 404 não encontrado, 403 sem permissão).

## Dashboard administrativo

A tela `/dashboard` consome `GET /api/dashboard` e está disponível para ADMIN e GERENTE. O endpoint aceita
`inicio` e `fim` opcionais no formato ISO (`AAAA-MM-DD`); sem parâmetros, considera o mês atual até hoje.
Datas inicial e final são inclusivas. Exemplo:

```http
GET /api/dashboard?inicio=2026-09-01&fim=2026-09-30
```

A resposta agrega os pedidos persistidos no MongoDB e contém:

- `indicadores`: total de pedidos, pedidos em andamento, cancelados e finalizados, faturamento e ticket médio;
- `pedidosPorStatus`: contagem de cada situação do fluxo, inclusive situações sem pedidos no período;
- `vendasPorPeriodo`: faturamento de pedidos finalizados por dia (períodos de até 31 dias) ou por mês;
- `produtosMaisVendidos`: cinco produtos com mais unidades em pedidos finalizados;
- `pedidosRecentes`: até cinco pedidos mais recentes, incluindo status em andamento e cancelados.

Faturamento, ticket médio e ranking de produtos consideram somente pedidos FINALIZADOS. Pedidos cancelados
continuam nas contagens de situação, mas não são tratados como venda. As consultas de data são filtradas no
repositório MongoDB; o navegador atualiza os dados ao aplicar os filtros e a cada minuto. O gráfico usa a
biblioteca Chart.js já incluída no projeto.

### Demonstração em vídeo (roteiro de até 3 minutos)

1. **0:00–0:25** — Entrar como gerente e abrir Dashboard; explicar filtros por período e acesso restrito.
2. **0:25–1:10** — Mostrar os indicadores, alterar as datas e explicar que faturamento/ticket consideram
   pedidos finalizados; mostrar a atualização do gráfico.
3. **1:10–1:50** — Apresentar a distribuição dos status e o ranking de produtos vendidos.
4. **1:50–2:20** — Abrir um pedido recente pela tabela e relacionar a tela aos dados da API MongoDB.
5. **2:20–3:00** — Demonstrar `GET /api/dashboard?inicio=...&fim=...` no navegador ou Postman e explicar
   a resposta agregada e a permissão de acesso ADMIN/GERENTE.

## Testando o bloqueio direto na API

Os botões somem conforme o perfil, mas a proteção é no servidor. Para provar isso, entre como **atendente01**,
abra o console do navegador (F12) numa página do sistema e rode:

```js
// tentar cancelar um pedido (troque pelo id real)
await fetch('/api/pedidos/ID_DO_PEDIDO/cancelamento', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json', ...csrfHeaders() },
  body: JSON.stringify({ justificativa: 'teste de acesso indevido' })
}).then(r => r.status)   // 403

await fetch('/api/auditoria').then(r => r.status)   // 403
```

Fazendo o mesmo logado como **admin**, o cancelamento também retorna 403 (só o gerente pode).

## Roteiro sugerido para o vídeo

1. Login com os três perfis mostrando o menu de cada um
2. Atendente: cadastra/altera um cliente (mudar logradouro e telefone) e registra um pedido
3. Atendente: tenta cancelar/ver auditoria pelo console → 403
4. Gerente: avança o pedido até "Saiu para entrega" mostrando data/hora e responsável da saída;
   tenta finalizar direto um pedido em preparação (bloqueado)
5. Gerente: aplica desconto (testar 25% → bloqueado, 10% → ok) e cancela outro pedido com justificativa
6. Admin: tenta cancelar pelo console → 403; cria um usuário e tenta mudar o próprio perfil (bloqueado)
7. Auditoria (gerente ou admin): mostrar os registros gerados pelas ações acima

## Estrutura

```
src/main/java/com/umc/pediupatrao
├── auditoria/      RegistroAuditoria, repositório, service, Alteracoes, MascaraDados
├── config/         SecurityConfig, DataInitializer, ClockConfig, MongoInitConfig
├── controller/     APIs REST, dashboard, HomeController (telas), ApiExceptionHandler
├── dto/            DTOs de pedidos e DashboardResponse
├── entity/         Pedido, ItemPedido, StatusPedido, TipoEntrega, HistoricoStatus, Perfil...
├── exception/      RegraNegocioException, TransicaoInvalidaException, RecursoNaoEncontradoException
├── repository/
├── security/       UsuarioLogado
└── service/        PedidoService, DashboardService, ClienteService, UsuarioService...
```
