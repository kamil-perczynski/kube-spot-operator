package pl.kperczynski.kube_spot_operator.domain

object ServiceOpIds {
  const val GET_JWKS = "service.op.getJwks"
  const val GET_OPENID_CONFIG = "service.op.getOpenIdConfig"
  const val DRAIN_KUBE_NODE = "service.op.drainKubeNode"
  const val CLEANUP_NODES = "service.op.cleanupNodes"
}

object EventIds {
  const val NODE_TERMINATION_SCHEDULED = "event.nodeTerminationScheduled"
}
