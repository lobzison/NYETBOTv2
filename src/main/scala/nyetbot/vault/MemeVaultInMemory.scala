package nyetbot.vault

import nyetbot.model.MemeId
import nyetbot.model.MemesPersisted
import nyetbot.model.MemePersisted
import nyetbot.model.MemeCreationRequest
import cats.effect.kernel.Ref
import cats.Monad

import cats.implicits.*
import cats.*

class MemeVaultInMemory[F[_]: Monad](ref: Ref[F, MemesPersisted]) extends MemeVault[F]:
    override def getAllMemes: F[MemesPersisted] =
        ref.get
    override def addMeme(meme: MemeCreationRequest): F[Unit] =
        for
            currentMemes <- ref.get
            newId   = currentMemes.memes.size + 1
            newMeme = meme.toPersisted(MemeId(newId))
            _ <- ref.update(memeVault => memeVault.copy(memes = memeVault.memes :+ newMeme))
            _ = println(s"adding meme $meme")
        yield ()
    override def deleteMeme(memeId: MemeId): F[Unit] =
        for
            currentState <- getAllMemes
            newState = currentState.copy(memes = currentState.memes.filterNot(_.id == memeId))
            _ <- ref.set(newState)
        yield ()
