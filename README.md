# FitU Chat Service

> 학교 단위 운동 커뮤니티 **FitU**의 실시간 채팅 마이크로서비스입니다.
> WebSocket(STOMP) 연결을 담당하며, 메시지 전파는 Redis Pub/Sub, 영속화는 Redis Streams + MongoDB로 처리합니다.

**관련 레포지토리** — [fitu-backend](https://github.com/jihyun109/fitu-backend) · [monitoring-infra](https://github.com/jihyun109/monitoring-infra)

> 실사용자를 대상으로 운영한 서비스는 아닙니다. 이 문서의 모든 수치는 k6로 가상 사용자를 생성해 개선 전후를 동일 조건에서 측정한 값입니다.

---

## 왜 별도 서비스로 분리했는가

메인 서버에 채팅이 함께 있을 때, **REST API 사용자를 10명만 추가**하자 채팅과 무관한 API가 함께 느려졌습니다.

| 지표 | 채팅 단독 | 채팅 + REST 10명 |
|---|---|---|
| 채팅방 목록 조회 p95 | — | **1,050ms** (목표 500ms) |
| 게시글 조회 p95 | — | 289ms |
| CPU max | 62.8% | **94.7%** |
| Pod 재시작 | 0회 | **1회** |

부하량이 아니라 **자원을 공유하는 구조**가 원인이었습니다. WebSocket 연결은 종료될 때까지 서버 스레드를 점유하는 반면 REST 요청은 수 밀리초 내에 반환되고, 채팅 메시지 INSERT가 동일한 MySQL 커넥션을 점유해 조회 쿼리가 대기했습니다.

**검토한 방안**

| 방안 | 판단 |
|---|---|
| 커넥션 풀 증설 · 레플리카 확장 | RDS 최대 커넥션 수에 제약이 있고, 채팅과 API가 함께 증가해 전이 구조는 유지됨 |
| 동일 이미지를 역할별로 분리 배포 | 자원 격리 목적은 대부분 달성. 추가 비용이 거의 없음 |
| **별도 서비스로 분리 (채택)** | 채팅은 *동시 접속자 수*, API는 *요청 수* 기준으로 확장해야 해 스케일 기준이 다르고 저장소도 분리가 필요했음 |

> 돌아보면 동일 이미지를 역할별로 나눠 배포하는 방안을 먼저 검증한 뒤, 부족할 때 분리하는 순서가 적절했습니다. 현재 구조는 배포 파이프라인이 두 개로 늘고 공통 코드가 중복되는 비용을 초기부터 부담하게 됐습니다.

**분리 결과** — 채팅방 목록 조회 **1,050ms → 60ms**, 게시글 조회 **289ms → 207ms**, Pod 강제 종료 **0회**

---

## 주요 코드

| 관심사 | 파일 |
|---|---|
| 브로커 추상화 (교체 가능한 구조) | [`messaging/MessageBrokerPort.java`](src/main/java/com/hsp/fituchat/messaging/MessageBrokerPort.java) |
| 메시지 발행 + 트레이스 컨텍스트 주입 | [`messaging/redis/RedisChatMessageBroker.java`](src/main/java/com/hsp/fituchat/messaging/redis/RedisChatMessageBroker.java) |
| 수신 + 팬아웃 비동기 위임 | [`messaging/redis/RedisMessageSubscriber.java`](src/main/java/com/hsp/fituchat/messaging/redis/RedisMessageSubscriber.java) |
| 스레드 풀 · 거부 정책 | [`config/ChatMessageBrokerConfig.java`](src/main/java/com/hsp/fituchat/config/ChatMessageBrokerConfig.java) |
| Streams 배치 저장 컨슈머 | [`messaging/ChatMessagePersistConsumer.java`](src/main/java/com/hsp/fituchat/messaging/ChatMessagePersistConsumer.java) |
| MongoDB 문서 · 인덱스 설계 | [`document/ChatMessageDocument.java`](src/main/java/com/hsp/fituchat/document/ChatMessageDocument.java) |
| STOMP 인증 | [`config/websocket/WebSocketAuthChannelInterceptor.java`](src/main/java/com/hsp/fituchat/config/websocket/WebSocketAuthChannelInterceptor.java) |

## 아키텍처

```
 Client A ──STOMP SEND──▶ chat-service #1
                              │
                              ├─▶ Redis PUBLISH ──┬──▶ #1 broadcastExecutor ──▶ 구독 세션
                              │   (실시간 전파)    └──▶ #2 broadcastExecutor ──▶ 구독 세션
                              │
                              └─▶ Redis Streams XADD
                                        │  (영속화 — 전송 경로와 분리)
                                        ▼
                              PersistConsumer  3초 / 최대 200건
                                        │
                                        ▼
                                    MongoDB
```

**전파와 영속화를 분리한 이유** — Redis Pub/Sub은 전달을 보장하지 않지만 팬아웃이 O(1)로 가볍습니다. 그래서 실시간 전파만 담당하게 하고, 유실되면 안 되는 영속화는 ACK가 있는 Streams가 처리하도록 역할을 나눴습니다. 순단으로 실시간 전달을 놓친 사용자는 재접속 시 이력 조회로 복구합니다.

---

## 핵심 구현

### 1. 수신 스레드가 팬아웃과 저장에 점유되던 문제

**증상** — JVM 스레드가 **107개 → 241개**로 급증하고 컨테이너 메모리 한도를 초과해 강제 종료(OOMKilled)됐습니다.

**원인** — Redis 메시지를 수신하는 리스너가 **단일 스레드**임에도, 그 스레드가 채팅방 구독자 전체에 대한 전달과 DB 저장까지 동기로 처리하고 있었습니다.

**분석** — 두 도구가 같은 결론을 가리켰습니다.

- Grafana — 커넥션 풀 고갈(32/32)로 대기 스레드가 누적, 스레드당 약 1MB 스택 × 134개 ≈ 134MB 증가 → 컨테이너 한도 초과
- Pyroscope — 메시지 전송 함수가 소비한 CPU의 **82%가 JPA AOP 프록시 체인(DB save)**

**조치**

- 구독자 전달을 전용 스레드 풀에 위임해 리스너 스레드를 즉시 반환 ([`ChatMessageBrokerConfig`](src/main/java/com/hsp/fituchat/config/ChatMessageBrokerConfig.java))
- 목적지마다 반복하던 직렬화를 `byte[]` **1회로 줄여 재사용**
- DB 저장을 Redis Streams 큐로 분리하고 컨슈머가 **3초 주기로 최대 200건씩 배치 INSERT**

**결과**

| 지표 | Before | After |
|---|---|---|
| 수용 가능한 동시 접속자 | 500명 (이후 OOMKilled) | **1,000명** |
| 응답 시간 p95 | 1,210ms | **595ms** |
| 커넥션 풀 active / 대기 | 32/32 · 175 | **1/32 · 0** |
| JVM 스레드 | 241개 | **158개** |
| 컨테이너 메모리 | 698MB → 종료 | **597MB** |
| WebSocket 연결 수립 avg | 780ms | **95ms** |

### 2. 스레드 풀 거부 정책

기본 정책(`AbortPolicy`)은 큐가 포화되면 **메시지를 로그 없이 버립니다.** 채팅에서는 유실보다 지연이 낫다고 판단해 `CallerRunsPolicy`를 적용하고, 포화 상황 자체를 `chat.broadcast.rejected` 카운터로 노출했습니다.

다만 이 정책은 포화 시 호출 스레드(= 리스너)가 직접 처리하므로 **원래 문제였던 리스너 블로킹이 재현되는 트레이드오프**가 있습니다. 유실을 막기 위해 감수한 선택입니다.

### 3. Streams를 선택한 이유

`List`(`LPUSH`/`RPOP`)는 꺼내는 순간 데이터가 사라져, 꺼낸 뒤 DB 저장이 실패하면 복구할 수 없습니다. Streams의 Consumer Group은 **읽기와 확인(ACK)이 분리**되어 있어, 저장에 실패하면 ACK하지 않는 것만으로 pending에 남아 재처리됩니다. ([`ChatMessagePersistConsumer`](src/main/java/com/hsp/fituchat/messaging/ChatMessagePersistConsumer.java))

### 4. MongoDB 문서 · 인덱스 설계

```java
@Document(collection = "chat_messages")
@CompoundIndex(name = "roomId_createdAt", def = "{'chatRoomId': 1, 'createdAt': -1}")
```

조회 패턴이 세 가지로 한정되어 복합 인덱스 하나로 모두 처리합니다.

| 조회 | 인덱스 활용 |
|---|---|
| 최신 50건 | 접두사 일치 + 정렬 방향 일치 → 인덱스 끝에서 50건만 읽음 |
| 과거 이력 스크롤 | `createdAt` 커서 지점부터 스캔, `OFFSET` 없음 → 페이지 깊이와 무관 |
| 재접속 시 미수신분 | 동일 인덱스 역방향 |

**MongoDB로 전환한 이유는 성능이 아닙니다.** MySQL을 공유하는 한 자원 경쟁이 지속되기 때문이며, 이미지·입퇴장 알림 등 메시지 유형이 확장돼도 스키마 변경이 불필요하다는 점을 고려했습니다. 실제로 조회 82ms를 트레이스로 분해했을 때 **DB 호출 구간은 1.3ms**였고 나머지는 Spring 계층 통과 비용이었습니다.

### 5. 비동기 구간 트레이스 전파

OpenTelemetry 자동 계측은 HTTP 컨텍스트는 이어주지만, Redis Pub/Sub을 경유하는 구간에서는 트레이스가 끊깁니다. 발행 시 W3C `traceparent`를 메시지 페이로드에 주입하고 수신 측에서 복원합니다.

```java
// RedisMessageSubscriber — makeCurrent() 스코프 안에서 execute() 호출
try (Scope ignored = extracted.makeCurrent()) {
    broadcastExecutor.execute(() -> broadcast(brokerMessage));
}
```

팬아웃이 별도 스레드 풀에서 실행되므로, **스코프 밖에서 제출하면** 워커 스레드가 복원된 컨텍스트를 보지 못해 트레이스가 다시 끊깁니다.

---

## 실행

```bash
./gradlew clean bootJar
docker build -t fitu-chat-service .
kubectl apply -f k8s/
```

저장소는 프로파일로 전환합니다 — `mongo`(기본) / `mysql`(비교 측정용). Secret은 저장소에 포함하지 않으며 `kubectl create secret`으로 생성합니다.

## 측정 환경

Kubernetes(k3d) 단일 노드 · 레플리카 2개 · 레플리카당 CPU 0.6코어 / 메모리 700MB · `-Xmx400m` G1GC · MySQL 8(AWS RDS db.t4g.micro) · HikariCP 32 · k6 → Prometheus → Grafana

부하 시나리오는 실제 사용자처럼 **세션을 유지하도록** 작성했습니다. 초기에는 27초 주기로 재연결을 반복하는 시나리오를 사용했는데, 핸드셰이크 부하가 섞여 결과가 왜곡되어 다시 설계했습니다.

## 한계

- **유실 창** — `XADD` 성공 이전에 Pod가 종료되면 복구 수단이 없습니다. `XADD` 이후에는 ACK 전까지 pending에 남아 보호됩니다.
- **고아 pending** — 컨슈머 이름이 hostname 기반이라, Pod 교체 시 이전 컨슈머가 ACK하지 못한 엔트리를 자동으로 회수하지 않습니다.
- **쓰기 지연** — 저장이 최대 3초 지연되어, 전송 직후 이력 API를 조회하면 미반영일 수 있습니다. 실시간 전달은 Pub/Sub이 이미 완료하므로 화면상 문제는 없습니다.
- **GC pause 증가** — 배치 처리로 객체가 일시에 생성·해제되며 104ms → 140ms로 증가했습니다. 배치 크기를 줄이면 완화되지만 DB 왕복이 늘어나는 트레이드오프입니다.
- **`senderName` 검증 부재** — 서비스 간 의존을 끊기 위해 클라이언트가 전달하는 값을 사용하고 있어 서버가 검증하지 않습니다. JWT claim으로 이전하는 것이 다음 작업입니다.
