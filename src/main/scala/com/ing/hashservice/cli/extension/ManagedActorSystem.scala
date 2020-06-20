package com.ing.hashservice.cli.extension

import akka.actor.typed.{ActorSystem, Behavior}
import zio.console._
import zio.{ZIO, ZManaged}

object ManagedActorSystem {

  trait Resource[T] {
    val actorSystem: ActorSystem[T]
  }

  final class Default {

    private def create[T](guardianActorBehavior: Behavior[T]): ZIO[Any, Nothing, Resource[T]] =
      ZIO.succeed(new Resource[T] {
        override val actorSystem: ActorSystem[T] = ActorSystem(guardianActorBehavior, "Hashing-as-a-service")
      })

    private def terminate[T](resource: Resource[T]): ZIO[Console, Nothing, Unit] = {
      putStrLn("Terminating actor system") *>
        ZIO.fromFuture { implicit ec =>
          resource.actorSystem.terminate()
          resource.actorSystem.whenTerminated
        }.unit
          .catchAll(e => putStrLn(s"Fatal init/shutdown error: ${e.getMessage}"))
    }

    def managed[T](guardianActorBehavior: Behavior[T]): ZManaged[Console, Nothing, Resource[T]] =
      ZManaged.make(create(guardianActorBehavior))(terminate)
  }

  def apply[T](guardianActorBehavior: Behavior[T]): ZManaged[Console, Nothing, Resource[T]] =
    new Default().managed[T](guardianActorBehavior)
}
