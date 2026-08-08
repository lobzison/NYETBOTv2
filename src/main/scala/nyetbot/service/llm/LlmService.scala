package nyetbot.service.llm

import cats.effect.IO
import io.circe.Json
import io.circe.literal.json
import nyetbot.config.OllamaConfig
import nyetbot.model.LlmContextMessage
import nyetbot.model.UserRef
import nyetbot.service.llm.feature.{ReplyFeature, ReplyFeaturePrompt}
import nyetbot.util.Text
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
        s"""Ниже последние сообщения пользователя ${who.displayName} из чата.
Составь сжатую нейтральную сводку: о чём он пишет, какая позиция, манера, повторяющиеся темы.
Только описание поведения, без ролей, без оценок, без обращений. Не больше ${cfg.summaryMaxChars} символов.

СООБЩЕНИЯ:
${renderChat(recent, cfg)}

СВОДКА:"""

    def topic(recentChat: List[LlmContextMessage], cfg: LlmConfig): String =
        s"""Ниже фрагмент группового чата. Опиши в 2-3 предложениях, о чём сейчас идёт разговор:
тема, что утверждают участники, ключевые детали (цифры, названия, факты). Нейтрально, без оценок.

ЧАТ:
${renderChat(recentChat, cfg)}

СУТЬ:"""

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
        val old = if oldProfile.isEmpty then "пусто" else oldProfile
        s"""Есть старое досье на пользователя ${who.displayName} и свежая сводка его поведения.
Слей их в одно обновлённое досье: сохрани важное из старого, добавь новое, выкинь устаревшее.
Пиши в третьем лице, нейтрально, одним абзацем, строго не больше ${cfg.profileMaxChars} символов.

СТАРОЕ ДОСЬЕ:
$old

СВЕЖАЯ СВОДКА:
$summary

ОБНОВЛЁННОЕ ДОСЬЕ:"""

    def intent(
        question: String,
        replyToText: String,
        recentChat: List[LlmContextMessage],
        cfg: LlmConfig
    ): String =
        val repliedTo = if replyToText.isEmpty then "нет" else replyToText
        s"""Определи, к чему относится обращение к боту.
Сообщение с упоминанием бота: $question
Сообщение, на которое это ответ (может быть пустым): $repliedTo
Недавний контекст чата:
${renderChat(recentChat, cfg)}

Если это продолжение уже идущего обсуждения — ответь одним словом: CONTEXT.
Если это новый отдельный вопрос — ответь одним словом: NEW.
Ответ:"""

class OllamaService(
    client: Client[IO],
    config: OllamaConfig,
    llmConfig: LlmConfig,
    replyFeature: ReplyFeature
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
        complete(
          config.utilityModel,
          OllamaPrompts.summary(recent, who, llmConfig),
          config.summaryNumPredict,
          config.utilityTemperature
        ).map(Text.truncate(_, llmConfig.summaryMaxChars))

    override def summarizeThread(recentChat: List[LlmContextMessage]): IO[String] =
        complete(
          config.utilityModel,
          OllamaPrompts.topic(recentChat, llmConfig),
          config.topicNumPredict,
          config.utilityTemperature
        )

    override def rewriteProfile(
        oldProfile: String,
        recentSummary: String,
        who: UserRef
    ): IO[String] =
        complete(
          config.utilityModel,
          OllamaPrompts.rewrite(oldProfile, recentSummary, who, llmConfig),
          config.rewriteNumPredict,
          config.utilityTemperature
        ).map(Text.truncate(_, llmConfig.profileMaxChars))

    override def classifyTagIntent(
        question: String,
        replyToText: String,
        recentChat: List[LlmContextMessage]
    ): IO[TagIntent] =
        complete(
          config.utilityModel,
          OllamaPrompts.intent(question, replyToText, recentChat, llmConfig),
          config.intentNumPredict,
          config.utilityTemperature
        ).map(r =>
            if r.toUpperCase.contains("NEW") then TagIntent.NewQuestion else TagIntent.Contextual
        )

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
