package pl.kperczynski.kube_spot_operator.kube

import com.github.tomakehurst.wiremock.WireMockServer
import io.vertx.core.Future
import io.vertx.core.VerticleBase
import pl.kperczynski.kube_spot_operator.logging.Slf4j

class WiremockVerticle(private val name: String, private val port: Int) : VerticleBase() {

  companion object : Slf4j()

  lateinit var wiremock: WireMockServer

  override fun start(): Future<*> {
    this.wiremock = WireMockServer(port)

    return vertx.executeBlocking {
      log.info("Starting Wiremock server '$name' on port $port")
      this.wiremock.start()
    }
  }

  override fun stop(): Future<*>? {
    return vertx.executeBlocking {
      log.info("Stopping Wiremock server '$name' on port $port")
      this.wiremock.stop()
    }
  }
}
