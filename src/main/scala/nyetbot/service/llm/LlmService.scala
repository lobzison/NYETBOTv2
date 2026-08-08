package nyetbot.service.llm

import cats.effect.IO
import io.circe.Json
import io.circe.literal.json
import nyetbot.config.OllamaConfig
import nyetbot.model.LlmContextMessage
import nyetbot.model.UserRef
import nyetbot.service.llm.feature.{
    ReplyFeature,
    ReplyFeaturePrompt,
    ClassifyIntentFeature,
    ClassifyIntentFeaturePrompt,
    SummarizeUserFeature,
    SummarizeUserFeaturePrompt,
    SummarizeThreadFeature,
    SummarizeThreadFeaturePrompt
}
import org.http4s.Method.POST
import org.http4s.Request
import org.http4s.Uri
import org.http4s.circe.*
import org.http4s.client.Client
import nyetbot.config.LlmConfig

enum TagIntent:
    case Contextual
    case NewQuestion

enum Register:
    case Spor
    case Sobytie
    case Shutka
    case Vopros
    case Byt

final case class ReplyContext(
    target: UserRef,
    profile: String,
    recentSummary: String,
    topic: String,
    recentChat: List[LlmContextMessage],
    intent: TagIntent,
    register: Register,
    minChars: Int,
    triggerText: String,
    currentDate: String,
    replyToText: String,
    replyToBot: Boolean
)

trait LlmService:
    def generateReply(ctx: ReplyContext): IO[String]
    def summarizeUser(recent: List[LlmContextMessage], who: UserRef): IO[String]
    def summarizeThread(recentChat: List[LlmContextMessage]): IO[String]
    def rewriteProfile(oldProfile: String, recentSummary: String, who: UserRef): IO[String]
    def classifyTagIntent(
        question: String,
        replyToText: String,
        recentChat: List[LlmContextMessage]
    ): IO[TagIntent]
    def classifyRegister(
        triggerText: String,
        recentChat: List[LlmContextMessage]
    ): IO[Register]

object OllamaPrompts:

    private def renderChat(chat: List[LlmContextMessage], cfg: LlmConfig): String =
        chat.map(m => s"${m.userName}${cfg.inputPrefix}${m.text}").mkString("\n")

    def reply(ctx: ReplyContext, cfg: LlmConfig): String =
        ReplyFeaturePrompt.render(ctx, cfg)

    def summary(recent: List[LlmContextMessage], who: UserRef, cfg: LlmConfig): String =
        SummarizeUserFeaturePrompt.summary(recent, who, cfg)

    def topic(recentChat: List[LlmContextMessage], cfg: LlmConfig): String =
        SummarizeThreadFeaturePrompt.render(recentChat, cfg)

    def register(
        triggerText: String,
        recentChat: List[LlmContextMessage],
        cfg: LlmConfig
    ): String =
        s"""Определи тип последнего сообщения в чате.

Недавний контекст:
${renderChat(recentChat.takeRight(8), cfg)}

Последнее сообщение: $triggerText

Типы:
SPOR — мнение, тейк, спорное утверждение
SOBYTIE — личная новость, поздравление, событие в жизни
SHUTKA — шутка, мем, стёб
VOPROS — вопрос или прямое обращение
BYT — бытовая болтовня, статус, мелочь

Ответь одним словом: SPOR, SOBYTIE, SHUTKA, VOPROS или BYT.
Ответ:"""

    def rewrite(oldProfile: String, summary: String, who: UserRef, cfg: LlmConfig): String =
        SummarizeUserFeaturePrompt.rewrite(oldProfile, summary, who, cfg)

    def intent(
        question: String,
        replyToText: String,
        recentChat: List[LlmContextMessage],
        cfg: LlmConfig
    ): String =
        ClassifyIntentFeaturePrompt.render(question, replyToText, recentChat, cfg)

class OllamaService(
    client: Client[IO],
    config: OllamaConfig,
    llmConfig: LlmConfig,
    replyFeature: ReplyFeature,
    summarizeThreadFeature: SummarizeThreadFeature,
    classifyIntentFeature: ClassifyIntentFeature,
    summarizeUserFeature: SummarizeUserFeature
) extends LlmService:

    private def complete(
        model: String,
        prompt: String,
        numPredict: Int,
        temperature: Double
    ): IO[String] =
        val body    =
            json"""{ "model": $model, "prompt": $prompt, "stream": false, "think": ${config.reply.think},
                     "options": { "num_predict": $numPredict, "temperature": $temperature,
                                  "num_ctx": ${config.reply.numCtx} } }"""
        val uri     = Uri.unsafeFromString(s"${config.uri}/api/generate")
        val request = Request[IO](method = POST).withUri(uri).withEntity(body)
        client
            .run(request)
            .use { res =>
                res.decodeJson[Json].flatMap { j =>
                    IO.fromEither(j.hcursor.downField("response").as[String])
                }
            }
            .map(_.trim)

    override def generateReply(ctx: ReplyContext): IO[String] =
        replyFeature.generateReply(ctx)

    override def summarizeUser(recent: List[LlmContextMessage], who: UserRef): IO[String] =
        summarizeUserFeature.summarizeUser(recent, who)

    override def summarizeThread(recentChat: List[LlmContextMessage]): IO[String] =
        summarizeThreadFeature.summarizeThread(recentChat)

    override def rewriteProfile(
        oldProfile: String,
        recentSummary: String,
        who: UserRef
    ): IO[String] =
        summarizeUserFeature.rewriteProfile(oldProfile, recentSummary, who)

    override def classifyTagIntent(
        question: String,
        replyToText: String,
        recentChat: List[LlmContextMessage]
    ): IO[TagIntent] =
        classifyIntentFeature.classifyIntent(question, replyToText, recentChat)

    override def classifyRegister(
        triggerText: String,
        recentChat: List[LlmContextMessage]
    ): IO[Register] =
        complete(
          config.utilityModel,
          OllamaPrompts.register(triggerText, recentChat, llmConfig),
          config.registerNumPredict,
          config.utilityTemperature
        ).map(_.toUpperCase).map { response =>
            if response.contains("SPOR") then Register.Spor
            else if response.contains("SOBYTIE") then Register.Sobytie
            else if response.contains("SHUTKA") then Register.Shutka
            else if response.contains("VOPROS") then Register.Vopros
            else Register.Byt
        }
