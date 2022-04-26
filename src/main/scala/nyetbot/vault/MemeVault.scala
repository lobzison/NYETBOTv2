package nyetbot.vault

import nyetbot.model.MemeId
import nyetbot.model.MemesPersisted
import nyetbot.model.MemePersisted
import nyetbot.model.MemeCreationRequest

trait MemeVault[F[_]]:
    def getAllMemes: F[MemesPersisted]
    def addMeme(meme: MemeCreationRequest): F[Unit]
    def deleteMeme(memeId: MemeId): F[Unit]
