package nyetbot.service

import nyetbot.model.{Meme, MemeId, Chance}
import nyetbot.model.SupportedMemeType
import nyetbot.model.MemeCreationRequest
import cats.Show

trait MemeService[F[_]]:
    def getAllMemes: F[List[Meme]]
    def showAllMemes(using Show[Chance]): F[String]
    def getMemeResponse(message: String): F[List[SupportedMemeType]]
    def addMeme(memeRequest: MemeCreationRequest): F[Unit]
    def deleteMeme(id: MemeId): F[Unit]
