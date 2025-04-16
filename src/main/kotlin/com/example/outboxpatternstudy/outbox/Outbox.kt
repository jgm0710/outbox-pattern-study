package com.example.outboxpatternstudy.outbox

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.type.SqlTypes
import org.hibernate.annotations.JdbcTypeCode
import java.time.OffsetDateTime


@Entity
@Table(name = "outbox")
class Outbox(

    @Id
    @Column(name = "id")
    private val id: String,

    @Column(name = "event_type", nullable = false)
    private val eventType: String,

    @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private val payload: String,

    @Column(name = "created_at", nullable = false)
    private val createdAt: OffsetDateTime,

    @Column(name = "processed", nullable = false)
    private val processed: Boolean,
) {
    // Getter 메서드 추가
    fun getId(): String = id
    fun getEventType(): String = eventType
    fun getPayload(): String = payload
    fun getCreatedAt(): OffsetDateTime = createdAt
    fun isProcessed(): Boolean = processed
}
