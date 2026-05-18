package com.app.proximahire.service

import com.app.proximahire.entity.Resume
import com.app.proximahire.repository.ResumeRepository
import com.app.proximahire.repository.UserRepository
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@Service
class ResumeService(
    private val resumeRepository: ResumeRepository,
    private val userRepository: UserRepository,
    private val embeddingService: EmbeddingService
) {

    fun processAndSaveResume(file: MultipartFile): Resume {
        val userId = extractUserIdFromContext()
        val user = userRepository.findById(userId)
            .orElseThrow { RuntimeException("User not found with ID: $userId") }

        val rawText = extractTextFromPdf(file)
        val embedding = embeddingService.generateEmbedding(rawText)

        val resume = Resume(
            user = user,
            rawText = rawText,
            embedding = embedding,
            fileName = file.originalFilename
        )

        return resumeRepository.save(resume)
    }

    private fun extractTextFromPdf(file: MultipartFile): String {
        return file.inputStream.use { inputStream ->
            val document = Loader.loadPDF(inputStream.readBytes())
            document.use {
                val pdfStripper = PDFTextStripper()
                pdfStripper.getText(it)
            }
        }
    }

    private fun extractUserIdFromContext(): UUID {
        val authentication = SecurityContextHolder.getContext().authentication
            ?: throw RuntimeException("No authentication found in security context")

        val principal = authentication.principal
        
        // Assuming Spring Security OAuth2 resource server with JWT
        if (principal is Jwt) {
            val idStr = principal.getClaimAsString("id") ?: principal.subject
            return UUID.fromString(idStr)
        }
        
        throw RuntimeException("Unsupported principal type or user ID could not be extracted")
    }
}
