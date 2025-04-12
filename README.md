# Outbox Pattern Study with Debezium

## 개요

이 프로젝트는 Debezium을 사용한 Outbox 패턴 구현 예제입니다. Outbox 패턴은 마이크로서비스 아키텍처에서 데이터 일관성을 유지하면서 이벤트를 안정적으로 발행하기 위한 패턴입니다.

## 기술 스택

- Kotlin
- Spring Boot
- Spring Data JPA
- PostgreSQL
- Kafka
- Debezium
- Flyway

## 실행 방법

### 1. 인프라 구성 요소 실행

```bash
docker-compose up -d
```

### 2. 애플리케이션 실행

```bash
./gradlew bootRun
```

### 3. 주문 생성 API 호출

```bash
curl -X POST http://localhost:8080/v1/orders \
  -H "Content-Type: application/json" \
  -d '{"price": 1000}'
```

## 구현 내용

1. 주문 생성 시 트랜잭션 내에서 Order 엔티티와 Outbox 엔티티를 함께 저장
2. Debezium이 Outbox 테이블의 변경 사항을 감지하여 Kafka 토픽으로 전송
3. 다른 서비스에서 Kafka 토픽을 구독하여 이벤트 처리

## 모니터링

- Kafka UI: http://localhost:8080
