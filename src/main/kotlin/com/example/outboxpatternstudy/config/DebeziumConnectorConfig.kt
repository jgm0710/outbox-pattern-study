package com.example.outboxpatternstudy.config

import io.debezium.config.Configuration
import io.debezium.embedded.Connect
import io.debezium.engine.DebeziumEngine
import io.debezium.engine.format.Json
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.apache.kafka.connect.storage.FileOffsetBackingStore
import org.slf4j.LoggerFactory
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
    private val databaseServerName: String,
    
    @Value("\${debezium.topic.prefix}")
    private val topicPrefix: String
) {
    private val logger = LoggerFactory.getLogger(DebeziumConnectorConfig::class.java)
    private lateinit var debeziumEngine: DebeziumEngine<Any, Any>
    
    @PostConstruct
    fun start() {
        logger.info("Starting Debezium connector...")
        
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
            .with("plugin.name", "pgoutput")
            .with("table.include.list", "public.outbox")
            .with("schema.include.list", "public")
            .with("topic.prefix", topicPrefix)
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
