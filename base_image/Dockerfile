FROM eclipse-temurin:17
WORKDIR deps
COPY ./Wizard-Vicuna-7B-Uncensored.ggmlv3.q5_0.bin ./Wizard-Vicuna-7B-Uncensored.ggmlv3.q5_0.bin
RUN apt-get update && apt-get -y install git build-essential
RUN git clone https://github.com/ggerganov/llama.cpp
RUN cd llama.cpp && git checkout 49e7cb5 && make libllama.so && cd ..

