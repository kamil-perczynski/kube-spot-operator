FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /opt/app
COPY build/libs/*-layered.jar /opt/app/app.jar

RUN jar xf app.jar

FROM eclipse-temurin:21-jre-alpine

WORKDIR /opt/app

# Postgres certificates for SSL
# COPY .postgresql $HOME/.postgresql

COPY --from=builder /opt/app/org/ ./org/
COPY --from=builder /opt/app/BOOT-INF/layers.idx ./BOOT-INF/layers.idx
COPY --from=builder /opt/app/BOOT-INF/lib/ ./BOOT-INF/lib/
COPY --from=builder /opt/app/BOOT-INF/classpath.idx ./BOOT-INF/classpath.idx

COPY --from=builder /opt/app/META-INF/ ./META-INF/
COPY --from=builder /opt/app/BOOT-INF/classes/ ./BOOT-INF/classes/

ENV JDK_JAVA_OPTIONS="-XX:ActiveProcessorCount=4 -XX:MaxRAMPercentage=75"
ENV JSON_LOG_FORMAT=true

EXPOSE 8080

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
