package nyetbot.service

import nyetbot.model.{Memes, MemeId}
import nyetbot.model.SupportedMemeType
import cats.effect.kernel.Ref
import nyetbot.model.MemeCreationRequest

trait MemeService[F[_]]:
    def getAllMemes: F[Memes]
    def getMemeResponse(message: String): F[List[SupportedMemeType]]
    def addMeme(memeRequest: MemeCreationRequest): F[Unit]
    def deleteMeme(id: MemeId): F[Unit]
