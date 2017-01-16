package hr.fer.zemris.sandworm.scheduler.controller

import hr.fer.zemris.sandworm.scheduler.service.SchedulerService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class MainController
@Autowired constructor(
        val schedulerService: SchedulerService
) {

    @PostMapping("/")
    fun schedule(
            @RequestParam registryUrl: String,
            @RequestParam image: String,
            @RequestParam loggingEndpoint: String
    ) {
        schedulerService.schedule(
                registryUrl,
                image,
                loggingEndpoint
        )
    }

}
