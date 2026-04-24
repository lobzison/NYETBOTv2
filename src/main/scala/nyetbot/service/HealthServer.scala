package nyetbot.service

import cats.effect.IO
import cats.effect.Resource
import org.http4s.HttpRoutes
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.dsl.io.*
import org.http4s.server.Server

object HealthServer:
    def resource(heartbeat: HeartbeatService, port: Int = 8080): Resource[IO, Server] =
        BlazeServerBuilder[IO]
            .bindHttp(port, "0.0.0.0")
            .withHttpApp(routes(heartbeat).orNotFound)
            .resource

    def routes(heartbeat: HeartbeatService): HttpRoutes[IO] =
        HttpRoutes.of[IO] { case GET -> Root / "health" =>
            heartbeat.isAlive.flatMap {
                case true  => Ok("ok")
                case false => ServiceUnavailable("stale")
            }
        }
