FROM amazoncorretto:25-alpine-jdk AS build
RUN apk add --no-cache nodejs npm
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY frontend frontend
COPY src src
RUN cd frontend && npm ci && npm run format:check && npm run lint \
    && npm test && npm run build
RUN ./mvnw -B -DskipTests -Dskip.installnodenpm -Dskip.npm package

FROM amazoncorretto:25-alpine
RUN apk add --no-cache curl
WORKDIR /app
COPY --from=build /workspace/target/configuration-rule-engine-0.1.0.jar app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
