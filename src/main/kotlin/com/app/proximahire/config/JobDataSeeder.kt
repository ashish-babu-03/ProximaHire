package com.app.proximahire.config

import com.app.proximahire.entity.JobDescription
import com.app.proximahire.repository.JobDescriptionRepository
import com.app.proximahire.service.EmbeddingService
import com.fasterxml.jackson.annotation.JsonProperty
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

data class RemotiveJob(
    val id: Long,
    val title: String,
    @JsonProperty("company_name") val companyName: String,
    val description: String,
    val category: String,
    @JsonProperty("job_type") val jobType: String
)

data class RemotiveResponse(
    val jobs: List<RemotiveJob>
)

@Component
class JobDataSeeder(
    private val jobDescriptionRepository: JobDescriptionRepository,
    private val embeddingService: EmbeddingService,
    webClientBuilder: WebClient.Builder
) : ApplicationRunner {

    private val logger = LoggerFactory.getLogger(JobDataSeeder::class.java)
    private val webClient: WebClient = webClientBuilder.build()
    private val htmlRegex = Regex("<[^>]*>")

    override fun run(args: ApplicationArguments?) {
        if (jobDescriptionRepository.count() > 0) {
            logger.info("Jobs already seeded. Skipping.")
            return
        }

        logger.info("Fetching jobs from Remotive API...")
        
        try {
            val response = webClient.get()
                .uri("https://remotive.com/api/remote-jobs?limit=50")
                .retrieve()
                .bodyToMono(RemotiveResponse::class.java)
                .block()

            val jobs = response?.jobs ?: emptyList()
            logger.info("Fetched ${jobs.size} jobs from Remotive")

            if (jobs.isEmpty()) {
                seedFallbackJobs()
                return
            }

            var seededCount = 0
            for ((index, remotiveJob) in jobs.withIndex()) {
                val cleanDescription = remotiveJob.description.replace(htmlRegex, "").trim()
                
                if (cleanDescription.length < 100) {
                    continue
                }

                logger.info("Seeding job ${index + 1}/${jobs.size}: ${remotiveJob.title} at ${remotiveJob.companyName}")
                
                val truncatedDescription = cleanDescription.take(2000)
                val embedding = embeddingService.generateEmbedding(truncatedDescription)
                
                val jobDescription = JobDescription(
                    title = remotiveJob.title,
                    company = remotiveJob.companyName,
                    description = truncatedDescription,
                    embedding = embedding
                )
                
                jobDescriptionRepository.save(jobDescription)
                seededCount++
            }
            
            logger.info("JobDataSeeder complete. $seededCount jobs seeded with embeddings.")
            
        } catch (e: Exception) {
            logger.error("Failed to fetch or seed jobs from Remotive API: ${e.message}", e)
            seedFallbackJobs()
        }
    }

    private fun seedFallbackJobs() {
        logger.info("Seeding fallback hardcoded jobs...")
        
        val fallbackJobs = listOf(
            Triple("Senior Kotlin Developer", "TechCorp Inc.", "We are looking for an experienced Kotlin developer to build highly scalable microservices. Must have 5+ years of experience and deep knowledge of Spring Boot, WebFlux, and PostgreSQL. Familiarity with AI frameworks is a plus."),
            Triple("Data Scientist", "AI Startup", "Join our AI startup! You will work with Python, PyTorch, and NLP models to build next-generation search systems. Experience with vector databases and LLMs is highly required to succeed in this dynamic role."),
            Triple("Frontend Engineer", "Creative Designs", "Looking for a Frontend Engineer with strong React and TypeScript skills. You should be able to create pixel-perfect UIs, connect to complex backend APIs, and have an exceptional eye for modern design aesthetics."),
            Triple("DevOps Engineer", "Cloud Solutions", "We need a DevOps engineer to manage our Kubernetes clusters, setup CI/CD pipelines, and ensure 99.9% uptime. Terraform and AWS experience required. Help us automate our deployment lifecycle from start to finish."),
            Triple("Product Manager", "Fintech Innovations", "We are hiring a Product Manager to lead our new mobile payment feature. You will work closely with engineering, design, and marketing to deliver a world-class product while maintaining strict timelines and budgets.")
        )
        
        var seededCount = 0
        for ((index, job) in fallbackJobs.withIndex()) {
            val (title, company, description) = job
            logger.info("Seeding fallback job ${index + 1}/${fallbackJobs.size}: $title at $company")
            
            val truncatedDescription = description.take(2000)
            val embedding = embeddingService.generateEmbedding(truncatedDescription)
            
            val jobDescription = JobDescription(
                title = title,
                company = company,
                description = truncatedDescription,
                embedding = embedding
            )
            
            jobDescriptionRepository.save(jobDescription)
            seededCount++
        }
        
        logger.info("JobDataSeeder complete. $seededCount fallback jobs seeded with embeddings.")
    }
}
