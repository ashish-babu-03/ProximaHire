package com.app.proximahire.entity

import jakarta.persistence.*
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "resumes")
class Resume(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @Column(name = "raw_text", columnDefinition = "TEXT")
    var rawText: String? = null,

    @Column(columnDefinition = "float4[]")
    var embedding: FloatArray? = null,

    @Column(name = "file_name")
    var fileName: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now()
)
