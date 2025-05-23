# 1st Docker build stage: build the project with Maven
FROM eclipse-temurin:latest as builder


# Default to UTF-8 file.encoding
ENV LANG='en_US.UTF-8' LANGUAGE='en_US:en' LC_ALL='en_US.UTF-8'

RUN set -eux; \
    apt-get update; \
    DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        git \
    ; \
    echo "en_US.UTF-8 UTF-8" >> /etc/locale.gen; \
    locale-gen en_US.UTF-8; \
    rm -rf /var/lib/apt/lists/*

WORKDIR /onomap
COPY . /onomap/

RUN ./gradlew assemble --no-daemon

FROM eclipse-temurin:latest

ENV APP_DIR=/onomap
ENV APP_FILE=bin/onomap

EXPOSE 8888

WORKDIR $APP_DIR

COPY --from=builder /onomap/build/distributions $APP_DIR

RUN mv onomap-*.tar onomap.tar; \
    tar -xvf onomap.tar --strip-components=1; \
    rm onomap.tar


ENTRYPOINT ["sh", "-c"]

CMD ["exec $APP_FILE"]

