package nyetbot.repo

import nyetbot.model.MemeId
import nyetbot.model.MemeRow
import nyetbot.model.MemeCreationRequest
import cats.effect.kernel.Ref

import cats.implicits.*
import cats.*

class MemeRepoInMemory[F[_]: Monad](ref: Ref[F, List[MemeRow]]) extends MemeRepo[F]:
    override def getAllMemes: F[List[MemeRow]]               =
        ref.get
    override def addMeme(meme: MemeCreationRequest): F[Unit] =
        for
            currentMemes <- ref.get
            newId         = currentMemes.size + 1
            newMeme       = meme.toPersisted(MemeId(newId))
            _            <- ref.update(memeVault => memeVault :+ newMeme)
        yield ()
    override def deleteMeme(memeId: MemeId): F[Unit]         =
        for
            currentState <- getAllMemes
            newState      = currentState.filterNot(_.id == memeId)
            _            <- ref.set(newState)
        yield ()
