FROM eclipse-temurin:17
WORKDIR app
COPY . .
RUN ./sbtx pack
ENV JAVA_OPTS="--enable-native-access=ALL-UNNAMED"
CMD ["./target/pack/bin/main"]

