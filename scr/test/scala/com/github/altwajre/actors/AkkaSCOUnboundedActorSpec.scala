package com.github.altwajre.actors

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory._

class AkkaSCOUnboundedActorSpec extends AkkaUnboundedActorSpec {
  override def config: Config = load(parseString(
    """
      akka {
        log-dead-letters = 0
        log-dead-letters-during-shutdown = off
        actor {
          unstarted-push-timeout = 100s
          benchmark-dispatcher {
            executor = "com.github.altwajre.actors.CustomExecutorServiceConfigurator"
            throughput = 1024
            mailbox-type = "akka.dispatch.SingleConsumerOnlyUnboundedMailbox"
          }
        }
      }
    """))
}

