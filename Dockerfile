FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
RUN ./gradlew --version
COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S webschool && adduser -S webschool -G webschool
WORKDIR /app
COPY --from=build /workspace/build/libs/webschool-0.0.1-SNAPSHOT.jar app.jar
RUN mkdir -p /data/uploads && chown -R webschool:webschool /app /data/uploads
USER webschool
EXPOSE 8888
ENTRYPOINT ["java", "-jar", "app.jar"]
