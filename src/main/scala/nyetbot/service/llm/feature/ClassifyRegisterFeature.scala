package nyetbot.service.llm.feature

import cats.effect.IO
import nyetbot.client.OllamaClient
import nyetbot.config.llm.feature.ClassifyRegisterFeatureConfig
import nyetbot.model.LlmContextMessage

trait ClassifyRegisterFeature:
    def classifyRegister(
        triggerText: String,
        recentChat: List[LlmContextMessage]
    ): IO[ClassifyRegisterFeature.Register]

object ClassifyRegisterFeature:

    enum Register:
        case Spor
        case Sobytie
        case Shutka
        case Vopros
        case Byt

        override def toString: String = this match
            case Spor    =>
                "Это спорный тейк: найди слабое место, переверни его же слова, вызови на ответ."
            case Sobytie =>
                "Это личное событие или поздравление: НЕ доёбывайся до человека. Поучаствуй " +
                    "по-своему — циничное поздравление, мрачный тост или едкий прогноз на будущее."
            case Shutka  =>
                "Это шутка или мем: подхвати и докрути шутку в своём стиле, не разваливай её наездом."
            case Vopros  =>
                "Это вопрос или обращение: ответь по существу, со своим мнением и лёгким подколом."
            case Byt     =>
                "Это бытовая болтовня: вбрось свой тейк по теме как участник, без наезда на человека."

    def apply(
        client: OllamaClient,
        config: ClassifyRegisterFeatureConfig
    ): ClassifyRegisterFeature =
        new ClassifyRegisterFeature:
            private val request = OllamaClient.Req.from(config.modelConfig)

            override def classifyRegister(
                triggerText: String,
                recentChat: List[LlmContextMessage]
            ): IO[Register] =
                client
                    .generate(
                      request.copy(
                        prompt = Prompt.render(
                          triggerText,
                          recentChat
                        )
                      )
                    )
                    .map(_.toUpperCase)
                    .map { response =>
                        if response.contains("SPOR") then Register.Spor
                        else if response.contains("SOBYTIE") then Register.Sobytie
                        else if response.contains("SHUTKA") then Register.Shutka
                        else if response.contains("VOPROS") then Register.Vopros
                        else Register.Byt
                    }

    object Prompt:
        def render(
            triggerText: String,
            recentChat: List[LlmContextMessage]
        ): String =
            s"""Определи тип последнего сообщения в чате.

Недавний контекст:
${ChatLog.render(recentChat.takeRight(8))}

Последнее сообщение: $triggerText

Типы:
SPOR — мнение, тейк, спорное утверждение
SOBYTIE — личная новость, поздравление, событие в жизни
SHUTKA — шутка, мем, стёб
VOPROS — вопрос или прямое обращение
BYT — бытовая болтовня, статус, мелочь

Ответь одним словом: SPOR, SOBYTIE, SHUTKA, VOPROS или BYT.
Ответ:"""
