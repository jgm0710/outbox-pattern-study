package com.example.outboxpatternstudy.order

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = """"order"""")
class Order(
    @Id
    @Column(name = "id")
    val id: UUID,

    val price: Int,

    @Enumerated(EnumType.STRING)
    val status : OrderStatus,

    val createdAt: OffsetDateTime,

    val lastModifiedAt: OffsetDateTime,
)
