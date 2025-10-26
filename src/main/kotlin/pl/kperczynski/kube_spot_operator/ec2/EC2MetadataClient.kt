package pl.kperczynski.kube_spot_operator.ec2

import io.vertx.core.Future

interface EC2MetadataClient {

  fun fetchInstanceAction(): Future<InstanceAction>

  fun fetchAsgTargetLifecycleState(): Future<AsgLifecycleState>
}

