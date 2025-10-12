package pl.kperczynski.kube_spot_operator.config

import io.vertx.core.json.JsonObject
import pl.kperczynski.kube_spot_operator.http.HttpServerProps
import pl.kperczynski.kube_spot_operator.kube.KubeClientProps

data class ConfigMap(
  val kubeClient: KubeClientProps,
  val httpServer: HttpServerProps
)

fun readConfigMap(jsonObject: JsonObject): ConfigMap {
  val kubeClient = readKubeClientProps(jsonObject.getJsonObject("kube"))
  val httpServer = readHttpServerProps(jsonObject.getJsonObject("server"))

  return ConfigMap(kubeClient, httpServer)
}

fun readKubeClientProps(json: JsonObject): KubeClientProps {
  return KubeClientProps(
    apiOrigin = json.getString("apiOrigin"),
    caCertPath = json.getString("caCertPath"),
    tokenPath = json.getString("tokenPath"),
    jwksEndpoint = json.getString("jwksEndpoint"),
    sslTrustAll = json.getBoolean("sslTrustAll", false)
  )
}

fun readHttpServerProps(json: JsonObject): HttpServerProps {
  return HttpServerProps(
    port = json.getInteger("port")
  )
}
