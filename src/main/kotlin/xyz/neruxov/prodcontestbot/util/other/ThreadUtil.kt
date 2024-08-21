package xyz.neruxov.prodcontestbot.util.other

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * @author <a href="https://github.com/Neruxov">Neruxov</a>
 */
class ThreadUtil {

    companion object {

        private val service: ExecutorService = Executors.newCachedThreadPool()

        fun execute(runnable: Runnable) {
            service.execute(runnable)
        }

    }

}