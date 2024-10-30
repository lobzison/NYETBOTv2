package nyetbot.repo

import cats.effect.IO
import nyetbot.model.MemeCreationRequest
import nyetbot.model.MemeId
import nyetbot.model.MemeRow

trait MemeRepo:
    def getAllMemes: IO[List[MemeRow]]
    def addMeme(meme: MemeCreationRequest): IO[Unit]
    def deleteMeme(memeId: MemeId): IO[Unit]
