package com.app.proximahire.controller

import com.app.proximahire.repository.JobDescriptionRepository
import com.app.proximahire.repository.ResumeRepository
import com.app.proximahire.service.EmbeddingService
import com.app.proximahire.service.GapAnalysisService
import com.app.proximahire.service.MatchClassification
import com.app.proximahire.service.MatchingService
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import java.util.UUID

data class JobWithMatchDto(
    val jobId: UUID,
    val title: String,
    val company: String,
    val matchScore: Int,
    val classification: MatchClassification
)

data class GapAnalysisResponse(
    val report: String
)

data class SearchRequest(
    val query: String
)

@RestController
@RequestMapping("/api/jobs")
class JobController(
    private val jobDescriptionRepository: JobDescriptionRepository,
    private val resumeRepository: ResumeRepository,
    private val matchingService: MatchingService,
    private val gapAnalysisService: GapAnalysisService,
    private val embeddingService: EmbeddingService
) {

    private val logger = LoggerFactory.getLogger(JobController::class.java)

    @GetMapping
    fun getAllJobsWithMatchScores(): ResponseEntity<List<JobWithMatchDto>> {
        val userId = extractUserIdFromContext()
        logger.info("Fetching match scores for userId: $userId")
        val startTime = System.currentTimeMillis()
        
        val latestResume = resumeRepository.findByUserIdOrderByCreatedAtDesc(userId).firstOrNull()
            ?: throw RuntimeException("No resume found for the authenticated user. Please upload a resume first.")

        val jobs = jobDescriptionRepository.findAll()
        
        val jobDtos = jobs.map { job ->
            val matchResult = matchingService.calculateMatch(latestResume.id!!, job.id!!)
            JobWithMatchDto(
                jobId = job.id!!,
                title = job.title,
                company = job.company,
                matchScore = matchResult.score,
                classification = matchResult.classification
            )
        }.sortedByDescending { it.matchScore } // Sorted by best match

        val duration = System.currentTimeMillis() - startTime
        logger.info("Match scores calculated for ${jobs.size} jobs in ${duration}ms")

        return ResponseEntity.ok(jobDtos)
    }

    @PostMapping("/search")
    fun searchJobs(@RequestBody request: SearchRequest): ResponseEntity<List<JobWithMatchDto>> {
        val queryEmbedding = embeddingService.generateEmbedding(request.query)
        val allJobs = jobDescriptionRepository.findAll()

        val results = allJobs.mapNotNull { job ->
            val jobEmbedding = job.embedding ?: return@mapNotNull null
            
            try {
                val matchResult = matchingService.calculateMatch(queryEmbedding, jobEmbedding)
                JobWithMatchDto(
                    jobId = job.id!!,
                    title = job.title,
                    company = job.company,
                    matchScore = matchResult.score,
                    classification = matchResult.classification
                )
            } catch (e: IllegalArgumentException) {
                null
            }
        }
        .sortedByDescending { it.matchScore }
        .take(5)

        return ResponseEntity.ok(results)
    }

    @GetMapping("/{jobId}/gap-analysis")
    fun getGapAnalysis(@PathVariable jobId: UUID): ResponseEntity<GapAnalysisResponse> {
        val userId = extractUserIdFromContext()
        val latestResume = resumeRepository.findByUserIdOrderByCreatedAtDesc(userId).firstOrNull()
            ?: throw RuntimeException("No resume found for the authenticated user. Please upload a resume first.")

        val job = jobDescriptionRepository.findById(jobId)
            .orElseThrow { RuntimeException("Job not found with ID: $jobId") }

        val resumeText = latestResume.rawText ?: throw RuntimeException("Resume text is empty.")
        val jobText = job.description ?: throw RuntimeException("Job description is empty.")

        val report = gapAnalysisService.analyzeGap(resumeText, jobText)

        return ResponseEntity.ok(GapAnalysisResponse(report))
    }

    // Secondary endpoint if you want to stream the text back in real-time
    @GetMapping("/{jobId}/gap-analysis/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamGapAnalysis(@PathVariable jobId: UUID): Flux<String> {
        val userId = extractUserIdFromContext()
        val latestResume = resumeRepository.findByUserIdOrderByCreatedAtDesc(userId).firstOrNull()
            ?: throw RuntimeException("No resume found for the authenticated user. Please upload a resume first.")

        val job = jobDescriptionRepository.findById(jobId)
            .orElseThrow { RuntimeException("Job not found with ID: $jobId") }

        val resumeText = latestResume.rawText ?: ""
        val jobText = job.description ?: ""

        return gapAnalysisService.analyzeGapStream(resumeText, jobText)
    }

    private fun extractUserIdFromContext(): UUID {
        val authentication = SecurityContextHolder.getContext().authentication
            ?: throw RuntimeException("No authentication found in security context")

        val principal = authentication.principal
        
        if (principal is Jwt) {
            val idStr = principal.getClaimAsString("id") ?: principal.subject
            return UUID.fromString(idStr)
        }
        
        throw RuntimeException("Unsupported principal type or user ID could not be extracted")
    }
}
