package com.example

import smithy4s.hello._
import cats.effect._
import cats.implicits._
import org.http4s.implicits._
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

  val run = (client, Routes.all).tupled.flatMap { case (client, routes) =>
    for {
      hs <- client
      _ <- EmberServerBuilder
        .default[IO]
        .withPort(port"9000")
        .withHost(host"0.0.0.0")
        .withHttpApp(routes.orNotFound)
        .build
      works <- {
        Resource.eval(hs.hello2("Olivier").flatTap(IO.println)) *>
          Resource.eval(hs.hello2("David").flatTap(IO.println)) *>
          Resource.eval(hs.hello2("Jakub").flatTap(IO.println))
      }
      fails <- {
        Resource.eval(hs.hello("Olivier").flatTap(IO.println)) *>
          // BOOM
          Resource.eval(hs.hello("David").flatTap(IO.println))
      }

    } yield ()

  }.use_

}
