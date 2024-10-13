package com.example.outboxpatternstudy.domainevent

interface DomainEventPublisher {

    fun publish(domainEvent: DomainEvent)
}
