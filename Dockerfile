ARG NYETBOT_KEY
ARG DATABASE_URL
FROM openjdk:20
WORKDIR app
COPY . .
RUN ./sbtx assembly
CMD ["java", "-jar", "./target/scala-3.2.1/NYETBOTv2-assembly-0.1.0.jar"]