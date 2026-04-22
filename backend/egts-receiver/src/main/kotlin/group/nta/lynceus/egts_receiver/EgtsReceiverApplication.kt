package group.nta.lynceus.egts_receiver

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class EgtsReceiverApplication

fun main(args: Array<String>) {
	runApplication<EgtsReceiverApplication>(*args)
}
