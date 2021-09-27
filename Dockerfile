FROM openjdk:11.0.12-jdk-slim

ADD target/leantech-proxyserver.jar leantech-proxyserver.jar
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "leantech-proxyserver.jar"]