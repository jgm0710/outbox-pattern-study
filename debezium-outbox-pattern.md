# Debezium을 사용한 Outbox 패턴 구현 가이드

## 개요
Outbox 패턴은 마이크로서비스 아키텍처에서 데이터 일관성을 유지하면서 이벤트를 안정적으로 발행하기 위한 패턴입니다. 이 패턴은 데이터베이스 트랜잭션과 메시지 발행을 원자적으로 처리하여 분산 시스템에서 발생할 수 있는 문제를 해결합니다.

Debezium은 Change Data Capture(CDC) 도구로, 데이터베이스의 변경 사항을 실시간으로 감지하고 이벤트로 변환하여 Kafka와 같은 메시지 브로커로 전송합니다.

## Outbox 패턴이 필요한 이유

일반적인 마이크로서비스 아키텍처에서는 다음과 같은 문제가 발생할 수 있습니다:

1. **이중 쓰기 문제**: 데이터베이스에 쓰기 작업과 메시지 브로커에 이벤트 발행이 별도의 작업으로 이루어질 때, 둘 중 하나만 성공하고 다른 하나는 실패할 수 있습니다.
2. **트랜잭션 일관성**: 데이터베이스 변경과 메시지 발행이 하나의 원자적 작업으로 처리되지 않으면 데이터 불일치가 발생할 수 있습니다.

Outbox 패턴은 이러한 문제를 해결하기 위해 다음과 같은 접근 방식을 사용합니다:

1. 비즈니스 엔티티 변경과 함께 outbox 테이블에 이벤트 메시지를 저장합니다.
2. 별도의 프로세스(Debezium)가 outbox 테이블의 변경 사항을 감지하고 메시지 브로커로 전송합니다.

## 구현 단계

### 1. 의존성 추가

현재 프로젝트에는 Debezium 관련 의존성이 명시적으로 추가되어 있지 않습니다. 다음 의존성을 `build.gradle.kts` 파일에 추가해야 합니다:

```kotlin
dependencies {
    // 기존 의존성
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    runtimeOnly("org.postgresql:postgresql")
    
    // Kafka 관련 의존성
    implementation("org.springframework.kafka:spring-kafka")
    
    // Debezium 관련 의존성
    implementation("io.debezium:debezium-api:2.5.0.Final")
    implementation("io.debezium:debezium-embedded:2.5.0.Final")
    implementation("io.debezium:debezium-connector-postgres:2.5.0.Final") // PostgreSQL 사용 시
    
    // 테스트 의존성
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
```

### 2. Outbox 테이블 설정

현재 프로젝트에는 이미 Outbox 엔티티가 정의되어 있습니다:

```kotlin
@Entity
@Table(name = "outbox")
class Outbox(
    @Id
    @Column(name = "id")
    private val id: String,

    @Column(name = "event_type", nullable = false)
    private val eventType: String,

    @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
    private val payload: String,

    @Column(name = "created_at", nullable = false)
    private val createdAt: OffsetDateTime,

    @Column(name = "processed", nullable = false)
    private val processed: Boolean,
)
```

### 3. Debezium 설정

Debezium을 설정하기 위해 다음과 같은 설정 클래스를 추가합니다:

```kotlin
package com.example.outboxpatternstudy.config

import io.debezium.config.Configuration
import io.debezium.embedded.Connect
import io.debezium.engine.DebeziumEngine
import io.debezium.engine.format.Json
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.apache.kafka.connect.storage.FileOffsetBackingStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Component
import java.util.concurrent.Executors

@Component
class DebeziumConnectorConfig(
    @Value("\${debezium.database.hostname}")
    private val databaseHost: String,
    
    @Value("\${debezium.database.port}")
    private val databasePort: String,
    
    @Value("\${debezium.database.user}")
    private val databaseUser: String,
    
    @Value("\${debezium.database.password}")
    private val databasePassword: String,
    
    @Value("\${debezium.database.server.name}")
    private val databaseServerName: String
) {
    private lateinit var debeziumEngine: DebeziumEngine<Any, Any>
    
    @PostConstruct
    fun start() {
        val config = Configuration.create()
            .with("connector.class", "io.debezium.connector.postgresql.PostgresConnector")
            .with("offset.storage", FileOffsetBackingStore::class.java.name)
            .with("offset.storage.file.filename", "./offsets.dat")
            .with("offset.flush.interval.ms", "60000")
            .with("name", databaseServerName)
            .with("database.hostname", databaseHost)
            .with("database.port", databasePort)
            .with("database.user", databaseUser)
            .with("database.password", databasePassword)
            .with("database.dbname", "outbox_pattern_study")
            .with("table.include.list", "public.outbox")
            .with("schema.include.list", "public")
            .with("topic.prefix", "outbox-events")
            .with("transforms", "outbox")
            .with("transforms.outbox.type", "io.debezium.transforms.outbox.EventRouter")
            .with("transforms.outbox.route.by.field", "event_type")
            .with("transforms.outbox.table.field.event.id", "id")
            .with("transforms.outbox.table.field.event.key", "id")
            .with("transforms.outbox.table.field.event.payload", "payload")
            .with("transforms.outbox.table.field.event.timestamp", "created_at")
            .build()
            
        debeziumEngine = DebeziumEngine.create(Json::class.java)
            .using(config)
            .notifying { record ->
                println("Received change event: $record")
                // 여기서 Kafka로 이벤트를 전송하거나 다른 처리를 수행할 수 있습니다.
            }
            .build()
            
        val executor = Executors.newSingleThreadExecutor()
        executor.execute(debeziumEngine)
    }
    
    @PreDestroy
    fun stop() {
        if (::debeziumEngine.isInitialized) {
            debeziumEngine.close()
        }
    }
}
```

