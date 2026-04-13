# Proxy de Score com Rate Limiting (Java)

Projeto da disciplina de Padrões de Projeto: serviço proxy em Java que consome a API pública de score (`https://score.hsborges.dev/api/score`) respeitando o limite de **1 requisição por segundo**.

O proxy:
- aceita múltiplas requisições internas simultâneas;
- usa uma **fila interna** (backpressure);
- possui um **scheduler** que envia no máximo **1 chamada/s** ao upstream;
- faz **cache** de resultados recentes por CPF;
- expõe endpoints HTTP para uso interno e observabilidade.

---

## Tecnologias

- Java 17+ (sem frameworks web, apenas `com.sun.net.httpserver.HttpServer`)
- `java.net.http.HttpClient` para consumir a API externa
- Nenhuma dependência extra / build tool (compilação com `javac`)

---

## Arquitetura e decisões de design

### Padrões utilizados

- **Proxy**  
  O programa `ProxyMain` expõe um serviço HTTP que age como **proxy** da API de score.  
  Ele mantém a mesma interface conceitual (`GET /proxy/score?cpf=...`) e esconde detalhes de:
  - autenticação (`CLIENT_ID`);
  - controle de taxa (rate limiting);
  - fila interna e cache.

- **Decorator**  
  A interface `ScoreClient` representa o cliente de score. Sobre ela foram criados decorators:

  - `APIClient`  
    Cliente concreto que chama a API remota.

  - `CachedDecorator`  
    Decorator que adiciona **cache em memória** por CPF:
    ```java
    ScoreClient cached = new CachedDecorator(new APIClient());
    ```

  - `ControlledDecorator` (usado na primeira parte da atividade)  
    Decorator que garante pelo menos 1 segundo entre chamadas ao upstream:
    ```java
    ScoreClient controlled = new ControlledDecorator(new APIClient());
    ```

- **Producer-Consumer / Fila com Worker**  
  - `ProxyService` mantém uma `BlockingQueue<ProxyRequest>`.
  - As requisições HTTP entram na fila (`producer`).
  - Uma thread interna (`worker`) consome a fila, chama o `ScoreClient` e devolve o resultado pelo `CompletableFuture`, respeitando 1 req/s.

### Padrões / abordagens rejeitados

- **Framework Web pesado (Spring Boot, etc.)**  
  Foi descartado para manter o código simples e didático, usando apenas a API HTTP embutida no JDK (`HttpServer`).

- **Bibliotecas externas de rate limiting (Bucket4j, Resilience4j)**  
  O rate limiting é implementado manualmente, via:
  - decorator (`ControlledDecorator`) e/ou
  - worker com `Thread.sleep(1000)` em `ProxyService`.
  
  Isso deixa explícito o uso do padrão Proxy e o controle de fila, ao invés de delegar a uma biblioteca.

- **Armazenamento de cache externo (Redis, banco de dados)**  
  Para a atividade, um cache em memória (`ConcurrentHashMap`) era suficiente e mais simples.  
  Em produção, seria razoável considerar Redis para cache distribuído.

---

## Como rodar

### Pré-requisitos

- Java 17+ instalado
- Acesso à internet
- Um `CLIENT_ID` válido para a API de score (fornecido pelo professor)

### 1. Configurar variável de ambiente

No **mesmo terminal** em que você vai rodar o proxy:

#### Linux / macOS / Git Bash

```bash
export CLIENT_ID="SEU_CLIENT_ID_AQUI"
```

### 2. Compilar o projeto
Na raiz do projeto (onde estão os .java):

```bash
javac *.java
```

Isso gera os .class na mesma pasta.

### 3. Rodar o proxy HTTP

```bash
java ProxyMain
```

Saída esperada:  

```txt
Proxy rodando em http://localhost:8080
```

### Endpoints e exemplos de uso
#### RF1 – GET /proxy/score
Retorna o score de um CPF usando o proxy.

**URL:** http://localhost:8080/proxy/score?cpf=218.422.170-89  
**Método**: GET  
**Parâmetros de query**:  
cpf (obrigatório)

##### Exemplo com curl
```bash
curl "http://localhost:8080/proxy/score?cpf=218.422.170-89"
```

Exemplo de resposta:
```json
{"cpf":"218.422.170-89","score":308}
```

#### RF2 – GET /metrics
Retorna métricas simples do rate limiter.

**URL:** http://localhost:8080/metrics
**Método:** GET

```bash
curl "http://localhost:8080/metrics"
```

Exemplo de resposta:

```json
{
  "queueSize": 0,
  "enqueued": 20,
  "dropped": 0,
  "upstreamCalls": 10
}
```
Significado:

queueSize: tamanho atual da fila interna.
enqueued: total de requisições que entraram na fila.
dropped: requisições recusadas porque a fila estava cheia.
upstreamCalls: chamadas efetivas feitas para a API externa (limitadas a ~1/s).


#### RF3 – GET /health
Endpoint simples de liveness/readiness.

**URL:** http://localhost:8080/health
**Método:** GET

```bash
curl "http://localhost:8080/health"
```

Exemplo de resposta:

```json
{
  "status": "UP",
  "queueSize": 0
}
```

#### Seed de testes
Para os testes da atividade foi usado o CPF de exemplo:

218.422.170-89

#### Testes / Experimentos
Os testes foram feitos com scripts simples usando curl e loops no terminal.

#### 1. Rajada controlada (RNF1, RNF3, RNF4)
Objetivo: enviar ~20 requisições internas em 1s e verificar se o upstream recebe ~1 req/s.

