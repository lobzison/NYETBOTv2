package nyetbot.service.llm

import cats.effect.IO
import cats.effect.Ref
import cats.syntax.all.*
import nyetbot.config.LlmFunctionalityConfig
import nyetbot.model.LlmContextMessage
import nyetbot.model.ProfileModels.*

trait ChatMemory:
    def ingest(m: LlmContextMessage): IO[Unit]
    def replyContext: IO[List[LlmContextMessage]]
    def recentUser(id: UserId): IO[List[LlmContextMessage]]

object ChatMemory:
    def apply(config: LlmFunctionalityConfig): IO[ChatMemory] =
        for
            chat    <- Ref.of[IO, Vector[LlmContextMessage]](Vector.empty)
            perUser <- Ref.of[IO, Map[UserId, Vector[LlmContextMessage]]](Map.empty)
        yield new ChatMemory:
            override def ingest(m: LlmContextMessage): IO[Unit] =
                for
                    _ <- chat.update(buf => (buf :+ m).takeRight(config.chatBufferSize))
                    _ <- m.userId.traverse_ { id =>
                             perUser.update { map =>
                                 val buf = (map.getOrElse(id, Vector.empty) :+ m)
                                     .takeRight(config.recentUserMessages)
                                 map.updated(id, buf)
                             }
                         }
                yield ()

            override def replyContext: IO[List[LlmContextMessage]] =
                chat.get.map(_.takeRight(config.replyContextWindow).toList)

            override def recentUser(id: UserId): IO[List[LlmContextMessage]] =
                perUser.get.map(_.getOrElse(id, Vector.empty).toList)
