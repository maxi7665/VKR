package group.nta.lynceus.egts_receiver

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class EgtsListener(
    private val egtsServer: EgtsServer
) {

    private var isListening = false

    private fun listen() {

        try {
            isListening = true
            egtsServer.start()
        }
        finally {
            isListening = false
        }
    }

    private val syncLock = Object()

    private fun startListening() {
        synchronized(syncLock) {

            if (isListening) {
                return
            }

            Thread { listen() }.start()
        }
    }

    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.SECONDS)
    fun checkEgtsListening() {
        startListening()
    }

}