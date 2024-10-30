package nyetbot.repo

import cats.*
import cats.effect.IO
import cats.effect.kernel.Ref
import cats.implicits.*
import nyetbot.model.MemeCreationRequest
import nyetbot.model.MemeId
import nyetbot.model.MemeRow

class MemeRepoInMemory(ref: Ref[IO, List[MemeRow]]) extends MemeRepo:
    override def getAllMemes: IO[List[MemeRow]]               =
        ref.get
    override def addMeme(meme: MemeCreationRequest): IO[Unit] =
        for
            currentMemes <- ref.get
            newId         = currentMemes.size + 1
            newMeme       = meme.toPersisted(MemeId(newId))
            _            <- ref.update(memeVault => memeVault :+ newMeme)
        yield ()
    override def deleteMeme(memeId: MemeId): IO[Unit]         =
        for
            currentState <- getAllMemes
            newState      = currentState.filterNot(_.id == memeId)
            _            <- ref.set(newState)
        yield ()
