package com.app.proximahire.repository

import com.app.proximahire.entity.JobDescription
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface JobDescriptionRepository : JpaRepository<JobDescription, UUID>