### 4. application.yml 설정 업데이트

현재 `application.yml`에는 MySQL 기반의 Debezium 설정이 있습니다. PostgreSQL을 사용하는 경우 다음과 같이 업데이트합니다:

```yaml
spring:
  application:
    name: outbox-pattern-study
  datasource:
    url: jdbc:postgresql://localhost:5432/outbox_pattern_study
    username: postgres
    password: postgres
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
    show-sql: true

debezium:
  connector.class: io.debezium.connector.postgresql.PostgresConnector
  database.hostname: localhost
  database.port: 5432
  database.user: postgres
  database.password: postgres
  database.server.id: 184054
  database.server.name: outbox-server
  table.include.list: public.outbox
  schema.include.list: public
  topic.prefix: outbox-events
```

### 5. Kafka 설정 추가

Debezium이 감지한 이벤트를 Kafka로 전송하기 위한 설정을 추가합니다:

```kotlin
package com.example.outboxpatternstudy.config

import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaAdmin
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.support.serializer.JsonSerializer

@Configuration
class KafkaConfig(
    @Value("\${spring.kafka.bootstrap-servers:localhost:9092}")
    private val bootstrapServers: String
) {
    
    @Bean
    fun kafkaAdmin(): KafkaAdmin {
        val configs = mapOf(
            AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers
        )
        return KafkaAdmin(configs)
    }
    
    @Bean
    fun orderTopic(): NewTopic {
        return NewTopic("order-events", 1, 1.toShort())
    }
    
    @Bean
    fun producerFactory(): ProducerFactory<String, Any> {
        val configProps = mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to JsonSerializer::class.java
        )
        return DefaultKafkaProducerFactory(configProps)
    }
    
    @Bean
    fun kafkaTemplate(): KafkaTemplate<String, Any> {
        return KafkaTemplate(producerFactory())
    }
}
```

### 6. Docker Compose 설정 (개발 환경용)

개발 환경에서 필요한 인프라를 쉽게 구성하기 위해 Docker Compose 파일을 추가합니다:

```yaml
version: '3'
services:
  postgres:
    image: postgres:latest
    container_name: postgres
    ports:
      - "5432:5432"
    environment:
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
      POSTGRES_DB: outbox_pattern_study
    volumes:
      - postgres_data:/var/lib/postgresql/data

  zookeeper:
    image: confluentinc/cp-zookeeper:latest
    container_name: zookeeper
    ports:
      - "2181:2181"
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000

  kafka:
    image: confluentinc/cp-kafka:latest
    container_name: kafka
    depends_on:
      - zookeeper
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:29092,PLAINTEXT_HOST://localhost:9092
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
      KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1

  kafka-ui:
    image: provectuslabs/kafka-ui:latest
    container_name: kafka-ui
    depends_on:
      - kafka
    ports:
      - "8080:8080"
    environment:
      KAFKA_CLUSTERS_0_NAME: local
      KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: kafka:29092

volumes:
  postgres_data:
```

## 사용 방법

### 1. 이벤트 발행

`OrderService`에서 이미 Outbox 패턴을 사용하여 이벤트를 발행하고 있습니다:

```kotlin
@Transactional
fun createOrder(command: CreateOrderCommand): UUID {
    val order: Order = command.createOrder()
    val objectMapper = jacksonObjectMapper()

    val outbox = Outbox(
        id = order.id.toString(),
        eventType = "OrderCreated",
        payload = objectMapper.writeValueAsString(order),
        createdAt = OffsetDateTime.now(),
        processed = false
    )

    orderRepository.save(order)
    outboxRepository.save(outbox)

    return order.id
}
```

### 2. 이벤트 소비

Debezium이 Outbox 테이블의 변경 사항을 감지하고 Kafka로 전송하면, 다른 서비스에서 이 이벤트를 소비할 수 있습니다:

```kotlin
@Component
class OrderEventConsumer(
    @Value("\${spring.kafka.consumer.group-id:order-consumer}")
    private val groupId: String
) {
    private val logger = LoggerFactory.getLogger(OrderEventConsumer::class.java)

    @KafkaListener(topics = ["order-events"], groupId = "\${spring.kafka.consumer.group-id:order-consumer}")
    fun consume(message: String) {
        logger.info("Received message: $message")
        // 이벤트 처리 로직
    }
}
```

## 장점

1. **데이터 일관성**: 비즈니스 엔티티와 이벤트 메시지가 하나의 트랜잭션으로 처리됩니다.
2. **느슨한 결합**: 서비스는 메시지 브로커와 직접 통신하지 않고, 데이터베이스 작업만 수행합니다.
3. **신뢰성**: Debezium은 데이터베이스의 트랜잭션 로그를 읽기 때문에 이벤트 손실이 없습니다.
4. **순서 보장**: 이벤트는 데이터베이스에 기록된 순서대로 발행됩니다.

## 주의사항

1. **중복 이벤트**: 소비자는 동일한 이벤트를 여러 번 받을 수 있으므로 멱등성(idempotent)을 보장해야 합니다.
2. **스키마 변경**: 이벤트 스키마가 변경되면 소비자도 업데이트해야 합니다.
3. **모니터링**: Debezium과 Kafka의 상태를 모니터링하여 문제를 빠르게 감지해야 합니다.

## 결론

Debezium을 사용한 Outbox 패턴은 마이크로서비스 아키텍처에서 데이터 일관성과 이벤트 발행의 신뢰성을 보장하는 효과적인 방법입니다. 이 패턴을 통해 분산 시스템에서 발생할 수 있는 다양한 문제를 해결하고, 서비스 간의 느슨한 결합을 유지할 수 있습니다.
