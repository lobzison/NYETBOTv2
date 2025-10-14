FROM ollama/ollama:latest AS builder

COPY ./Modelfile /tmp/Modelfile

RUN nohup bash -c "ollama serve &" && \
    sleep 20 && \
    ollama create NYETBOTv1 -f /tmp/Modelfile && \
    pkill ollama

FROM ollama/ollama:latest

COPY --from=builder /root/.ollama /root/.ollama

EXPOSE 11434

CMD ["ollama", "serve"]
