package pl.kperczynski.kube_spot_operator.config

import io.vertx.core.json.JsonObject
import pl.kperczynski.kube_spot_operator.ec2.EC2MetadataProps
import pl.kperczynski.kube_spot_operator.http.HttpServerProps
import pl.kperczynski.kube_spot_operator.kube.KubeClientProps

data class ConfigMap(
  val kubeClient: KubeClientProps,
  val httpServer: HttpServerProps,
  val ec2: EC2MetadataProps,
  val kubeNode: KubeNodeProps,
)

fun parseConfigMap(jsonObject: JsonObject): ConfigMap {
  val kubeClient = readKubeClientProps(jsonObject.getJsonObject("kube"))
  val httpServer = readHttpServerProps(jsonObject.getJsonObject("server"))
  val ec2 = readEc2MetadataProps(jsonObject.getJsonObject("ec2"))
  val kubeNode = readKubeNodeProps(jsonObject.getJsonObject("kubeNode"))

  return ConfigMap(
    kubeClient = kubeClient,
    httpServer = httpServer,
    ec2 = ec2,
    kubeNode = kubeNode
  )
}

fun readEc2MetadataProps(json: JsonObject): EC2MetadataProps {
  return EC2MetadataProps(
    timerInterval = json.getLong("timerInterval", 30_000L),
    enabled = json.getBoolean("enabled", true),
    apiOrigin = json.getString("apiOrigin"),
    ttlSeconds = json.getLong("ttlSeconds")
  )
}

fun readKubeClientProps(json: JsonObject): KubeClientProps {
  return KubeClientProps(
    apiOrigin = json.getString("apiOrigin"),
    caCertPath = json.getString("caCertPath"),
    tokenPath = json.getString("tokenPath"),
    jwksEndpoint = json.getString("jwksEndpoint"),
    openIdConfigurationEndpoint = json.getString("openIdConfigurationEndpoint"),
    externalJwksUri = json.getString("externalJwksUri"),
    sslTrustAll = json.getBoolean("sslTrustAll", false)
  )
}

fun readKubeNodeProps(json: JsonObject): KubeNodeProps {
  return KubeNodeProps(
    currentNodeName = resolveEnv(json.getString("currentNodeName")),
    enableAutomaticNodeCleanup = json.getBoolean("enableAutomaticNodeCleanup", true)
  )
}

fun readHttpServerProps(json: JsonObject): HttpServerProps {
  return HttpServerProps(
    port = json.getInteger("port")
  )
}


fun resolveEnv(input: String): String {
  if (!input.startsWith("\$env:")) {
    return input
  }

  val envVar = input.removePrefix("\$env:")
  val value = System.getenv(envVar) ?: throw IllegalArgumentException("Environment variable '$envVar' is not set")
  return value
}
