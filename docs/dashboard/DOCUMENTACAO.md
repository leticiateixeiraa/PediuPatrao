# Dashboard Administrativo – PediuPatrão

Painel para o responsável pelo estabelecimento acompanhar vendas, situação dos pedidos e produtos mais vendidos.
O dashboard é uma página do próprio sistema (`/dashboard`) que consome uma API REST nova (`/api/dashboard`),
calculada a partir da coleção `pedidos` do MongoDB.

Sumário:
1. [Requisitos funcionais](#1-requisitos-funcionais)
2. [Requisitos não funcionais](#2-requisitos-não-funcionais)
3. [Dados utilizados e regras de cálculo](#3-dados-utilizados-e-regras-de-cálculo)
4. [Documentação dos endpoints](#4-documentação-dos-endpoints)
5. [Erros da API](#5-erros-da-api)
6. [Decisões de análise e projeto](#6-decisões-de-análise-e-projeto)
7. [Evidências de testes](#7-evidências-de-testes)
8. [Fluxo de branches (gitflow)](#8-fluxo-de-branches-gitflow)
9. [Como executar](#9-como-executar)

---

## 1. Requisitos funcionais

| Código | Requisito | Como foi atendido |
|---|---|---|
| **RF01** | Indicadores gerais de vendas | Cards com faturamento, total de pedidos (e válidos), ticket médio, itens vendidos, pedidos em andamento, cancelamentos (quantidade e taxa) e descontos concedidos. Endpoint `GET /api/dashboard/resumo`. |
| **RF02** | Análise de vendas (gráficos) | Gráfico de evolução (faturamento em linha + quantidade de pedidos em barras) agrupado por dia, semana ou mês, e gráfico comparando Entrega x Retirada. Endpoints `GET /api/dashboard/vendas` e `GET /api/dashboard/vendas-por-tipo`. |
| **RF03** | Situação dos pedidos | Gráfico de rosca com a quantidade e o percentual de pedidos em cada status, e tabela com os pedidos mais recentes. Endpoints `GET /api/dashboard/situacao-pedidos` e `GET /api/dashboard/pedidos-recentes`. |
| **RF04** | Produtos mais vendidos | Ranking (gráfico de barras + tabela) por quantidade, com faturamento e participação de cada produto. Endpoint `GET /api/dashboard/produtos-mais-vendidos`. |
| **RF05** | Filtros de consulta | Período (data inicial e final, com atalhos Hoje/7/30/90 dias), status, tipo de entrega e agrupamento. Os mesmos filtros são aceitos por todos os endpoints. |
| **RF06** | Integração com a API | A tela não calcula nada: faz `GET /api/dashboard` e só desenha o resultado. A API lê os pedidos do MongoDB. |
| **RF07** | Atualização e tratamento de dados | Atualização automática a cada 60 s (pode ser desligada), botão "Atualizar" e horário da última atualização. Mensagens de carregando, erro e "sem dados". No backend, registros incompletos (sem status, sem valor, sem itens) não quebram o cálculo e são contados em `registrosIgnorados`. |

Perfis de acesso: o dashboard é **somente leitura** e fica disponível para **ADMIN** e **GERENTE**
(o atendente não tem acesso, seguindo a mesma lógica da auditoria da etapa anterior).

## 2. Requisitos não funcionais

| Código | Categoria | Requisito |
|---|---|---|
| RNF01 | Segurança | Somente usuários autenticados com perfil ADMIN ou GERENTE acessam a página e a API. A regra está no `SecurityConfig` (URL) e no `@PreAuthorize` do `DashboardService`. Qualquer método diferente de GET em `/api/dashboard/**` é negado. |
| RNF02 | Segurança | Sem login a API responde 401 e sem permissão 403, em JSON, sem expor detalhes internos. |
| RNF03 | Desempenho | A consulta filtra os pedidos pela data de entrada no próprio MongoDB, usando o índice `idx_pedidos_data_entrada`, criado ao iniciar a aplicação. O período máximo de consulta é de 366 dias. |
| RNF04 | Desempenho | A tela faz uma única requisição (`GET /api/dashboard`) por atualização, em vez de uma por gráfico. |
| RNF05 | Precisão | Valores monetários são calculados com `BigDecimal` e arredondados com 2 casas (HALF_UP). |
| RNF06 | Usabilidade | Layout responsivo (Bootstrap): os cards e gráficos se reorganizam em telas menores. Valores formatados em reais (R$) e datas no padrão brasileiro. |
| RNF07 | Disponibilidade da informação | Os dados exibidos têm no máximo 60 s de atraso quando a atualização automática está ligada. A atualização é pausada enquanto a aba do navegador está em segundo plano. |
| RNF08 | Manutenibilidade | Regras de cálculo concentradas no `DashboardService`, respostas tipadas em `records` (pacote `dashboard.dto`) e cobertura por testes automatizados. |
| RNF09 | Compatibilidade | Funciona nos navegadores atuais (Chrome, Edge, Firefox). Gráficos com Chart.js 4.4.1. |

## 3. Dados utilizados e regras de cálculo

Fonte: coleção `pedidos` (entidade `Pedido`). Campos usados:

| Campo | Uso |
|---|---|
| `dataHoraEntrada` | filtro de período e agrupamento por dia/semana/mês |
| `status` | filtro, situação dos pedidos, cancelamentos, pedidos em andamento |
| `tipoEntrega` | filtro e gráfico Entrega x Retirada |
| `valorTotal` | faturamento e ticket médio |
| `valorDesconto` | total de descontos |
| `itens[].produtoId / nomeProduto / quantidade / subtotal` | ranking de produtos |
| `clienteNome`, `registradoPor` | tabela de pedidos recentes |

Regras:
- O período considera a **data de entrada** do pedido; sem datas informadas, são usados os **últimos 30 dias**, contando com hoje.
- **Faturamento** é a soma de `valorTotal` dos pedidos **não cancelados**, já com o desconto aplicado.
- **Ticket médio** = faturamento ÷ pedidos válidos (não cancelados).
- **Taxa de cancelamento** = cancelados ÷ total de pedidos do período × 100.
- **Pedidos em andamento** = pedidos válidos que ainda não estão FINALIZADOS.
- **Produtos mais vendidos**: ordenados por quantidade (desempate por faturamento e depois por nome), considerando só pedidos não cancelados. O faturamento do produto usa o subtotal do item, antes do desconto do pedido.
- **Situação dos pedidos** inclui todos os status, até os com zero, e os cancelados.
- No gráfico de vendas, dias, semanas ou meses sem venda aparecem com zero, para a linha do tempo não "pular". As semanas começam na segunda-feira.
- Pedidos sem status (registros antigos ou incompletos) não entram nos cálculos e são informados em `registrosIgnorados`.

## 4. Documentação dos endpoints

Base: `http://localhost:8080/api/dashboard`. Todos são `GET`, exigem login (sessão) com perfil ADMIN ou GERENTE
e respondem `application/json`.

### Parâmetros comuns (todos opcionais)

| Parâmetro | Tipo | Exemplo | Padrão | Descrição |
|---|---|---|---|---|
| `dataInicio` | data `yyyy-MM-dd` | `2026-11-01` | hoje − 29 dias | início do período (inclusive) |
| `dataFim` | data `yyyy-MM-dd` | `2026-11-09` | hoje | fim do período (inclusive) |
| `status` | enum | `FINALIZADO` | todos | `RECEBIDO`, `EM_PREPARACAO`, `PRONTO`, `SAIU_PARA_ENTREGA`, `RETIRADO`, `FINALIZADO`, `CANCELADO` |
| `tipoEntrega` | enum | `ENTREGA` | todos | `ENTREGA` ou `RETIRADA` |

### 4.1 `GET /api/dashboard` – dashboard completo

Retorna todos os blocos de uma vez. É o endpoint usado pela tela.

Parâmetros extras:

| Parâmetro | Tipo | Padrão | Regra |
|---|---|---|---|
| `agrupamento` | `DIA`, `SEMANA` ou `MES` | `DIA` | agrupamento do gráfico de vendas |
| `limiteProdutos` | inteiro | `5` | de 1 a 50 |
| `limiteRecentes` | inteiro | `10` | de 1 a 50 |

Requisição:
```
GET /api/dashboard?dataInicio=2026-11-07&dataFim=2026-11-09&agrupamento=DIA&limiteProdutos=3&limiteRecentes=2
```

Resposta `200 OK`, com os blocos `resumo`, `vendasPorPeriodo` etc. iguais aos dos endpoints abaixo:
```json
{
  "geradoEm": "2026-11-09T12:00:00",
  "resumo": { "...": "ver 4.2" },
  "vendasPorPeriodo": [ "...ver 4.3" ],
  "situacaoPedidos": [ "...ver 4.5" ],
  "vendasPorTipoEntrega": [ "...ver 4.4" ],
  "produtosMaisVendidos": [ "...ver 4.7" ],
  "pedidosRecentes": [ "...ver 4.6" ]
}
```

Os exemplos de 4.2 a 4.7 usam a mesma massa de dados do teste `DashboardServiceTest`, no período de 07/11 a 09/11/2026:

| Pedido | Entrada | Status | Tipo | Itens | Total |
|---|---|---|---|---|---|
| p1 | 08/11 10:00 | FINALIZADO | ENTREGA | 2x Pizza Calabresa, 1x Refrigerante 2L (desconto de 10%) | 93,42 |
| p2 | 08/11 19:00 | CANCELADO | RETIRADA | 1x Pizza Mussarela | 42,00 |
| p3 | 09/11 11:00 | EM_PREPARACAO | RETIRADA | 1x Pizza Calabresa, 4x Esfiha de Carne | 71,90 |
| p4 | 07/11 09:00 | (sem status) | – | – | – |

### 4.2 `GET /api/dashboard/resumo` – indicadores (RF01)

Requisição:
```
GET /api/dashboard/resumo?dataInicio=2026-11-07&dataFim=2026-11-09
```
Resposta `200 OK`:
```json
{
  "dataInicio": "2026-11-07",
  "dataFim": "2026-11-09",
  "faturamento": 165.32,
  "totalPedidos": 3,
  "pedidosValidos": 2,
  "pedidosCancelados": 1,
  "pedidosEmAndamento": 1,
  "ticketMedio": 82.66,
  "taxaCancelamento": 33.33,
  "totalDescontos": 10.38,
  "itensVendidos": 8,
  "registrosIgnorados": 1
}
```

### 4.3 `GET /api/dashboard/vendas` – evolução das vendas (RF02)

Parâmetro extra: `agrupamento` (`DIA` | `SEMANA` | `MES`, padrão `DIA`).

Requisição:
```
GET /api/dashboard/vendas?dataInicio=2026-11-07&dataFim=2026-11-09&agrupamento=DIA
```
Resposta `200 OK`:
```json
[
  { "periodo": "2026-11-07", "rotulo": "07/11", "quantidadePedidos": 0, "faturamento": 0.00 },
  { "periodo": "2026-11-08", "rotulo": "08/11", "quantidadePedidos": 1, "faturamento": 93.42 },
  { "periodo": "2026-11-09", "rotulo": "09/11", "quantidadePedidos": 1, "faturamento": 71.90 }
]
```
Com `agrupamento=MES`, `periodo` vem como `2026-11` e `rotulo` como `11/2026`.
Com `SEMANA`, `periodo` é a segunda-feira da semana e `rotulo` é `Sem. dd/MM`.

### 4.4 `GET /api/dashboard/vendas-por-tipo` – entrega x retirada (RF02)

Resposta `200 OK`, sem contar os cancelados:
```json
[
  { "tipoEntrega": "ENTREGA",  "quantidadePedidos": 1, "faturamento": 93.42 },
  { "tipoEntrega": "RETIRADA", "quantidadePedidos": 1, "faturamento": 71.90 }
]
```

### 4.5 `GET /api/dashboard/situacao-pedidos` – pedidos por status (RF03)

Resposta `200 OK` (sempre os 7 status):
```json
[
  { "status": "RECEBIDO",          "descricao": "Recebido",          "quantidade": 0, "percentual": 0.00 },
  { "status": "EM_PREPARACAO",     "descricao": "Em preparação",     "quantidade": 1, "percentual": 33.33 },
  { "status": "PRONTO",            "descricao": "Pronto",            "quantidade": 0, "percentual": 0.00 },
  { "status": "SAIU_PARA_ENTREGA", "descricao": "Saiu para entrega", "quantidade": 0, "percentual": 0.00 },
  { "status": "RETIRADO",          "descricao": "Retirado",          "quantidade": 0, "percentual": 0.00 },
  { "status": "FINALIZADO",        "descricao": "Finalizado",        "quantidade": 1, "percentual": 33.33 },
  { "status": "CANCELADO",         "descricao": "Cancelado",         "quantidade": 1, "percentual": 33.33 }
]
```

### 4.6 `GET /api/dashboard/pedidos-recentes` – últimos pedidos (RF03)

Parâmetro extra: `limite` (1 a 50, padrão 10).

Requisição:
```
GET /api/dashboard/pedidos-recentes?dataInicio=2026-11-07&dataFim=2026-11-09&limite=2
```
Resposta `200 OK`:
```json
[
  { "id": "p3", "clienteNome": "Cliente p3", "status": "EM_PREPARACAO", "statusDescricao": "Em preparação",
    "tipoEntrega": "RETIRADA", "valorTotal": 71.90, "dataHoraEntrada": "2026-11-09T11:00:00", "registradoPor": "atendente01" },
  { "id": "p2", "clienteNome": "Cliente p2", "status": "CANCELADO", "statusDescricao": "Cancelado",
    "tipoEntrega": "RETIRADA", "valorTotal": 42.00, "dataHoraEntrada": "2026-11-08T19:00:00", "registradoPor": "atendente01" }
]
```

### 4.7 `GET /api/dashboard/produtos-mais-vendidos` – ranking (RF04)

Parâmetro extra: `limite` (1 a 50, padrão 5).

Resposta `200 OK`:
```json
[
  { "posicao": 1, "produtoId": "esfiha",    "nome": "Esfiha de Carne", "quantidade": 4, "faturamento": 26.00,  "participacaoFaturamento": 14.80 },
  { "posicao": 2, "produtoId": "calabresa", "nome": "Pizza Calabresa", "quantidade": 3, "faturamento": 137.70, "participacaoFaturamento": 78.37 },
  { "posicao": 3, "produtoId": "refri",     "nome": "Refrigerante 2L", "quantidade": 1, "faturamento": 12.00,  "participacaoFaturamento": 6.83 }
]
```
A Pizza Mussarela não aparece porque só foi pedida no pedido cancelado.

### Como testar pelo navegador

Logado como **gerente** ou **admin**, basta abrir a URL na barra de endereço, por exemplo
`http://localhost:8080/api/dashboard/resumo?dataInicio=2026-11-01&dataFim=2026-11-09`.

## 5. Erros da API

Formato padrão das respostas de erro:
```json
{ "status": 422, "erro": "A data inicial não pode ser posterior à data final.", "dataHora": "2026-11-09T12:00:00" }
```

| HTTP | Quando acontece | Exemplo de requisição | Mensagem |
|---|---|---|---|
| **401** | sem login (sessão expirada) | qualquer endpoint sem estar logado | `Não autenticado` |
| **403** | perfil sem permissão (ATENDENTE) | `GET /api/dashboard` logado como atendente | `Seu perfil não tem permissão para esta operação` |
| **403** | método diferente de GET | `POST /api/dashboard` | `Seu perfil não tem permissão para esta operação` |
| **400** | data em formato errado | `?dataInicio=31/12/2026` | `Requisição inválida: verifique os dados enviados.` |
| **400** | valor de enum inexistente | `?status=ENTREGUE` ou `?agrupamento=ANO` | `Requisição inválida: verifique os dados enviados.` |
| **422** | data inicial depois da final | `?dataInicio=2026-11-09&dataFim=2026-11-01` | `A data inicial não pode ser posterior à data final.` |
| **422** | período maior que 366 dias | `?dataInicio=2024-01-01&dataFim=2026-11-09` | `O período consultado pode ter no máximo 366 dias.` |
| **422** | limite fora de 1 a 50 | `/produtos-mais-vendidos?limite=0` | `O parâmetro 'limite' deve ficar entre 1 e 50.` |
| **500** | falha inesperada (ex.: banco fora do ar) | – | `Erro interno. Tente novamente mais tarde.` |

Período sem pedidos **não é erro**: a API responde `200` com valores zerados e listas vazias, e a tela mostra "Nenhuma venda no período".

## 6. Decisões de análise e projeto

1. **Dashboard dentro do sistema existente.** A tela é uma página Thymeleaf protegida pelo mesmo login e pelos mesmos perfis. Assim não foi preciso criar outra aplicação nem outro mecanismo de autenticação.
2. **Acesso só para ADMIN e GERENTE.** São informações gerenciais (faturamento, cancelamentos). O atendente opera os pedidos, mas não precisa desses números.
3. **API separada da tela.** A página só desenha o que recebe de `/api/dashboard`. Os mesmos dados podem ser usados por outro cliente (app, relatório) sem mudar o backend.
4. **Um endpoint "completo" e endpoints por recurso.** O `GET /api/dashboard` evita várias requisições a cada atualização. Os endpoints individuais (`/resumo`, `/vendas` etc.) deixam a API organizada por recurso e mais fácil de testar e reutilizar.
5. **Cálculo no service (Java) em vez de pipeline de agregação do MongoDB.** O banco já filtra o período pelo índice; o restante é calculado no `DashboardService`. Para o volume de uma pizzaria (centenas de pedidos por mês) isso é rápido, as regras ficam legíveis e foram testadas com testes unitários sem precisar de banco. Se o volume crescer muito, o próximo passo seria mover os cálculos para `aggregate()` do MongoDB.
6. **Cancelados fora do faturamento, mas dentro da situação.** Cancelamento não gera receita, mas o gerente precisa ver quantos houve (taxa de cancelamento).
7. **Intervalos sem venda aparecem com zero.** Sem isso o gráfico ligaria diretamente dias distantes e daria uma leitura errada da tendência.
8. **Atualização a cada 60 s.** É suficiente para acompanhar a operação sem sobrecarregar o servidor, e a atualização é pausada com a aba em segundo plano.
9. **Valores em `BigDecimal`.** Evita erros de arredondamento de `double` em dinheiro.
10. **Dados de demonstração opcionais.** Com `DEMO_DATA=true` a aplicação gera pedidos dos últimos 45 dias (só se houver menos de 20 pedidos), para que os gráficos possam ser demonstrados. Fica desligado por padrão e os pedidos gerados ficam marcados com `registradoPor = "demo"`.

## 7. Evidências de testes

### 7.1 Testes automatizados

Executar na pasta do projeto:
```
mvnw.cmd test        (Windows)
./mvnw test          (Linux/Mac)
```

| Classe | O que verifica |
|---|---|
| `DashboardServiceTest` | indicadores do resumo; dias sem venda no gráfico; agrupamento por mês; entrega x retirada; situação com todos os status e percentuais; pedidos recentes em ordem; ranking de produtos e participação; filtros por status e tipo; período padrão de 30 dias; validações (data inicial > final, período > 366 dias, limite inválido); registros incompletos; período sem pedidos |
| `SegurancaApiTest` (testes do dashboard) | atendente recebe 403; gerente e admin recebem 200; POST é negado; sem login recebe 401; parâmetro inválido recebe 400 |

Os testes das etapas anteriores (pedidos, clientes, usuários, auditoria) continuam no projeto e também devem passar.

**Resultado da execução:** print da saída do `mvnw test` (com `Tests run`, `Failures: 0` e `BUILD SUCCESS`) em `evidencias/01-testes-automatizados.png`.

### 7.2 Testes manuais

| # | Cenário | Passos | Resultado esperado | Evidência |
|---|---|---|---|---|
| 1 | Acesso do gerente | login `gerente` → menu Dashboard | painel carrega com indicadores e gráficos | `evidencias/02-dashboard-gerente.png` |
| 2 | Filtro por período | atalho "7 dias" | números e gráfico mudam para a última semana | `evidencias/03-filtro-periodo.png` |
| 3 | Filtro por status | Status = Cancelado → Filtrar | faturamento R$ 0,00 e taxa de cancelamento 100% | `evidencias/04-filtro-status.png` |
| 4 | Agrupamento | Agrupar por Semana | gráfico de evolução passa a mostrar semanas | `evidencias/05-agrupamento.png` |
| 5 | Atualização | registrar pedido novo em outra aba e clicar em Atualizar | total de pedidos aumenta e o pedido aparece em "Pedidos recentes" | `evidencias/06-atualizacao.png` |
| 6 | Acesso negado | login `atendente01` → abrir `/api/dashboard` | JSON com status 403 (e menu sem Dashboard) | `evidencias/07-atendente-403.png` |
| 7 | Erro de validação | gerente abre `/api/dashboard/resumo?dataInicio=2026-11-09&dataFim=2026-11-01` | JSON com status 422 e a mensagem da data | `evidencias/08-erro-422.png` |
| 8 | Endpoint individual | gerente abre `/api/dashboard/produtos-mais-vendidos?limite=3` | JSON com 3 produtos | `evidencias/09-endpoint-produtos.png` |

## 8. Fluxo de branches (gitflow)

```
main ─────────────────────────────────────────────●── (v1.1.0)
  \                                              /
   develop ──●────────────●──────────●──────────●
              \          / \        / \        /
               feature/dashboard-api   feature/dashboard-tela   feature/documentacao-dashboard
                                                     \
                                                      release/1.1.0
```

- `main`: versão entregue (tag `v1.0.0` = Prática M1 Parte 2, `v1.1.0` = dashboard).
- `develop`: integração das funcionalidades.
- `feature/dashboard-api`: endpoints, regras de cálculo, índice e testes da API.
- `feature/dashboard-tela`: página, gráficos, filtros, atualização automática e dados de demonstração.
- `feature/documentacao-dashboard`: este documento e o README.
- `release/1.1.0`: fechamento da versão, mesclada em `main` e de volta em `develop`.

## 9. Como executar

Pré-requisitos: Java 21 e MongoDB (local ou Atlas).

Windows (PowerShell):
```powershell
$env:MONGODB_URI="mongodb://localhost:27017"
$env:DEMO_DATA="true"        # opcional: gera pedidos de demonstração para os gráficos
.\mvnw.cmd spring-boot:run
```

Acesse `http://localhost:8080`, entre com `gerente` / `teste123` (ou `admin` / `teste123`) e clique em **Dashboard** no menu.
