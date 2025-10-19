package pl.kperczynski.kube_spot_operator.domain

import io.vertx.core.Vertx
import io.vertx.core.http.HttpClient
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.net.PemTrustOptions
import io.vertx.micrometer.backends.BackendRegistries
import pl.kperczynski.kube_spot_operator.kube.HttpKubeClient
import pl.kperczynski.kube_spot_operator.kube.KubeClient
import pl.kperczynski.kube_spot_operator.kube.KubeClientProps
import pl.kperczynski.kube_spot_operator.kube.MonitoredKubeClient
import java.net.URI

private val log = org.slf4j.LoggerFactory.getLogger(KubeClientFactory::class.java)

private class KubeClientFactory

fun monitoredKubeClient(vertx: Vertx, props: KubeClientProps): KubeClient {
  return MonitoredKubeClient(
    delegate = HttpKubeClient(
      httpClient = kubeHttpClient(vertx, props),
      vertx = vertx,
      props = props,
    ),
    meterRegistry = BackendRegistries.getDefaultNow()
  )
}

fun kubeHttpClient(vertx: Vertx, kubeClientProps: KubeClientProps): HttpClient {
  val origin = URI.create(kubeClientProps.apiOrigin)

  val schemePort = when (origin.scheme) {
    "https" -> 443
    "http" -> 80
    else -> null
  }

  val port = origin.port.takeIf { it != -1 } ?: schemePort ?: 443

  log.debug(
    "Creating kube openid client for origin={} with port={}, caPath={}",
    origin,
    port,
    kubeClientProps.caCertPath
  )

  val opts = HttpClientOptions()
    .setSsl(origin.scheme == "https")
    .setDefaultHost(origin.host)
    .setDefaultPort(port)
    .setKeepAlive(true)

  if (kubeClientProps.sslTrustAll) {
    log.warn("SSL Trust All is enabled")
    opts.setTrustAll(true)
    opts.setVerifyHost(false)
  } else {
    opts.setTrustOptions(
      PemTrustOptions()
        .addCertPath(kubeClientProps.caCertPath)
    )
  }

  return vertx.createHttpClient(opts)
}
