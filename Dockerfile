FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build
COPY pom.xml .
COPY openapi.yaml .
COPY .openapi-generator-ignore .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
EXPOSE 8080
COPY --from=builder /build/target/moss-*-jar-with-dependencies.jar /opt/app/app.jar

SHELL ["/bin/bash", "-c"]

CMD if [[ -n "$EXTRA_ROOT_CERT_PATH" ]]; then \
      if [[ -f "$EXTRA_ROOT_CERT_PATH" ]]; then \
        echo "Checking if certificate is already installed..."; \
        if ! keytool -list -keystore "$JAVA_HOME/lib/security/cacerts" \
                     -storepass changeit -alias extra-root-cert > /dev/null 2>&1; then \
          echo "Installing extra root certificate from $EXTRA_ROOT_CERT_PATH"; \
          keytool -import -trustcacerts -keystore "$JAVA_HOME/lib/security/cacerts" \
                  -storepass changeit -noprompt -alias extra-root-cert -file "$EXTRA_ROOT_CERT_PATH"; \
        else \
          echo "Certificate with alias 'extra-root-cert' already installed, skipping."; \
        fi; \
      else \
        echo "WARNING: EXTRA_ROOT_CERT_PATH is set to '$EXTRA_ROOT_CERT_PATH' but file was not found."; \
      fi; \
    fi && \
    java -jar /opt/app/app.jar
