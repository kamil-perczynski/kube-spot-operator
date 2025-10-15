package pl.kperczynski.kube_spot_operator.libs

import io.vertx.core.buffer.Buffer
import io.vertx.core.eventbus.MessageCodec

class DirectMessageCodec : MessageCodec<Object, Object> {

  companion object {
    const val DIRECT_CODEC_NAME = "Direct"
  }

  override fun encodeToWire(buffer: Buffer?, s: Object?) {
    throw UnsupportedOperationException("Not implemented")
  }

  override fun decodeFromWire(pos: Int, buffer: Buffer?): Object {
    throw UnsupportedOperationException("Not implemented")
  }

  override fun transform(s: Object?): Object {
    return s as Object
  }

  override fun name(): String {
    return DIRECT_CODEC_NAME
  }

  override fun systemCodecID(): Byte {
    return -1
  }
}
