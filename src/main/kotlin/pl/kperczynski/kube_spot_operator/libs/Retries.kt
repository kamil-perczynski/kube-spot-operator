package pl.kperczynski.kube_spot_operator.libs

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import org.slf4j.Logger


fun <T> retryDecorator(
  opName: String,
  vertx: Vertx,
  log: Logger,
  times: Array<Long> = arrayOf(3000L, 5000L, 10000L),
): (() -> Future<T>) -> Future<T> {
  return { fn ->
    var res = fn()

    for ((index, lng) in times.withIndex()) {
      res = res.recover { err ->
        log.info("$opName fail #${index + 1}: ${err.message}, ${lng / 1000}s to retry...")
        setTimeout(vertx, lng).compose { fn() }
      }
    }

    res
  }
}

fun setTimeout(vertx: Vertx, delayMs: Long): Future<Void> {
  val promise = Promise.promise<Void>()
  vertx.setTimer(delayMs) { promise.complete() }
  return promise.future()
}
