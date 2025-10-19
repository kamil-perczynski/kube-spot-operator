package pl.kperczynski.kube_spot_operator

import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.config.MeterFilter
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.micrometer.MicrometerMetricsOptions
import io.vertx.micrometer.VertxPrometheusOptions
import io.vertx.micrometer.backends.BackendRegistries
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

private val log = LoggerFactory.getLogger("KubeSpotOperatorApplication")

fun main() {
  val vertx = Vertx.vertx(
    VertxOptions().setMetricsOptions(
      MicrometerMetricsOptions()
        .setJvmMetricsEnabled(true)
        .setEnabled(true)
        .setPrometheusOptions(
          VertxPrometheusOptions()
            .setEnabled(true)
            .setStartEmbeddedServer(false)
        )
    )
  )

  val registry = BackendRegistries.getDefaultNow() as PrometheusMeterRegistry

  registry.config().meterFilter(
    object : MeterFilter {
      override fun configure(id: Meter.Id, config: DistributionStatisticConfig): DistributionStatisticConfig? {
        if (id.type === Meter.Type.TIMER) {
          return DistributionStatisticConfig.builder()
            .percentiles(0.5, 0.95, 0.99)
            .percentilesHistogram(false)
            .build()
            .merge(config)
        }

        return config
      }
    }
  )

  vertx.deployVerticle(MainVerticle())
    .onFailure { exitProcess(-1) }

  Runtime.getRuntime().addShutdownHook(Thread {
    log.info("Shutting down Kube-Spot-Operator application")
    vertx.close()
  })
}
