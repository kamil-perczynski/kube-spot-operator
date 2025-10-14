package pl.kperczynski.kube_spot_operator.domain

import io.vertx.core.eventbus.ReplyException
import io.vertx.core.eventbus.ReplyFailure

class RecipientException(message: String, cause: Throwable? = null) :
  ReplyException(ReplyFailure.RECIPIENT_FAILURE, 500, message, cause, false)
