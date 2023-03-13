package me.saket.telephoto.test

import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

class Retry(
  private val canRetry: (error: Throwable, runCount: Int) -> Boolean,
) : TestRule {

  override fun apply(base: Statement, description: Description): Statement {
    return object : Statement() {
      override fun evaluate() {
        repeat(Int.MAX_VALUE) { runCount ->
          try {
            base.evaluate()
            return
          } catch (e: Throwable) {
            if (!canRetry(e, runCount + 1)) {
              System.err.println("${description.displayName} failed after ${runCount + 1} retries")
              throw e
            }
          }
        }
      }
    }
  }
}
