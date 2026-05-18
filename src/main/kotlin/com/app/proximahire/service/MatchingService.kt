package com.app.proximahire.service

import com.app.proximahire.repository.JobDescriptionRepository
import com.app.proximahire.repository.ResumeRepository
import org.springframework.stereotype.Service
import java.util.UUID
import kotlin.math.sqrt

enum class MatchClassification {
    GREEN, ORANGE, RED
}

data class MatchResult(
    val score: Int,
    val classification: MatchClassification
)

@Service
class MatchingService(
    private val resumeRepository: ResumeRepository,
    private val jobDescriptionRepository: JobDescriptionRepository
) {

    fun calculateMatch(resumeId: UUID, jobDescriptionId: UUID): MatchResult {
        val resume = resumeRepository.findById(resumeId)
            .orElseThrow { IllegalArgumentException("Resume not found with ID: $resumeId") }
        
        val jobDescription = jobDescriptionRepository.findById(jobDescriptionId)
            .orElseThrow { IllegalArgumentException("Job Description not found with ID: $jobDescriptionId") }

        val resumeEmbedding = resume.embedding 
            ?: throw IllegalStateException("Resume has no embedding generated")
        
        val jobEmbedding = jobDescription.embedding
            ?: throw IllegalStateException("Job Description has no embedding generated")

        return calculateMatch(resumeEmbedding, jobEmbedding)
    }

    fun calculateMatch(embeddingA: FloatArray, embeddingB: FloatArray): MatchResult {
        if (embeddingA.size != embeddingB.size) {
            throw IllegalArgumentException("Embeddings dimension mismatch: ${embeddingA.size} vs ${embeddingB.size}")
        }

        val similarity = cosineSimilarity(embeddingA, embeddingB)
        
        // Convert cosine similarity (which ranges from -1.0 to 1.0) to a 0-100 score.
        // For text embeddings, values are usually positive. We cap it between 0 and 100.
        val score = (similarity * 100).toInt().coerceIn(0, 100)

        val classification = when {
            score > 70 -> MatchClassification.GREEN
            score >= 40 -> MatchClassification.ORANGE
            else -> MatchClassification.RED
        }

        return MatchResult(score, classification)
    }

    private fun cosineSimilarity(vectorA: FloatArray, vectorB: FloatArray): Double {
        var dotProduct = 0.0
        var normA = 0.0
        var normB = 0.0

        for (i in vectorA.indices) {
            val a = vectorA[i].toDouble()
            val b = vectorB[i].toDouble()
            dotProduct += a * b
            normA += a * a
            normB += b * b
        }

        if (normA == 0.0 || normB == 0.0) return 0.0

        return dotProduct / (sqrt(normA) * sqrt(normB))
    }
}
