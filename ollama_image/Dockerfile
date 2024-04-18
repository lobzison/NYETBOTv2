FROM ollama/ollama as ollama

COPY ./Modelfile ./Modelfile
EXPOSE 11434

RUN ollama serve & sleep 2 && ollama create NYETBOTv1 -f ./Modelfile

