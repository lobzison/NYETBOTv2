FROM openjdk:20
COPY ./target/scala-3.2.1/NYETBOTv2-assembly-0.1.0.jar ./app.jar
WORKDIR .
CMD ["java", "-jar", "app.jar"]