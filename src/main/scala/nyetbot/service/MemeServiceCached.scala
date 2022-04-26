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
import cats.effect.std.Random

class MemeServiceCached[F[_]: MonadThrow: Random](vault: MemeVault[F], memesF: Ref[F, Memes])
    extends MemeService[F]:

    override def getAllMemes: F[Memes]                                        =
        memesF.get
    override def getMemeResponse(message: String): F[List[SupportedMemeType]] =
        val messageTokens = message.toLowerCase
        for
            memes          <- memesF.get
            memesWithRolls <- memes.memes.traverse(shouldSendMeme(messageTokens))
            triggeredMemes  = memesWithRolls.collect {
                                  case (meme, shouldSend) if shouldSend => meme
                              }
            memesToSend     = triggeredMemes.map(_.body)
        yield memesToSend

    private def shouldSendMeme(message: String)(meme: Meme): F[(Meme, Boolean)] =
        for
            r         <- Random[F].betweenFloat(0f, 1f)
            shouldSend = message.contains(meme.trigger) && (r < 1f / meme.chance.value)
        yield (meme, shouldSend)

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
    def apply[F[_]: Concurrent: Random](vault: MemeVault[F]): F[MemeService[F]] =
        for
            memesPersisted <- vault.getAllMemes
            memesParsed    <- memesPersisted.toMemes
            ref            <- Ref.of[F, Memes](memesParsed)
        yield new MemeServiceCached(vault, ref)
