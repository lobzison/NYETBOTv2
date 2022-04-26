package nyetbot.service
import nyetbot.service.MemeService
import nyetbot.model.SupportedMemeType
import cats.effect.kernel.Ref
import nyetbot.model.*
import nyetbot.vault.MemeVault
import cats.Monad

import cats.implicits.*
import cats.*
import cats.effect.kernel.Concurrent
import nyetbot.model.MemeCreationRequest

class MemeServiceCached[F[_]: MonadThrow](vault: MemeVault[F], memesF: Ref[F, Memes])
    extends MemeService[F]:

    override def getAllMemes: F[Memes]                                        =
        memesF.get
    override def getMemeResponse(message: String): F[List[SupportedMemeType]] =
        val messageTokens = message.toLowerCase
        for
            memes         <- memesF.get
            triggeredMemes = memes.memes.filter(m => messageTokens.contains(m.trigger))
            memesToSend    = triggeredMemes.map(_.body)
        yield memesToSend

    def addMeme(memeRequest: MemeCreationRequest): F[Unit] =
        for
            _              <- vault.addMeme(memeRequest)
            memesPersisted <- vault.getAllMemes
            memesParsed    <- memesPersisted.toMemes
            _              <- memesF.set(memesParsed)
        yield ()

    def deleteMeme(id: MemeId): F[Unit] =
        for
            _              <- vault.deleteMeme(id)
            memesPersisted <- vault.getAllMemes
            memesParsed    <- memesPersisted.toMemes
            _              <- memesF.set(memesParsed)
        yield ()

object MemeServiceCached:
    def apply[F[_]: Concurrent](vault: MemeVault[F]): F[MemeService[F]] =
        for
            memesPersisted <- vault.getAllMemes
            memesParsed    <- memesPersisted.toMemes
            ref            <- Ref.of[F, Memes](memesParsed)
        yield new MemeServiceCached(vault, ref)
