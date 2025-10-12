package pl.kperczynski.kube_spot_operator.kube

data class KubeClientProps(
  val apiOrigin: String,
  val caCertPath: String,
  val tokenPath: String,
  val jwksEndpoint: String,
  val openIdConfigurationEndpoint: String,
  val externalJwksUri: String,
  val sslTrustAll: Boolean
)
