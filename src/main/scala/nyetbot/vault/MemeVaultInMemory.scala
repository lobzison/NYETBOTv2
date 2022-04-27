package nyetbot.vault

import nyetbot.model.MemeId
import nyetbot.model.MemeRow
import nyetbot.model.MemeCreationRequest
import cats.effect.kernel.Ref
import cats.Monad

import cats.implicits.*
import cats.*

class MemeVaultInMemory[F[_]: Monad](ref: Ref[F, List[MemeRow]]) extends MemeVault[F]:
    override def getAllMemes: F[List[MemeRow]]         =
        ref.get
    override def addMeme(meme: MemeCreationRequest): F[Unit] =
        for
            currentMemes <- ref.get
            newId         = currentMemes.size + 1
            newMeme       = meme.toPersisted(MemeId(newId))
            _            <- ref.update(memeVault => memeVault :+ newMeme)
            _             = println(s"adding meme $meme")
        yield ()
    override def deleteMeme(memeId: MemeId): F[Unit]         =
        for
            currentState <- getAllMemes
            newState      = currentState.filterNot(_.id == memeId)
            _            <- ref.set(newState)
        yield ()
