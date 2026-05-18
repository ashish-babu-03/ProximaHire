package com.app.proximahire.controller

import com.app.proximahire.service.ResumeService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

data class ResumeUploadResponse(
    val resumeId: UUID,
    val message: String
)

@RestController
@RequestMapping("/api/resumes")
class ResumeController(private val resumeService: ResumeService) {

    @PostMapping("/upload")
    fun uploadResume(@RequestParam("file") file: MultipartFile): ResponseEntity<ResumeUploadResponse> {
        val resume = resumeService.processAndSaveResume(file)
        
        return ResponseEntity.ok(
            ResumeUploadResponse(
                resumeId = resume.id ?: throw IllegalStateException("Resume ID is null after save"),
                message = "Resume uploaded and processed successfully."
            )
        )
    }
}
