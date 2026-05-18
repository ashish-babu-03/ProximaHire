package com.app.proximahire.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux

data class OllamaGenerateRequest(
    val model: String,
    val prompt: String,
    val stream: Boolean = true
)

data class OllamaGenerateResponse(
    val model: String? = null,
    val response: String = "",
    val done: Boolean = false
)

@Service
class GapAnalysisService(private val webClientBuilder: WebClient.Builder) {

    private val logger = LoggerFactory.getLogger(GapAnalysisService::class.java)

    @Value("\${ollama.url:http://localhost:11434}")
    lateinit var ollamaUrl: String

    @Value("\${ollama.generate.model:llama3.2}")
    lateinit var generateModel: String

    /**
     * Generates a gap analysis and returns it as a reactive stream (Flux).
     * This processes the NDJSON stream from Ollama and emits text chunks in real-time.
     */
    fun analyzeGapStream(resumeText: String, jobDescriptionText: String): Flux<String> {
        val client = webClientBuilder.baseUrl(ollamaUrl).build()
        val prompt = buildPrompt(resumeText, jobDescriptionText)
        val request = OllamaGenerateRequest(model = generateModel, prompt = prompt, stream = true)

        return client.post()
            .uri("/api/generate")
            .bodyValue(request)
            .retrieve()
            .bodyToFlux(OllamaGenerateResponse::class.java)
            .map { it.response }
            .onErrorResume { e -> 
                Flux.error(RuntimeException("Error communicating with Ollama local API: ${e.message}", e))
            }
    }

    /**
     * Generates a gap analysis and blocks until the full text is aggregated.
     * Useful for synchronous operations where real-time streaming isn't required.
     */
    fun analyzeGap(resumeText: String, jobDescriptionText: String): String {
        logger.info("Starting gap analysis...")
        val startTime = System.currentTimeMillis()
        
        val client = webClientBuilder.baseUrl(ollamaUrl).build()
        val result = analyzeGapStream(resumeText, jobDescriptionText)
            .reduce { acc, chunk -> acc + chunk }
            .block() ?: throw RuntimeException("Failed to generate gap analysis: Stream returned empty")
            
        val duration = System.currentTimeMillis() - startTime
        logger.info("Gap analysis completed in ${duration}ms")
        
        return result
    }

    private fun buildPrompt(resumeText: String, jobDescriptionText: String): String {
        return """
            You are an expert technical recruiter and HR assistant. 
            Please analyze the gap between the following candidate's resume and the target job description.
            
            Provide a structured gap report explaining:
            1. Estimated Match Percentage
            2. Strong Areas (where the candidate meets or exceeds requirements)
            3. Missing Skills (areas for improvement or gaps)
            
            Keep the report in plain English, well-formatted, and concise.

            --- JOB DESCRIPTION ---
            $jobDescriptionText

            --- CANDIDATE RESUME ---
            $resumeText
            
            --- GAP ANALYSIS REPORT ---
        """.trimIndent()
    }
}
