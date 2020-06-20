package com.ing.hashservice.cli.extension

import akka.actor.typed.ActorRef
import zio.{Runtime, ZIO}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

trait ZioFutureOps[+R] {
  def zioToFuture[A](f: ZIO[R, Throwable, A]): Future[A]
}

object ZioFutureOps {

  def apply[R](runtime: Runtime[R]): ZioFutureOps[R] = new ZioFutureOps[R] {

    override def zioToFuture[A](f: ZIO[R, Throwable, A]): Future[A] =
      runtime.unsafeRunToFuture[Throwable, A](f)
  }

  implicit class ZioOps[R, A](f: ZIO[R, Throwable, A]) {

    def pipeTo[M, RI <: R](actor: ActorRef[M], createMessage: Try[A] => M)(
      implicit ops: ZioFutureOps[RI],
      executionContext: ExecutionContext): Unit = {
      ops.zioToFuture(f).onComplete { result =>
        actor ! createMessage(result)
      }
    }
  }
}
