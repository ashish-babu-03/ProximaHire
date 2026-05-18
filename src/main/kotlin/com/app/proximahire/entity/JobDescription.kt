package com.app.proximahire.entity

import jakarta.persistence.*
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "job_descriptions")
class JobDescription(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(nullable = false)
    var title: String,

    @Column(nullable = false)
    var company: String,

    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    @Column(columnDefinition = "float4[]")
    var embedding: FloatArray? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now()
)
