# llm-shadow-router

A Spring Boot service that implements the **shadow deployment** pattern for LLMs:

- `POST /v1/chat` forwards the payload to a **primary** model on DigitalOcean's
  serverless inference API and returns its response to the caller immediately.
- The exact same payload is offered to a **bounded** background executor that
  calls a **candidate** model, compares outputs, and updates metrics.
- Under heavy traffic, when the shadow pool is saturated, background evaluations
  are **load-shed** (dropped) so memory and the primary request path stay healthy.

## Architecture

```
                         +---------------------------+
                         |        API layer          |
  Client --- POST /v1/chat -->| ChatController         |
         \                   | MetricsController      |
          \  GET /metrics    +------------+----------+
           \                              |
            \                             v
             \               +------------+----------+
              \              |      ChatService      |
               \             |   completeChat(...)   |
                \            +------+--------+-------+
                 \                  |        |
                  \     sync path   |        | non-blocking offer
                   \                v        v
                    \    +----------+--+  +--+---------------------------+
                     \   | Primary LLM |  | ShadowEvaluationService      |
                      \  | (awaited)   |  | submitEvaluation(...)        |
                       \ +------+------+  +------+-----------------------+
                        \       |                |
                         \      | HTTP response  | bounded executor
                          \     v                v
                           \  Client      +------+-------+
                            \             | accept/queue |
                             \            +--+--------+--+
                              \              |        |
                               \             | full   | accepted
                                \            v        v
                                 \         shed    Candidate LLM
                                  \          |        |
                                   \         v        v
                                    \   ShadowMetrics <--- compare (action match)
```

**Sync path (immediate return):**  
`Client` → `ChatController` → `ChatService.completeChat` → primary LLM → response to client.

**Decoupled background path:**  
`ChatService` offers work to `ShadowEvaluationService` → bounded queue/pool → candidate LLM → `OutputComparator` → `ShadowMetrics`.

**Load shedding:** when concurrency + queue are full, the shadow task is dropped; the chat response is unchanged.

```mermaid
flowchart LR
  Client(["Client"])

  subgraph api ["API layer"]
    Chat["POST /v1/chat<br/>ChatController"]
    Metrics["GET /metrics<br/>MetricsController"]
  end

  subgraph sync ["Sync path — immediate return"]
    ChatSvc["ChatService.completeChat"]
    Primary["Primary LLM<br/>DigitalOcean Inference API"]
  end

  subgraph async ["Decoupled background shadow evaluation"]
    ShadowSvc["ShadowEvaluationService.submitEvaluation"]
    Offer{"Offer to bounded<br/>shadow executor"}
    Queue["Bounded queue<br/>+ max concurrency"]
    Candidate["Candidate LLM"]
    Compare["OutputComparator<br/>JSON + action match"]
    Shed["Load shed<br/>drop evaluation"]
  end

  Store["ShadowMetrics"]

  Client --> Chat
  Chat --> ChatSvc
  ChatSvc --> Primary
  Primary -->|HTTP response| Client

  ChatSvc -->|non-blocking offer| ShadowSvc
  ShadowSvc --> Offer
  Offer -->|accepted| Queue
  Offer -->|queue full| Shed
  Shed --> Store
  Queue --> Candidate
  Candidate --> Compare
  Compare --> Store
  Metrics --> Store
```

## Requirements

- Java 21+ and Maven 3.9+ **or** Docker
- A DigitalOcean [model access key](https://docs.digitalocean.com/products/gradient-ai-platform/how-to/use-serverless-inference/)

## CI

On every push, GitHub Actions runs the full Maven test suite (`.github/workflows/ci.yml`):

- Ubuntu runner
- Temurin JDK 21
- Maven dependency cache
- `mvn -B test`

No DigitalOcean credentials are required in CI; tests mock or stub the inference API.

## Configuration

All settings come from environment variables (see `.env.example`):

| Variable | Default | Purpose |
|---|---|---|
| `DO_INFERENCE_BASE_URL` | `https://inference.do-ai.run/v1` | OpenAI-compatible base URL |
| `DO_MODEL_ACCESS_KEY` | (required) | Bearer token for the inference API |
| `PRIMARY_MODEL` | `llama3.3-70b-instruct` | Model that answers the user |
| `CANDIDATE_MODEL` | `openai-gpt-oss-120b` | Model that receives the mirrored payload |
| `PRIMARY_TIMEOUT` | `60s` | Timeout for the user-facing call |
| `CANDIDATE_TIMEOUT` | `90s` | Timeout for the background shadow call |
| `SHADOW_MAX_CONCURRENCY` | `32` | Max concurrent background shadow evaluations |
| `SHADOW_QUEUE_CAPACITY` | `128` | Max queued shadow tasks before shedding |

## Run locally

```bash
cp .env.example .env   # fill in DO_MODEL_ACCESS_KEY
set -a && source .env && set +a
mvn spring-boot:run
```

## Run with Docker

Build a portable image (Java/Maven not required on the host):

```bash
docker build -t llm-shadow-router .
```

Run it (pass your model access key and optional overrides):

```bash
docker run --rm -p 8080:8080 \
  -e DO_MODEL_ACCESS_KEY="your-model-access-key-here" \
  -e PRIMARY_MODEL="llama3.3-70b-instruct" \
  -e CANDIDATE_MODEL="openai-gpt-oss-120b" \
  llm-shadow-router
```

Or load variables from a `.env` file:

```bash
cp .env.example .env   # fill in DO_MODEL_ACCESS_KEY
docker run --rm -p 8080:8080 --env-file .env llm-shadow-router
```

The multi-stage `Dockerfile` builds the Spring Boot jar with Maven, then runs it on a slim Eclipse Temurin 21 JRE image.

## Try it

```bash
curl -s http://localhost:8080/v1/chat \
  -H "Content-Type: application/json" \
  -d '{
    "messages": [
      {"role": "user", "content": "Say hello in one sentence."}
    ],
    "max_tokens": 64
  }'
```

The response is the primary model's chat completion, passed through as-is.

```bash
curl -s http://localhost:8080/metrics
```

Example metrics payload:

```json
{
  "totalRequestsProcessed": 42,
  "shadowErrorsOrTimeouts": 3,
  "shadowEvaluationsShed": 5,
  "comparisonsEvaluated": 34,
  "exactActionMatches": 28,
  "exactMatchRatePercentage": 82.4
}
```

## How it works

- `ChatService` owns the user response: primary inference only.
- `ShadowEvaluationService` owns candidate inference, comparison, and load shedding.
- Shadow work uses a `ThreadPoolExecutor` with fixed concurrency and a fixed-size
  `ArrayBlockingQueue`. Overflow uses `AbortPolicy` → task dropped.
- Dropped evaluations increment `shadowEvaluationsShed` and never block `/v1/chat`.
- Accepted shadow tasks compare primary vs candidate (`action` exact match) and
  update match-rate metrics.
- Any `model` field in the incoming payload is overridden per call so both models
  receive the identical prompt.
