package cqrs.settings

import akka.actor.{ Actor, ExtensionKey, Extension, ExtendedActorSystem }
import scala.concurrent.duration.{ Duration, FiniteDuration }
import java.util.concurrent.TimeUnit

object Settings extends ExtensionKey[Settings]

// extension class implementation for getting the desired configuration properties from the application.conf
class Settings(system: ExtendedActorSystem) extends Extension {
  // get the my-test-app configuration from the
  val config = system.settings.config getConfig ("cqrs")

  val maxOrderPrice: Double = config getDouble "maxOrderPrice"
}

/*
 * Create a trait using a so called self-type annotation that this is an Actor.
 * This allows us to get access to the context and therefore the system to allow
 * us to construct the Settings Extension.
 */
trait SettingsActor { this: Actor ⇒
  val settings = Settings(context.system)
}