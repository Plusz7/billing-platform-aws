# Build stage trusts any local TLS-inspection root placed in certs/ (e.g. AVG on a dev machine).
# The runtime stage never receives those certificates.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY certs/ /tmp/certs/
RUN for f in /tmp/certs/*.crt; do \
      if [ -e "$f" ]; then keytool -importcert -noprompt -trustcacerts -cacerts -storepass changeit -alias "local-$(basename "$f" .crt)" -file "$f"; fi; \
    done
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
COPY src src
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew && ./gradlew bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 app
COPY --from=build /src/build/libs/*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
