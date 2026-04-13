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