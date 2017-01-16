package hr.fer.zemris.sandworm.scheduler.service

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.model.PullResponseItem
import com.github.dockerjava.api.model.WaitResponse
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientBuilder
import hr.fer.zemris.sandworm.packer.RemoteLogger
import org.springframework.stereotype.Service
import java.io.Closeable
import kotlin.concurrent.thread

@Service
class SchedulerService {

    data class ScheduledExecution(
            val registryUrl: String,
            val image: String,
            val loggingEndpoint: String
    )

    private val scheduledExecutions = mutableListOf<ScheduledExecution>()

    private val availableClients = mutableSetOf(
            "tcp://172.17.0.2:2375",
            "tcp://172.17.0.3:2375",
            "tcp://172.17.0.4:2375",
            "tcp://172.17.0.5:2375"
    )

    fun schedule(
            registryUrl: String,
            image: String,
            loggingEndpoint: String
    ) {
        scheduledExecutions.add(ScheduledExecution(
                registryUrl,
                image,
                loggingEndpoint
        ))
        executeNext()
    }

    private fun println(logger: RemoteLogger, tag: String, message: String) {
        logger.log(tag, message)
        println(message)
    }

    private fun execute(scheduledExecution: ScheduledExecution, client: String) {
        println("Executing $scheduledExecution on client $client")

        val logger = RemoteLogger(scheduledExecution.loggingEndpoint)
        val config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(client)
                .withRegistryUrl(scheduledExecution.registryUrl)
                .build()
        val dockerClient = DockerClientBuilder.getInstance(config).build()

        pull(scheduledExecution, client, dockerClient, logger)
    }

    private fun pull(scheduledExecution: ScheduledExecution, client: String, dockerClient: DockerClient, logger: RemoteLogger) {
        println(logger, "scheduler/pull", "Pulling image ${scheduledExecution.image} from ${scheduledExecution.registryUrl}")

        dockerClient
                .pullImageCmd(scheduledExecution.image)
                .withRegistry(scheduledExecution.registryUrl)
                .exec(object : ResultCallback<PullResponseItem> {
                    override fun onError(throwable: Throwable?) {
                        println(logger, "scheduler/pull/error", "Error pulling image ${scheduledExecution.image} from ${scheduledExecution.registryUrl}")
                    }

                    override fun close() {
                        println(logger, "scheduler/pull/close", "Closed ${scheduledExecution.image}")
                    }

                    override fun onComplete() {
                        println(logger, "scheduler/pull/done", "Done pulling ${scheduledExecution.image}")
                        run(scheduledExecution, client, dockerClient, logger)
                    }

                    override fun onStart(closeable: Closeable?) {
                        println(logger, "scheduler/pull/start", "Starting pull of ${scheduledExecution.image}")
                    }

                    override fun onNext(item: PullResponseItem) {
                        println(logger, "scheduler/pull/next", item.stream ?: item.toString())
                    }

                })
    }

    private fun run(scheduledExecution: ScheduledExecution, client: String, dockerClient: DockerClient, logger: RemoteLogger) {

        val container = dockerClient
                .createContainerCmd(scheduledExecution.image)
                .withCmd(scheduledExecution.loggingEndpoint)
                .exec()

        dockerClient.startContainerCmd(container.id).exec()
        dockerClient.waitContainerCmd(container.id).exec(object : ResultCallback<WaitResponse> {
            override fun close() {
                println(logger, "scheduler/run/close", "Closed ${container.id}")
            }

            override fun onNext(response: WaitResponse) {
                println(logger, "scheduler/run/onNext", "${container.id} - ${response.statusCode}")
            }

            override fun onError(throwable: Throwable?) {
                println(logger, "scheduler/run/onError", "Container ${container.id} error - ${throwable?.message ?: throwable?.toString() ?: ""}")
                availableClients.add(client)
                executeNext()
            }

            override fun onComplete() {
                println(logger, "scheduler/run/onComplete", "Container ${container.id} has finished")
                availableClients.add(client)
                executeNext()
            }

            override fun onStart(closeable: Closeable?) {
                println(logger, "scheduler/run/onStart", "Container ${container.id} has started")
            }
        })
    }

    @Synchronized private fun executeNext() {
        println("Starting execution cycle")
        println("${scheduledExecutions.size} executions in queue")
        println("${availableClients.size} clients available")

        if (availableClients.isEmpty()) {
            println("No available clients, skipping")
            return
        }

        if (scheduledExecutions.isEmpty()) {
            println("Nothing to execute, skipping")
            return
        }

        val client = availableClients.first()
        val execution = scheduledExecutions.first()

        availableClients.remove(client)
        scheduledExecutions.remove(execution)

        thread {
            execute(execution, client)
        }

        executeNext()
    }

}
