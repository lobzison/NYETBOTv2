package nyetbot.vault

import nyetbot.model.MemeId
import nyetbot.model.MemeRow
import nyetbot.model.MemeCreationRequest

trait MemeVault[F[_]]:
    def getAllMemes: F[List[MemeRow]]
    def addMeme(meme: MemeCreationRequest): F[Unit]
    def deleteMeme(memeId: MemeId): F[Unit]