Com o proxy rodando:

```bash
for i in {1..20}; do
  curl -s "http://localhost:8080/proxy/score?cpf=218.422.170-89" > /dev/null &
done
wait

curl "http://localhost:8080/metrics"
```

Comportamento esperado / observado:

enqueued ≈ 20;
dropped == 0 (com fila configurada para 100);
upstreamCalls aumenta na ordem de 1 por segundo, por causa do Thread.sleep(1000) no worker;
queueSize vai diminuindo com o tempo, até voltar a 0.

Isso mostra que o proxy tolera rajadas internas sem violar o limite externo de 1 req/s.

### 2. Penalidade proposital (comparando com e sem proxy)
Ideia: forçar chamadas paralelas diretas à API (sem proxy) e comparar com o uso via proxy.

Chamadas diretas à API externa:

for i in {1..5}; do
  curl -s -H "client-id: $CLIENT_ID" \
       "https://score.hsborges.dev/api/score?cpf=218.422.170-89" &
done
wait
.```

Nessa configuração, há risco de violar o limite de 1 req/s e sofrer a penalidade de +2s.

Chamadas **via proxy**:

```bash
for i in {1..5}; do
  curl -s "http://localhost:8080/proxy/score?cpf=218.422.170-89" &
done
wait

curl "http://localhost:8080/metrics"
```

Como o proxy envia no máximo 1 chamada/s ao upstream, a tendência é **evitar penalidades recorrentes**, mesmo com carga paralela interna.

---

### 3. Timeout / lentidão no upstream

O código usa `CompletableFuture.get(5, TimeUnit.SECONDS)` no `ProxyMain`, ou seja:

- se a resposta demorar **mais de 5s** dentro do proxy (fila + upstream), o cliente recebe `503 Falha ao obter score`;
- não há política de retry ou fallback avançado.

Isso atende o cenário básico de não ficar bloqueado indefinidamente, mas **não implementa**:

- janela de bloqueio para novas tentativas;
- fallback com dados de cache especificamente para erro;
- controle adaptativo.

Esses pontos seriam extensões possíveis.

---

### 4. Política de fila

**Implementado:**

- Fila limitada **FIFO** (`ArrayBlockingQueue` com capacidade fixa, ex.: 100).
- Quando a fila está cheia:
  - a requisição é marcada como `dropped`;
  - o cliente recebe erro (`Fila cheia, requisição descartada`).

**Não implementado:**

- prioridades por tipo de requisição;
- TTL por requisição;
- diferentes motivos de descarte.

A solução atual cobre a parte obrigatória de **“fila/buffer interno”** com tempo de espera previsível (fila controlada e limite claro).

---

## Relato técnico

### Padrões adotados

#### Proxy

O serviço HTTP (`ProxyMain`) atua como um proxy para a API de score, escondendo:

- detalhes de autenticação (`CLIENT_ID`);
- a gestão de fila e rate limiting;
- o cache de respostas.

#### Decorator

A interface `ScoreClient` é decorada por:

- `APIClient` (cliente real da API externa);
- `CachedDecorator` (cache em memória por CPF);
- `ControlledDecorator` (versão da primeira atividade para controlar intervalo entre chamadas).

#### Producer-Consumer com fila bloqueante

`ProxyService` implementa um padrão **producer-consumer** simples:

- produtores: handlers HTTP (`/proxy/score`) que chamam `proxy.requestScore(cpf)`;
- consumidor: uma thread worker que processa a fila e respeita 1 chamada/s ao upstream.

---

### Padrões / abordagens rejeitados (com justificativa)

#### Frameworks web completos (Spring, etc.)

Rejeitados para manter o foco em Padrões de Projeto, sem adicionar complexidade de configuração.

#### Bibliotecas de rate limiting prontas

Não utilizadas para deixar a lógica de fila, scheduler e rate limit explícita no código.

#### Cache distribuído

Não é necessário para o escopo da atividade (um único processo em execução).  
Cache simples em memória é suficiente e mais fácil de entender.

---

### Experimentos (resumo)

- Rajada de 20 requisições internas em ~1s:
  - fila absorve o pico;
  - upstream recebe ~1 req/s;
  - sem descartes com fila configurada para 100.
- Chamadas paralelas diretas à API vs. chamadas via proxy:
  - diretas: risco maior de acionar a penalidade do provedor;
  - via proxy: chamadas serializadas pelo worker, respeitando o limite de 1 req/s.
- Erros e lentidão:
  - caso a fila + upstream demorem mais de 5s, o proxy responde `503`;
  - não há política de retry ou adaptive rate control por enquanto.

---

## Análise crítica e trade-offs

### Simplicidade vs. robustez

O código é intencionalmente simples (Java puro, sem libs extras).

Isso facilita o entendimento dos padrões, mas não cobre todos os cenários reais de produção (falhas intermitentes, retries, backoff, etc.).

### Rate limit fixo (1s)

O scheduler usa um `Thread.sleep(1000)` fixo, o que:

- garante 1 req/s;
- mas não reage automaticamente a penalidades (RNF2 não implementado de forma adaptativa).

### Cache em memória

Ajuda a reduzir chamadas repetidas para o mesmo CPF.

Em contrapartida:

- não possui expiração/TTL configurado;
- não é compartilhado entre instâncias.

### Fila FIFO com descarte simples

A política de fila é clara e fácil de explicar:

- fila cheia → requisição descartada com erro.

Porém, não há priorização de requisições importantes nem TTL por item, que seriam relevantes em cenários mais críticos.