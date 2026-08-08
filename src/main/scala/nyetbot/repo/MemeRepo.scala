package nyetbot.repo

import cats.effect.IO
import nyetbot.model.MemeModels.*

trait MemeRepo:
    def getAllMemes: IO[List[MemeRow]]
    def addMeme(meme: MemeCreationRequest): IO[Unit]
    def deleteMeme(memeId: MemeId): IO[Unit]
