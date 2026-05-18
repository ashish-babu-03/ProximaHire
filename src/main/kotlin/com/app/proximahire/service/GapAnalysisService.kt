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

data class OpenRouterMessage(val role: String, val content: String)
data class OpenRouterRequest(val model: String, val messages: List<OpenRouterMessage>)
data class OpenRouterChoice(val message: OpenRouterMessage)
data class OpenRouterResponse(val choices: List<OpenRouterChoice>)

@Service
class GapAnalysisService(private val webClientBuilder: WebClient.Builder) {

    private val logger = LoggerFactory.getLogger(GapAnalysisService::class.java)

    @Value("\${llm.provider:ollama}")
    lateinit var llmProvider: String

    @Value("\${ollama.url:http://localhost:11434}")
    lateinit var ollamaUrl: String

    @Value("\${ollama.generate.model:llama3.2}")
    lateinit var ollamaModel: String

    @Value("\${openrouter.api.key:}")
    lateinit var openRouterApiKey: String

    @Value("\${openrouter.model:meta-llama/llama-3.2-3b-instruct:free}")
    lateinit var openRouterModel: String

    /**
     * Generates a gap analysis and returns it as a reactive stream (Flux).
     */
    fun analyzeGapStream(resumeText: String, jobDescriptionText: String, matchScore: Int): Flux<String> {
        val prompt = buildPrompt(resumeText, jobDescriptionText, matchScore)

        if (llmProvider == "openrouter") {
            return Flux.defer {
                val client = webClientBuilder.baseUrl("https://openrouter.ai/api/v1/chat/completions").build()
                val request = OpenRouterRequest(
                    model = openRouterModel,
                    messages = listOf(OpenRouterMessage("user", prompt))
                )
                
                try {
                    val response = client.post()
                        .header("Authorization", "Bearer $openRouterApiKey")
                        .header("Content-Type", "application/json")
                        .bodyValue(request)
                        .retrieve()
                        .bodyToMono(OpenRouterResponse::class.java)
                        .block()
                        
                    val content = response?.choices?.getOrNull(0)?.message?.content 
                        ?: throw RuntimeException("Empty response from OpenRouter")
                    Flux.just(content)
                } catch (e: Exception) {
                    Flux.error(RuntimeException("Error communicating with OpenRouter API: ${e.message}", e))
                }
            }
        }

        // Ollama local stream logic
        val client = webClientBuilder.baseUrl(ollamaUrl).build()
        val request = OllamaGenerateRequest(model = ollamaModel, prompt = prompt, stream = true)

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
     */
    fun analyzeGap(resumeText: String, jobDescriptionText: String, matchScore: Int): String {
        logger.info("Starting gap analysis via \$llmProvider... (pre-computed score=$matchScore)")
        val startTime = System.currentTimeMillis()
        
        val result = analyzeGapStream(resumeText, jobDescriptionText, matchScore)
            .reduce { acc, chunk -> acc + chunk }
            .block() ?: throw RuntimeException("Failed to generate gap analysis: Stream returned empty")
            
        val duration = System.currentTimeMillis() - startTime
        logger.info("Gap analysis completed in \${duration}ms")
        
        return result
    }

    private fun buildPrompt(resumeText: String, jobDescriptionText: String, matchScore: Int): String {
        return """
            You are an expert technical recruiter and HR assistant.
            Be concise. Maximum 300 words total.
            Please analyze the gap between the following candidate's resume and the target job description.
            
            The semantic similarity between this resume and the job description has already been computed using vector cosine similarity and is exactly $matchScore out of 100. Use this exact number for the match percentage — do not estimate or compute your own.
            
            Provide a structured gap report with exactly these three sections:
            1. Match Percentage: $matchScore/100
            2. Strong Areas (where the candidate meets or exceeds requirements)
            3. Missing Skills (areas for improvement or gaps)
            
            Stop after providing these three sections only. Keep the report in plain English, well-formatted, and concise.

            --- JOB DESCRIPTION ---
            $jobDescriptionText

            --- CANDIDATE RESUME ---
            $resumeText
            
            IMPORTANT: The match score is $matchScore/100 — use this exact number, do not change it. Stop after the Missing Skills section. Do not add recommendations, next steps, conclusions, or any additional sections.
            
            --- GAP ANALYSIS REPORT ---
        """.trimIndent()
    }
}
