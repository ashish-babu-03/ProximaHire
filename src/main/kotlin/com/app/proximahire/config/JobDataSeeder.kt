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
        
        var seededCount = 0
        try {
            val response1 = webClient.get()
                .uri("https://remotive.com/api/remote-jobs?limit=150")
                .retrieve()
                .bodyToMono(RemotiveResponse::class.java)
                .block()
                
            val response2 = webClient.get()
                .uri("https://remotive.com/api/remote-jobs?category=software-dev&limit=100")
                .retrieve()
                .bodyToMono(RemotiveResponse::class.java)
                .block()

            val jobs1 = response1?.jobs ?: emptyList()
            val jobs2 = response2?.jobs ?: emptyList()
            val allJobs = jobs1 + jobs2
            
            val uniqueJobs = allJobs.distinctBy { "${it.title.lowercase()}|${it.companyName.lowercase()}" }
            
            logger.info("Fetched ${allJobs.size} jobs from Remotive. Deduplicated to ${uniqueJobs.size} unique jobs.")

            for ((index, remotiveJob) in uniqueJobs.withIndex()) {
                val cleanDescription = remotiveJob.description.replace(htmlRegex, "").trim()
                
                if (cleanDescription.length < 100) {
                    continue
                }

                logger.info("Seeding job ${index + 1}/${uniqueJobs.size}: ${remotiveJob.title} at ${remotiveJob.companyName}")
                
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
            
            logger.info("Seeded $seededCount jobs from Remotive.")
            
        } catch (e: Exception) {
            logger.error("Failed to fetch jobs from Remotive API: ${e.message}", e)
        }

        // If we didn't get enough jobs from the API, or just want to supplement the list
        if (seededCount < 30) {
            logger.info("Remotive seeded fewer than 30 jobs ($seededCount). Supplementing with 60 hardcoded fallback jobs.")
            seedFallbackJobs()
        } else {
            logger.info("We have enough jobs from Remotive ($seededCount). We will ALSO seed hardcoded jobs to ensure a rich dataset.")
            seedFallbackJobs()
        }
    }

    private fun expandDescription(core: String): String {
        val intro = "We are a fast-growing, innovative company looking to scale our teams with exceptional talent. Our culture is built on transparency, collaboration, and a relentless drive for engineering excellence. "
        val responsibilities = "As a key member of our organization, you will be expected to take ownership of complex projects from inception to production. You will collaborate closely with cross-functional teams including product managers, designers, and other engineers to deliver high-quality, scalable solutions. You will participate in code reviews, architecture discussions, and help mentor junior team members. Ensuring the reliability and performance of our systems is paramount. You will also contribute to our agile processes, participating in sprint planning, daily stand-ups, and retrospectives to continuously improve our workflow and team dynamics. "
        val qualifications = "The ideal candidate will have a proven track record of delivering robust software solutions in a fast-paced environment. Strong problem-solving skills, excellent communication abilities, and a deep understanding of software engineering principles are essential. We value continuous learners who stay up-to-date with the latest industry trends and technologies. You should be comfortable working in a distributed, remote-friendly environment and be able to manage your time effectively. "
        val benefits = "We offer a highly competitive compensation package including equity, comprehensive health insurance (medical, dental, and vision), a generous 401(k) match, and unlimited PTO. Our wellness programs include gym stipends, mental health support, and flexible working hours to ensure a healthy work-life balance. We also provide a generous annual learning and development budget to help you grow your career, along with home office stipends to ensure you have the best tools available. Join us in building the future of our industry!"
        
        return "$intro\n\n$core\n\n$responsibilities\n\n$qualifications\n\n$benefits"
    }

    private fun seedFallbackJobs() {
        logger.info("Seeding realistic hardcoded jobs...")
        
        val fallbackJobs = listOf(
            // 10 Backend
            Triple("Senior Kotlin Developer", "Stripe", "We are looking for a backend expert to build payment processing microservices using Kotlin, Spring Boot, and PostgreSQL. You will design resilient APIs capable of handling millions of transactions daily."),
            Triple("Backend Engineer, Java", "Notion", "Join our core infrastructure team to optimize and scale our backend written in Java. You'll work with complex Postgres queries, distributed caching, and microservices architecture to ensure low latency."),
            Triple("Python Developer", "Linear", "Help us build the fastest issue tracker on the internet. You will work on our Python and Django backend, designing fast GraphQL resolvers and optimizing database access patterns."),
            Triple("Go Software Engineer", "Vercel", "We need a strong Go developer to work on our edge network and deployment pipeline. Deep knowledge of concurrency in Go, distributed systems, and performance profiling is required."),
            Triple("Node.js Backend Lead", "Figma", "Lead a team of backend engineers scaling our Node.js and TypeScript services. You will design highly concurrent multiplayer sync engines using WebSockets and Redis."),
            Triple("Mid-level Java Engineer", "Atlassian", "Work on the Jira backend platform. You will build new RESTful APIs using Java and Spring Boot, and implement asynchronous event-driven architectures using Apache Kafka."),
            Triple("Senior Python Engineer", "Datadog", "Build massive scale data ingestion pipelines. You will write highly optimized Python using Asyncio, interfacing with various cloud APIs to pull and process terabytes of telemetry data."),
            Triple("Kotlin Backend Architect", "MongoDB", "Design the next generation of our cloud management platform. You will use Kotlin and Ktor to build highly available orchestration systems, mentoring junior developers along the way."),
            Triple("Junior Go Developer", "Twilio", "Kickstart your career by joining our messaging API team. You will write Go code to handle SMS routing, participate in code reviews, and learn how to build highly available distributed systems."),
            Triple("Backend Systems Engineer", "Shopify", "Join our platform team to scale the world's leading e-commerce engine. You will write performant Ruby and Go code, optimizing MySQL queries and building caching layers with Memcached."),

            // 8 Frontend
            Triple("Senior React Engineer", "Vercel", "Build the future of the web with Next.js and React. You will create highly reusable UI components using Tailwind CSS, ensuring accessibility and perfect lighthouse performance scores."),
            Triple("Frontend Developer, Vue", "GitLab", "Join our frontend team to improve the GitLab UI. You will write Vue.js and Nuxt applications, styling with SCSS, and implementing complex interactive code review interfaces."),
            Triple("Angular UI Engineer", "EnterpriseCorp", "Develop robust enterprise dashboards using Angular, RxJS, and TypeScript. You will work on complex state management, real-time data visualization, and comprehensive unit testing with Karma."),
            Triple("Lead Frontend Architect", "Figma", "Drive the architectural direction of our browser-based design tool. You need deep expertise in React, WebGL, Canvas API, and browser performance optimization."),
            Triple("Mid-level React Developer", "Stripe", "Help build our merchant dashboard. You will write clean, maintainable React code, manage state with Redux, and write rigorous tests using Jest and React Testing Library."),
            Triple("Frontend Specialist", "Notion", "Work on our core editor experience. You will deal with complex DOM manipulation, Prosemirror integrations, and advanced CSS to create a seamless typing experience."),
            Triple("Junior Vue.js Engineer", "StartupX", "Join our fast-paced startup to build customer-facing web apps. You will use Vue 3, the Composition API, and Pinia for state management in a collaborative agile team."),
            Triple("Senior UI/UX Engineer", "Linear", "Bridge the gap between design and engineering. You will use React and Framer Motion to build delightful micro-interactions and extremely polished, keyboard-first user interfaces."),

            // 6 Full Stack
            Triple("Full Stack Engineer", "Vercel", "Own features end-to-end. You will build React components on the frontend and write Node.js serverless functions connected to PostgreSQL databases on the backend."),
            Triple("Senior Full Stack Developer", "Shopify", "Work across the entire stack to build new merchant features. You will use React for the frontend and Ruby on Rails for the backend, focusing on scalability and clean architecture."),
            Triple("Mid Full Stack Engineer", "Datadog", "Build internal tools and customer-facing dashboards. You will write Vue.js on the frontend and build robust Python and Go microservices to serve high-throughput data."),
            Triple("Full Stack Lead", "Twilio", "Lead a cross-functional pod building communication tools. You will architect React single-page applications and connect them to highly scalable Java Spring Boot backend services."),
            Triple("Full Stack Web Developer", "Notion", "Build collaborative workspace features from the database up to the UI. You will use TypeScript extensively, working with React, Node.js, and Postgres."),
            Triple("Junior Full Stack Engineer", "TechCorp", "Start your full stack journey. You will maintain Angular frontends, build Node.js APIs, and manage data in MongoDB, with plenty of mentorship from senior engineers."),

            // 6 DevOps and Cloud
            Triple("DevOps Engineer", "Stripe", "Automate our infrastructure deployment. You will manage AWS resources using Terraform, build resilient CI/CD pipelines in GitHub Actions, and ensure high availability of our payment systems."),
            Triple("Cloud Infrastructure Engineer", "Vercel", "Build the infrastructure that powers the modern web. You will manage large-scale Kubernetes clusters on GCP, optimizing container deployments with Docker and Helm."),
            Triple("Senior DevOps Architect", "Datadog", "Design the next generation of our massive telemetry platform. You will architect multi-region AWS environments, pushing the limits of Kubernetes scaling and networking."),
            Triple("Platform Engineer", "Shopify", "Provide internal developer platforms. You will write custom Kubernetes operators in Go, manage GCP infrastructure with Terraform, and build tools to increase developer velocity."),
            Triple("Site Reliability & DevOps Lead", "Atlassian", "Lead a team focused on uptime. You will configure AWS infrastructure, write Ansible playbooks, and manage Jenkins pipelines for continuous delivery across multiple products."),
            Triple("Junior Cloud Engineer", "TechStartup", "Help us transition to the cloud. You will get hands-on experience provisioning AWS EC2 and S3 resources, writing Dockerfiles, and managing Linux servers."),

            // 5 Data Engineering
            Triple("Data Engineer", "Stripe", "Build robust financial data pipelines. You will write Apache Spark jobs, orchestrate workflows with Apache Airflow, and process data using Python to ensure accurate ledger accounting."),
            Triple("Senior Data Infrastructure Engineer", "Datadog", "Scale our real-time data ingestion. You will manage massive Apache Kafka clusters, write Apache Flink streaming jobs in Java, and ensure low-latency data availability."),
            Triple("Big Data Engineer", "MongoDB", "Process internal telemetry at scale. You will work with Hadoop, write complex Scala code for Spark, and build data lakes to support our analytics team."),
            Triple("Analytics Engineer", "Figma", "Transform raw data into business insights. You will write advanced SQL, use dbt for data transformation, and model data in Snowflake for our product and marketing teams."),
            Triple("Mid-level Data Engineer", "Shopify", "Build pipelines for e-commerce analytics. You will write Python scripts, manage BigQuery datasets, and stream events using Kafka to power merchant dashboards."),

            // 5 AI and ML
            Triple("Machine Learning Engineer", "OpenAI", "Push the boundaries of artificial intelligence. You will write highly optimized Python code, train massive neural networks using PyTorch, and deploy LLMs to production."),
            Triple("AI Researcher", "Anthropic", "Conduct cutting-edge research on AI alignment and capabilities. You will work with Transformer architectures, perform NLP experiments in Python, and publish your findings."),
            Triple("RAG / LLM Engineer", "Notion", "Build AI-powered workspace features. You will use LangChain, integrate with Vector Databases like Pinecone, and optimize prompts to create an intelligent AI assistant."),
            Triple("Senior ML Infrastructure Engineer", "Datadog", "Build the platform that powers our ML models. You will deploy models on Kubernetes, manage experiment tracking with MLflow, and optimize GPU utilization in Python."),
            Triple("AI Software Engineer", "Stripe", "Detect fraud using machine learning. You will build and deploy predictive models using TensorFlow and Python, integrating them into our high-throughput payment flow."),

            // 4 Mobile
            Triple("iOS Developer", "Figma", "Bring our collaborative tools to mobile. You will write clean Swift code, build complex interfaces with UIKit and SwiftUI, and optimize rendering performance on iOS devices."),
            Triple("Android Engineer", "Stripe", "Build our Android payment SDK. You will write modern Kotlin code, leverage Jetpack Compose for UI, and use Coroutines for asynchronous network operations."),
            Triple("React Native Developer", "Shopify", "Build our merchant-facing mobile app. You will use React Native and TypeScript to build cross-platform features, managing state with Redux and ensuring native-like performance."),
            Triple("Senior Mobile Architect", "Twilio", "Define the architecture for our mobile communication SDKs. You will design robust APIs for both iOS and Android platforms, and establish CI/CD practices for mobile."),

            // 4 Product Manager
            Triple("Technical Product Manager", "Linear", "Drive the development of developer-focused features. You will define API designs, write technical specs, manage the Jira backlog, and run Agile sprints with the engineering team."),
            Triple("Senior Product Manager", "Notion", "Lead product growth initiatives. You will design A/B tests, analyze user behavior data, and develop the strategic roadmap for our collaborative workspace platform."),
            Triple("Product Lead, Platform", "Vercel", "Own the developer experience. You will gather feedback from the community, prioritize platform features, and ensure we provide the best possible deployment workflow."),
            Triple("PM, Data Products", "Datadog", "Build products for data scientists and engineers. You will define requirements for new data visualization tools and telemetry analytics dashboards."),

            // 3 QA and Testing
            Triple("Senior QA Engineer", "Stripe", "Ensure the reliability of our payment flows. You will write end-to-end automated tests using Cypress and Selenium, and integrate them into our CI/CD pipelines."),
            Triple("Software Engineer in Test", "Atlassian", "Build robust testing frameworks. You will write automated integration tests in Java using JUnit, and create tools to help developers test their code more effectively."),
            Triple("Manual & Automation Tester", "TechCorp", "Maintain high software quality. You will write test plans, perform manual exploratory testing, and automate API tests using Postman and Jest."),

            // 3 Tech Lead / Architect
            Triple("Principal Software Architect", "MongoDB", "Design the future of our database engine. You will architect complex distributed systems, write high-performance C++ and Go, and guide the technical vision of the company."),
            Triple("Engineering Manager / Tech Lead", "Stripe", "Lead a team of backend engineers. You will balance technical architecture in Java with people management, providing mentorship and driving project delivery."),
            Triple("Cloud Architecture Lead", "Vercel", "Design scalable cloud infrastructure. You will architect serverless solutions on AWS, design highly available system topologies, and ensure robust disaster recovery plans."),

            // 3 Security
            Triple("Application Security Engineer", "Twilio", "Secure our communication platform. You will perform penetration testing, implement OWASP best practices, and write Python scripts to automate vulnerability scanning."),
            Triple("Cloud Security Architect", "Datadog", "Protect our cloud environments. You will design secure AWS architectures, manage IAM policies, and implement continuous security auditing and monitoring."),
            Triple("Information Security Analyst", "Shopify", "Ensure our compliance and security posture. You will manage SOC2 audits, assess third-party vendor risk, and develop internal security policies and training."),

            // 3 SRE
            Triple("Site Reliability Engineer", "Stripe", "Keep our payment systems online. You will manage Kubernetes clusters, write automation scripts in Go, and build comprehensive observability dashboards."),
            Triple("Senior SRE", "Datadog", "Manage our internal infrastructure. You will dive deep into Linux kernel performance, write Python automation, and lead incident response for critical outages."),
            Triple("Infrastructure SRE", "MongoDB", "Build resilient database hosting platforms. You will use Terraform to manage AWS resources, tune system performance, and participate in on-call rotations.")
        )
        
        var seededCount = 0
        for ((index, job) in fallbackJobs.withIndex()) {
            val (title, company, coreDescription) = job
            logger.info("Seeding fallback job ${index + 1}/${fallbackJobs.size}: $title at $company")
            
            val fullDescription = expandDescription(coreDescription).take(2000)
            val embedding = embeddingService.generateEmbedding(fullDescription)
            
            val jobDescription = JobDescription(
                title = title,
                company = company,
                description = fullDescription,
                embedding = embedding
            )
            
            jobDescriptionRepository.save(jobDescription)
            seededCount++
        }
        
        logger.info("JobDataSeeder complete. $seededCount hardcoded jobs seeded with embeddings.")
    }
}
