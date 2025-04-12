package com.example.outboxpatternstudy.config

import io.debezium.engine.ChangeEvent
import io.debezium.engine.DebeziumEngine
import io.debezium.engine.format.Json
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.apache.kafka.connect.storage.FileOffsetBackingStore
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.Properties
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
    private val databaseServerName: String,

    @Value("\${debezium.topic.prefix}")
    private val topicPrefix: String
) {
    private val logger = LoggerFactory.getLogger(DebeziumConnectorConfig::class.java)
    private lateinit var debeziumEngine: DebeziumEngine<ChangeEvent<String,String>>

    @PostConstruct
    fun start() {
        logger.info("Starting Debezium connector...")

        val props = Properties().apply {
            // Connector 클래스 및 기본 설정
            setProperty("connector.class", "io.debezium.connector.postgresql.PostgresConnector")
            setProperty("offset.storage", FileOffsetBackingStore::class.java.name)
            setProperty("offset.storage.file.filename", "./offsets.dat")
            setProperty("offset.flush.interval.ms", "60000")
            setProperty("name", databaseServerName)

            // 데이터베이스 연결 설정
            setProperty("database.hostname", databaseHost)
            setProperty("database.port", databasePort)
            setProperty("database.user", databaseUser)
            setProperty("database.password", databasePassword)
            setProperty("database.dbname", "outbox_pattern_study")
            setProperty("plugin.name", "pgoutput")

            // 테이블 및 스키마 설정
            setProperty("table.include.list", "public.outbox")
            setProperty("schema.include.list", "public")
            setProperty("topic.prefix", topicPrefix)

            // Outbox 변환 설정
            setProperty("transforms", "outbox")
            setProperty("transforms.outbox.type", "io.debezium.transforms.outbox.EventRouter")
            setProperty("transforms.outbox.route.by.field", "event_type")
            setProperty("transforms.outbox.table.field.event.id", "id")
            setProperty("transforms.outbox.table.field.event.key", "id")
            setProperty("transforms.outbox.table.field.event.payload", "payload")
            setProperty("transforms.outbox.table.field.event.timestamp", "created_at")
        }

        debeziumEngine = DebeziumEngine.create(Json::class.java)
            .using(props)
            .notifying { record ->
                logger.info("Received change event: $record")
                // 여기서 Kafka로 이벤트를 전송하거나 다른 처리를 수행할 수 있습니다.
            }
            .build()

        val executor = Executors.newSingleThreadExecutor()
        executor.execute(debeziumEngine)

        logger.info("Debezium connector started")
    }

    @PreDestroy
    fun stop() {
        if (::debeziumEngine.isInitialized) {
            logger.info("Stopping Debezium connector...")
            debeziumEngine.close()
            logger.info("Debezium connector stopped")
        }
    }
}
