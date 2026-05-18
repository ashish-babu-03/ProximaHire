package com.app.proximahire.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
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

    @Value("\${embedding.provider:ollama}")
    lateinit var embeddingProvider: String

    @Value("\${ollama.url:http://localhost:11434}")
    lateinit var ollamaUrl: String

    @Value("\${ollama.embedding.model:nomic-embed-text}")
    lateinit var ollamaModel: String

    @Value("\${huggingface.api.key:}")
    lateinit var huggingfaceApiKey: String

    @Value("\${huggingface.model:sentence-transformers/all-MiniLM-L6-v2}")
    lateinit var huggingfaceModel: String

    fun generateEmbedding(text: String): FloatArray {
        logger.info("Generating embedding for text of length: ${text.length} via $embeddingProvider")
        val startTime = System.currentTimeMillis()

        val result = if (embeddingProvider == "huggingface") {
            generateHuggingFaceEmbedding(text)
        } else {
            generateOllamaEmbedding(text)
        }

        val duration = System.currentTimeMillis() - startTime
        logger.info("Embedding generated in ${duration}ms (dims=${result.size})")

        return result
    }

    private fun generateOllamaEmbedding(text: String): FloatArray {
        val client = webClientBuilder.baseUrl(ollamaUrl).build()
        val request = OllamaEmbeddingRequest(model = ollamaModel, prompt = text)

        val response = client.post()
            .uri("/api/embeddings")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(OllamaEmbeddingResponse::class.java)
            .block()

        return response?.embedding?.toFloatArray()
            ?: throw RuntimeException("Failed to generate Ollama embedding: empty response")
    }

    private fun generateHuggingFaceEmbedding(text: String): FloatArray {
        // HuggingFace Router inference endpoint — inputs is a plain string
        val url = "https://router.huggingface.co/hf-inference/models/$huggingfaceModel/pipeline/feature-extraction"
        val client = webClientBuilder.baseUrl(url).build()

        // inputs is a plain String (not an array)
        val requestBody = mapOf("inputs" to text)

        // Response is a flat List<Float> — the embedding vector directly
        val responseType = object : ParameterizedTypeReference<List<Float>>() {}

        val response = client.post()
            .header("Authorization", "Bearer $huggingfaceApiKey")
            .header("Content-Type", "application/json")
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(responseType)
            .block()

        if (response.isNullOrEmpty()) {
            throw RuntimeException("Failed to generate HuggingFace embedding: empty response")
        }

        return response.toFloatArray()
    }
}
