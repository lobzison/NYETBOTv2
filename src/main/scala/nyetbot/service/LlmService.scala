package nyetbot.service

import cats.effect.IO
import io.circe.Json
import io.circe.literal.json
import nyetbot.config.OllamaConfig
import nyetbot.model.LlmContextMessage
import nyetbot.model.UserRef
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

    private def registerLine(register: Register): String = register match
        case Register.Spor    =>
            "Это спорный тейк: найди слабое место, переверни его же слова, вызови на ответ."
        case Register.Sobytie =>
            "Это личное событие или поздравление: НЕ доёбывайся до человека. Поучаствуй " +
                "по-своему — циничное поздравление, мрачный тост или едкий прогноз на будущее."
        case Register.Shutka  =>
            "Это шутка или мем: подхвати и докрути шутку в своём стиле, не разваливай её наездом."
        case Register.Vopros  =>
            "Это вопрос или обращение: ответь по существу, со своим мнением и лёгким подколом."
        case Register.Byt     =>
            "Это бытовая болтовня: вбрось свой тейк по теме как участник, без наезда на человека."

    def reply(ctx: ReplyContext, cfg: LlmConfig): String =
        val intentLine          = ctx.intent match
            case TagIntent.Contextual  =>
                "Тебя дёрнули внутри уже идущего спора — отвечай в контексте нити."
            case TagIntent.NewQuestion =>
                "Тебя дёрнули с новым, отдельным вопросом — отвечай именно на него, старьё не тащи."
        val dossier             = if ctx.profile.isEmpty then "нет данных, новичок" else ctx.profile
        val topicBlock          =
            if ctx.topic.isEmpty then ""
            else s"""
[СУТЬ ОБСУЖДЕНИЯ]
${ctx.topic}
"""
        val replyToBlock        =
            if ctx.replyToText.isEmpty then ""
            else
                val marker =
                    if ctx.replyToBot then
                        "(это ТВОЁ прошлое сообщение — собеседник отвечает тебе)\n"
                    else ""
                s"""
[НА ЧТО ОН ОТВЕЧАЕТ]
$marker${ctx.replyToText}
"""
        val replyToBotDirective =
            if ctx.replyToBot then
                """
Собеседник ответил на твоё сообщение: отстаивай или докручивай свою позицию, отвечай именно на его возражение и не повторяй уже сказанное тобой."""
            else ""
        s"""[ДОСЬЕ НА СОБЕСЕДНИКА]
Кого разносишь: ${ctx.target.displayName}
Его давнее досье (как вёл себя раньше): $dossier
Его свежие замашки (по последним сообщениям): ${ctx.recentSummary}
$topicBlock
[КОНТЕКСТ ЧАТА]
${renderChat(ctx.recentChat, cfg)}
$replyToBlock
[СООБЩЕНИЕ, НА КОТОРОЕ ОТВЕЧАЕШЬ]
${ctx.target.displayName}${cfg.inputPrefix}${ctx.triggerText}

[ЗАДАЧА]
Ты давний участник этого чата и отвечаешь на последнее сообщение в нити.
$intentLine
${registerLine(ctx.register)}
Главное — выскажись по СУТИ обсуждаемой темы (см. [СУТЬ ОБСУЖДЕНИЯ] и [КОНТЕКСТ ЧАТА]):
дай свой конкретный тейк, наблюдение или мрачную теорию про сам предмет разговора. Цепляйся за
конкретные детали треда — цифры, названия, факты из сообщений. Подкол собеседника — приправа,
а не основа ответа.
Целься примерно в ${ctx.minChars} символов. Пиши строго по-русски.
Сейчас ${ctx.currentDate}.
Опирайся только на то, что реально написано в чате и досье — не выдумывай фактов и слов.
Никогда не упоминай слова «досье», «сводка», «контекст» и сам факт, что тебе дали информацию, —
ты просто помнишь этого человека и чат сам.
Держи один грамматический род собеседника в пределах ответа.
Не извиняйся, не ломай образ, не будь полезным ассистентом.$replyToBotDirective"""

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
    ollamaDomain: String
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
        val uri     = Uri.unsafeFromString(s"${config.uri(ollamaDomain)}/api/generate")
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
