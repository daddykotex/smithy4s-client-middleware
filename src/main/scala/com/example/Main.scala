package com.example

import smithy4s.hello._
import cats.effect._
import cats.implicits._
import cats.effect.syntax.resource._
import org.http4s.ember.server._
import org.http4s._
import com.comcast.ip4s._
import smithy4s.http4s.SimpleRestJsonBuilder
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.client.Client

object HelloWorldImpl extends HelloWorldService[IO] {

  def hello(
      name: String,
      town: Option[String]
  ): IO[HelloOutput] =
    IO.pure { HelloOutput(mkMessage(name, town)) }

  def hello2(
      name: String,
      age: Option[Int],
      town: Option[String]
  ): IO[Hello2Output] =
    IO.pure { Hello2Output(mkMessage(name, town)) }

  def mkMessage(name: String, town: Option[String]): String =
    town.fold(s"Hello " + name + "!")(t =>
      s"Hello " + name + " from " + t + "!"
    )
}

object Routes {
  private val example: Resource[IO, HttpRoutes[IO]] =
    SimpleRestJsonBuilder.routes(HelloWorldImpl).resource

  private val docs: HttpRoutes[IO] =
    smithy4s.http4s.swagger.docs[IO](HelloWorldService)

  val all: Resource[IO, HttpRoutes[IO]] = example.map(_ <+> docs)
}

object Main extends IOApp.Simple {
  // client middleware
  val authMiddleware: org.http4s.client.Middleware[IO] = { client =>
    Client { req =>
      client.run(
        req.withHeaders(
          headers.Authorization(Credentials.Token(AuthScheme.Bearer, "TOKEN"))
        )
      )
    }
  }

  val client = EmberClientBuilder.default[IO].build.map { client =>
    SimpleRestJsonBuilder(HelloWorldService)
      .clientResource(
        authMiddleware(client),
        Uri.unsafeFromString("http://127.0.0.1:9000")
      )
  }

  // slow, ~ 35s on my M1
  def hello2Twice(hs: HelloWorldService[IO]): IO[Unit] = {
    hs.hello2("call 1").flatTap(IO.println) *>
      hs.hello2("call 2").flatTap(IO.println).void
  }

  // will fail
  def helloTwice(hs: HelloWorldService[IO]): IO[Unit] = {
    hs.hello("call 1").flatTap(IO.println) *>
      hs.hello("call 2").flatTap(IO.println).void
  }

  // will fail
  def helloThenHello2(hs: HelloWorldService[IO]): IO[Unit] = {
    hs.hello("call 1").flatTap(IO.println) *>
      hs.hello2("call 2").flatTap(IO.println).void
  }

  // will succeed
  def hello2ThenHello(hs: HelloWorldService[IO]): IO[Unit] = {
    hs.hello2("call 1").flatTap(IO.println) *>
      hs.hello("call 2").flatTap(IO.println).void
  }

  val run = (client, Routes.all).tupled.flatMap { case (client, routes) =>
    for {
      hs <- client
      _ <- EmberServerBuilder
        .default[IO]
        .withPort(port"9000")
        .withHost(host"0.0.0.0")
        .withHttpApp(routes.orNotFound)
        .build
//      _ <- helloTwice(hs).toResource
//      _ <- hello2Twice(hs).toResource
//      _ <- helloThenHello2(hs).toResource
      _ <- hello2ThenHello(hs).toResource
    } yield ()

  }.use_

}
