package nyetbot.service

import cats.effect.IO
import io.circe.Json
import io.circe.literal.json
import nyetbot.Config
import nyetbot.model.LlmContextMessage
import nyetbot.model.UserRef
import nyetbot.util.Text
import org.http4s.Method.POST
import org.http4s.Request
import org.http4s.Uri
import org.http4s.circe.*
import org.http4s.client.Client

enum TagIntent:
    case Contextual
    case NewQuestion

final case class ReplyContext(
    target: UserRef,
    profile: String,
    recentSummary: String,
    recentChat: List[LlmContextMessage],
    intent: TagIntent,
    minChars: Int,
    triggerText: String
)

trait LlmService:
    def generateReply(ctx: ReplyContext): IO[String]
    def summarizeUser(recent: List[LlmContextMessage], who: UserRef): IO[String]
    def rewriteProfile(oldProfile: String, recentSummary: String, who: UserRef): IO[String]
    def classifyTagIntent(
        question: String,
        replyToText: String,
        recentChat: List[LlmContextMessage]
    ): IO[TagIntent]

object OllamaPrompts:

    private def renderChat(chat: List[LlmContextMessage], cfg: Config.LlmConfig): String =
        chat.map(m => s"${m.userName}${cfg.inputPrefix}${m.text}").mkString("\n")

    def reply(ctx: ReplyContext, cfg: Config.LlmConfig): String =
        val intentLine = ctx.intent match
            case TagIntent.Contextual  =>
                "Тебя дёрнули внутри уже идущего спора — отвечай в контексте нити."
            case TagIntent.NewQuestion =>
                "Тебя дёрнули с новым, отдельным вопросом — отвечай именно на него, старьё не тащи."
        val dossier    = if ctx.profile.isEmpty then "нет данных, новичок" else ctx.profile
        s"""[ДОСЬЕ НА СОБЕСЕДНИКА]
Кого разносишь: ${ctx.target.displayName}
Его давнее досье (как вёл себя раньше): $dossier
Его свежие замашки (по последним сообщениям): ${ctx.recentSummary}

[КОНТЕКСТ ЧАТА]
${renderChat(ctx.recentChat, cfg)}

[СООБЩЕНИЕ, НА КОТОРОЕ ОТВЕЧАЕШЬ]
${ctx.target.displayName}${cfg.inputPrefix}${ctx.triggerText}

[ЗАДАЧА]
Ответь именно на это сообщение (см. блок выше), а не на что-то другое из контекста. $intentLine
Пиши строго по-русски, плотным заходом не короче ${ctx.minChars} символов.
Ты задёрганный параноик, ветеран двача с забористой шизой: цепляйся за одну самую подозрительную
деталь, разворачивай её в сюрреалистичную гипер-конкретную параноидальную метафору, наезжай и
провоцируй, но держись темы. Не объясняй метафоры, не извиняйся, не ломай образ."""

    def summary(recent: List[LlmContextMessage], who: UserRef, cfg: Config.LlmConfig): String =
        s"""Ниже последние сообщения пользователя ${who.displayName} из чата.
Составь сжатую нейтральную сводку: о чём он пишет, какая позиция, манера, повторяющиеся темы.
Только описание поведения, без ролей, без оценок, без обращений. Не больше ${cfg.summaryMaxChars} символов.

СООБЩЕНИЯ:
${renderChat(recent, cfg)}

СВОДКА:"""

    def rewrite(oldProfile: String, summary: String, who: UserRef, cfg: Config.LlmConfig): String =
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
        cfg: Config.LlmConfig
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
    config: Config.OllamaConfig,
    llmConfig: Config.LlmConfig
) extends LlmService:

    private def complete(
        model: String,
        prompt: String,
        numPredict: Int,
        temperature: Double
    ): IO[String] =
        val body    =
            json"""{ "model": $model, "prompt": $prompt, "stream": false, "think": ${config.think},
                     "options": { "num_predict": $numPredict, "temperature": $temperature,
                                  "num_ctx": ${config.numCtx} } }"""
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
        complete(
          config.replyModel,
          OllamaPrompts.reply(ctx, llmConfig),
          config.replyNumPredict,
          config.replyTemperature
        )

    override def summarizeUser(recent: List[LlmContextMessage], who: UserRef): IO[String] =
        complete(
          config.utilityModel,
          OllamaPrompts.summary(recent, who, llmConfig),
          config.summaryNumPredict,
          config.utilityTemperature
        ).map(Text.truncate(_, llmConfig.summaryMaxChars))

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
