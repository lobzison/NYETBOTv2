ARG NYETBOT_KEY
ARG DATABASE_URL
FROM eclipse-temurin:17
WORKDIR app
COPY . .
RUN apt-get update && apt-get -y install git build-essential
RUN ./sbtx pack 
ENV WEIGHTS="./Wizard-Vicuna-7B-Uncensored.ggmlv3.q5_0.bin"
ENV LIBLLAMA="/app/llama.cpp/libllama.so"
ENV JAVA_OPTS="--add-modules=jdk.incubator.foreign --enable-native-access=ALL-UNNAMED"
RUN apt-get update && apt-get -y install git build-essential
RUN git clone https://github.com/ggerganov/llama.cpp
RUN cd llama.cpp && git checkout 49e7cb5 && make libllama.so && cd ..
RUN curl -L https://huggingface.co/TheBloke/Wizard-Vicuna-7B-Uncensored-GGML/resolve/main/Wizard-Vicuna-7B-Uncensored.ggmlv3.q5_0.bin --output Wizard-Vicuna-7B-Uncensored.ggmlv3.q5_0.bin
CMD ["./target/pack/bin/main"]

