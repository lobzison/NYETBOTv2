package nyetbot.service
import cats.*
import cats.effect.IO
import cats.effect.kernel.Ref
import cats.effect.std.Random
import cats.implicits.*
import nyetbot.model.*
import nyetbot.repo.MemeRepo

class MemeServiceCached(vault: MemeRepo, memesF: Ref[IO, List[Meme]])(using Random[IO])
    extends MemeService:

    override def getAllMemes: IO[List[Meme]]                                   =
        memesF.get
    override def getMemeResponse(message: String): IO[List[SupportedMemeType]] =
        val messageTokens = message.toLowerCase
        for
            memes          <- memesF.get
            memesWithRolls <- memes.traverse(shouldSendMeme(messageTokens))
            triggeredMemes  = memesWithRolls.collect {
                                  case (meme, shouldSend) if shouldSend => meme
                              }
            memesToSend     = triggeredMemes.map(_.body)
        yield memesToSend

    private def shouldSendMeme(message: String)(meme: Meme): IO[(Meme, Boolean)] =
        for
            r         <- Random[IO].betweenInt(0, meme.chance.value)
            shouldSend =
                meme.trigger.value.matches(message) && (r == 0)
        yield (meme, shouldSend)

    def addMeme(memeRequest: MemeCreationRequest): IO[Unit] =
        vault.addMeme(memeRequest) >> updateInMemoryRepresentation

    def deleteMeme(id: MemeId): IO[Unit] =
        vault.deleteMeme(id) >> updateInMemoryRepresentation

    val updateInMemoryRepresentation: IO[Unit] =
        for
            memesPersisted <- vault.getAllMemes
            memesParsed    <- memesPersisted.toMemes[IO]
            _              <- memesF.set(memesParsed)
        yield ()

    def showAllMemes(using Show[Chance]): IO[String] =
        def buildDrawer(memes: List[Meme]): IO[TableDrawer] =
            val header = List("id", "trigger", "chance of trigger")
            val body   = memes.map { m =>
                List(
                  m.id.value.toString,
                  m.trigger.toMemeTriggerUserSyntax.value,
                  m.chance.show
                )
            }
            TableDrawer.create[IO](header.length, header :: body)
        for
            memes  <- getAllMemes
            drawer <- buildDrawer(memes)
        yield drawer.buildHtmlCodeTable

object MemeServiceCached:
    def apply(vault: MemeRepo)(using Random[IO]): IO[MemeService] =
        for
            memesPersisted <- vault.getAllMemes
            memesParsed    <- memesPersisted.toMemes[IO]
            ref            <- Ref.of[IO, List[Meme]](memesParsed)
        yield new MemeServiceCached(vault, ref)
