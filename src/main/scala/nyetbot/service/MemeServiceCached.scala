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

class MemeServiceCached[F[_]: MonadThrow: Random](vault: MemeVault[F], memesF: Ref[F, List[Meme]])
    extends MemeService[F]:

    override def getAllMemes: F[List[Meme]]                                   =
        memesF.get
    override def getMemeResponse(message: String): F[List[SupportedMemeType]] =
        val messageTokens = message.toLowerCase
        for
            memes          <- memesF.get
            memesWithRolls <- memes.traverse(shouldSendMeme(messageTokens))
            triggeredMemes  = memesWithRolls.collect {
                                  case (meme, shouldSend) if shouldSend => meme
                              }
            memesToSend     = triggeredMemes.map(_.body)
        yield memesToSend

    private def shouldSendMeme(message: String)(meme: Meme): F[(Meme, Boolean)] =
        for
            r         <- Random[F].betweenInt(0, meme.chance.value)
            shouldSend =
                meme.trigger.value.matches(message) && (r == 0)
        yield (meme, shouldSend)

    def addMeme(memeRequest: MemeCreationRequest): F[Unit] =
        vault.addMeme(memeRequest) >> updateInMemoryRepresentation

    def deleteMeme(id: MemeId): F[Unit] =
        vault.deleteMeme(id) >> updateInMemoryRepresentation

    val updateInMemoryRepresentation: F[Unit] =
        for
            memesPersisted <- vault.getAllMemes
            memesParsed    <- memesPersisted.toMemes
            _              <- memesF.set(memesParsed)
        yield ()

    def showAllMemes(using Show[Chance]): F[String] =
        def buildDrawer(memes: List[Meme]): F[TableDrawer] =
            val header = List("id", "trigger", "chance of trigger")
            val body   = memes.map { m =>
                List(
                  m.id.value.toString,
                  m.trigger.toMemeTriggerUserSyntax.value,
                  m.chance.show
                )
            }
            TableDrawer.create[F](header.length, header :: body)
        for
            memes  <- getAllMemes
            drawer <- buildDrawer(memes)
        yield drawer.buildHtmlCodeTable

object MemeServiceCached:
    def apply[F[_]: Concurrent: Random](vault: MemeVault[F]): F[MemeService[F]] =
        for
            memesPersisted <- vault.getAllMemes
            memesParsed    <- memesPersisted.toMemes
            ref            <- Ref.of[F, List[Meme]](memesParsed)
        yield new MemeServiceCached(vault, ref)
