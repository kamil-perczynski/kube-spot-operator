package pl.kperczynski.kube_spot_operator.logging

import org.slf4j.Logger
import org.slf4j.LoggerFactory

open class Slf4j {

  val log: Logger = LoggerFactory.getLogger(this.javaClass.declaringClass)

}
