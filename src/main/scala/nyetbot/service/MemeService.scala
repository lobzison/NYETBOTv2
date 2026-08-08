package nyetbot.service

import cats.Show
import cats.effect.IO
import nyetbot.model.Chance
import nyetbot.model.MemeModels.*

trait MemeService:
    def getAllMemes: IO[List[Meme]]
    def showAllMemes(using Show[Chance]): IO[String]
    def getMemeResponse(message: String): IO[List[SupportedMemeType]]
    def addMeme(memeRequest: MemeCreationRequest): IO[Unit]
    def deleteMeme(id: MemeId): IO[Unit]
