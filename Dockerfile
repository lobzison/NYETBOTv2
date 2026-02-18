FROM eclipse-temurin:17
WORKDIR /app
COPY . .
# Publish canoe fork to local ivy cache so NYETBOTv2 can resolve it
RUN cd canoe && ../sbtx core/publishLocal
RUN ./sbtx pack
ENV JAVA_OPTS="--enable-native-access=ALL-UNNAMED"
CMD ["./target/pack/bin/main"]
