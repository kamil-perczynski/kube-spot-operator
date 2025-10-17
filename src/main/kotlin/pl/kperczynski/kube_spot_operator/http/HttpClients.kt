package pl.kperczynski.kube_spot_operator.http

import io.netty.handler.codec.http.HttpStatusClass
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpClientRequest
import io.vertx.core.http.HttpClientResponse
import org.slf4j.Logger
import pl.kperczynski.kube_spot_operator.kube.KubeClientException

fun preconfigureRequest(log: Logger): Handler<in HttpClientRequest> {
  return Handler {
    log.debug("Calling {} {}", it.method, it.uri)
    it.idleTimeout(5000L)
  }
}

fun handleResponseErrors(res: HttpClientResponse, log: Logger): Future<Buffer> {
  if (HttpStatusClass.valueOf(res.statusCode()) == HttpStatusClass.SUCCESS) {
    return res.body()
  }

  return res.body().compose {
    log.info(
      "Call {} {} returned status={} body={}",
      res.request().method,
      res.request().uri,
      res.statusCode(),
      it.toString(Charsets.UTF_8)
    )

    Future.failedFuture(
      KubeClientException("Call ${res.request().method} ${res.request().uri} returned status=${res.statusCode()}")
    )
  }
}
