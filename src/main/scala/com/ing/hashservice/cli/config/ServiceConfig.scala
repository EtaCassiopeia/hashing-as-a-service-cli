package com.ing.hashservice.cli.config

import pureconfig.ConfigSource
import pureconfig.generic.auto._

final case class ServiceConfig(scheme: String, host: String, port: Int)

object ServiceConfig {
  def load: ServiceConfig = ConfigSource.default.at(namespace = "service").loadOrThrow[ServiceConfig]
}
