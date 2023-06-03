package me.saket.telephoto.util

import android.os.Process
import com.github.anrwatchdog.ANRWatchDog
import org.junit.rules.ExternalResource

class KillOnAnr : ExternalResource() {
  private val watchDog = ANRWatchDog()

  init {
    println("Starting ANR WatchDog")
    watchDog.setANRListener { error ->
      println("ANR detected! ${error.message}")
      error.printStackTrace()

      println("Killing test process.")
      Process.killProcess(Process.myPid())
    }
    watchDog.start()
  }

  override fun after() {
    println("Interrupting ANR WatchDog")
    watchDog.interrupt()
  }
}
