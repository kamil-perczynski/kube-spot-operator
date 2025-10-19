package pl.kperczynski.kube_spot_operator.ec2

import io.vertx.core.Vertx
import io.vertx.core.http.HttpClient
import io.vertx.core.http.HttpClientOptions
import io.vertx.micrometer.backends.BackendRegistries
import java.net.URI

fun ec2MetadataHttpClient(vertx: Vertx, ec2MetadataProps: EC2MetadataProps): HttpClient {
  val origin = URI.create(ec2MetadataProps.apiOrigin)

  val schemePort = when (origin.scheme) {
    "https" -> 443
    "http" -> 80
    else -> null
  }

  val port = origin.port.takeIf { it != -1 } ?: schemePort ?: 443

  val opts = HttpClientOptions()
    .setSsl(origin.scheme == "https")
    .setDefaultHost(origin.host)
    .setDefaultPort(port)
    .setKeepAlive(true)

  return vertx.createHttpClient(opts)
}

fun monitoredEC2MetadataClient(vertx: Vertx, props: EC2MetadataProps): EC2MetadataClient {
  return MonitoredEC2MetadataClient(
    delegate = HttpEC2MetadataClient(
      httpClient = ec2MetadataHttpClient(vertx, props),
      ec2MetadataProps = props
    ),
    meterRegistry = BackendRegistries.getDefaultNow()
  )
}
