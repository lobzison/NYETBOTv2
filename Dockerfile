FROM lobzison/nyetbot-llm:latest
WORKDIR app
COPY . .
RUN ./sbtx pack
ENV WEIGHTS="/deps/Wizard-Vicuna-7B-Uncensored.ggmlv3.q5_0.bin"
ENV LIBLLAMA="/deps/llama.cpp/libllama.so"
ENV JAVA_OPTS="--add-modules=jdk.incubator.foreign --enable-native-access=ALL-UNNAMED"
CMD ["./target/pack/bin/main"]

