package com.app.proximahire.repository

import com.app.proximahire.entity.Resume
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ResumeRepository : JpaRepository<Resume, UUID> {
    fun findByUserIdOrderByCreatedAtDesc(userId: UUID): List<Resume>
}
