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
  def hello(name: String, town: Option[String]): IO[Greeting] = IO.pure {
    town match {
      case None    => Greeting(s"Hello " + name + "!")
      case Some(t) => Greeting(s"Hello " + name + " from " + t + "!")
    }
  }
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
        req.putHeaders(
          headers.Authorization(Credentials.Token(AuthScheme.Bearer, "TOKEN"))
        )
      )
    }
  }

  val client = EmberClientBuilder.default[IO].build.map { client =>
    SimpleRestJsonBuilder(HelloWorldService)
      .clientResource(
        authMiddleware(client),
        Uri.unsafeFromString("http://some-api.com")
      )
  }
  val run = (client, Routes.all).tupled.flatMap { case (client, routes) =>
    // here you have a HelloWorldClient, but headers will be added
    EmberServerBuilder
      .default[IO]
      .withPort(port"9000")
      .withHost(host"localhost")
      .withHttpApp(routes.orNotFound)
      .build
  }.useForever

}
