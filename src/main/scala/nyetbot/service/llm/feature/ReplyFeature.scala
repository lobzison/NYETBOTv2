package nyetbot.service.llm.feature

import cats.effect.IO
import nyetbot.client.OllamaClient
import nyetbot.config.LlmConfig
import nyetbot.config.llm.feature.ReplyFeatureConfig
import nyetbot.model.LlmContextMessage
import nyetbot.service.llm.{Register, ReplyContext, TagIntent}

trait ReplyFeature:
    def generateReply(ctx: ReplyContext): IO[String]

object ReplyFeature:
    def apply(
        client: OllamaClient,
        config: ReplyFeatureConfig,
        llmConfig: LlmConfig
    ): ReplyFeature =
        new ReplyFeatureImpl(client, config, llmConfig)

class ReplyFeatureImpl(
    client: OllamaClient,
    config: ReplyFeatureConfig,
    llmConfig: LlmConfig
) extends ReplyFeature:
    private val request = OllamaClient.Req.from(config.modelConfig)

    override def generateReply(ctx: ReplyContext): IO[String] =
        client.generate(request.copy(prompt = ReplyFeaturePrompt.render(ctx, llmConfig)))

object ReplyFeaturePrompt:
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

    def render(ctx: ReplyContext, cfg: LlmConfig): String =
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
