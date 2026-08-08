package nyetbot.config.llm.feature

import pureconfig.ConfigReader

final case class OllamaModelConfig(
    model: String,
    system: Option[String] = None,
    template: Option[String] = None,
    temperature: Option[Double] = None,
    numPredict: Option[Int] = None,
    numCtx: Option[Int] = None,
    think: Option[Boolean] = None,
    topP: Option[Double] = None,
    topK: Option[Int] = None,
    repeatPenalty: Option[Double] = None,
    stop: Option[List[String]] = None
) derives ConfigReader
