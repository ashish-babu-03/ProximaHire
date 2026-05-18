package com.app.proximahire.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient

data class OllamaEmbeddingRequest(
    val model: String,
    val prompt: String
)

data class OllamaEmbeddingResponse(
    val embedding: List<Float>
)

@Service
class EmbeddingService(private val webClientBuilder: WebClient.Builder) {

    private val logger = LoggerFactory.getLogger(EmbeddingService::class.java)

    @Value("\${ollama.url:http://localhost:11434}")
    lateinit var ollamaUrl: String

    @Value("\${ollama.embedding.model:nomic-embed-text}")
    lateinit var embeddingModel: String

    fun generateEmbedding(text: String): FloatArray {
        logger.info("Generating embedding for text of length: ${text.length}")
        val startTime = System.currentTimeMillis()
        
        val client = webClientBuilder.baseUrl(ollamaUrl).build()
        val request = OllamaEmbeddingRequest(model = embeddingModel, prompt = text)

        val response = client.post()
            .uri("/api/embeddings")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(OllamaEmbeddingResponse::class.java)
            .block()

        val duration = System.currentTimeMillis() - startTime
        logger.info("Embedding generated in ${duration}ms")
        
        return response?.embedding?.toFloatArray() ?: throw RuntimeException("Failed to generate embedding")
    }
}
