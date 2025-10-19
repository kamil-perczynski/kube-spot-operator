package pl.kperczynski.kube_spot_operator.ec2

data class EC2MetadataProps(
  val enabled: Boolean,
  val timerInterval: Long,
  val apiOrigin: String,
  val ttlSeconds: Long
)
