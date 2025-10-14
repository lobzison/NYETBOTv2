FROM ollama/ollama as ollama

COPY ./Modelfile ./Modelfile
EXPOSE 11434

# RUN sysctl -w vm.swappiness=1
RUN ollama serve & sleep 20 && ollama create NYETBOTv1 -f ./Modelfile
