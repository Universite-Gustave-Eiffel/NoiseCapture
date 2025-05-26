# 1st Docker build stage: build the project with Maven
FROM eclipse-temurin:latest AS builder

# Default to UTF-8 file.encoding
ENV LANG='en_US.UTF-8' LANGUAGE='en_US:en' LC_ALL='en_US.UTF-8'

WORKDIR /onomap
COPY . /onomap/

RUN ./gradlew assemble --no-daemon

FROM eclipse-temurin:latest

LABEL org.opencontainers.image.authors="Nicolas Fortin, UMRAE Univ Eiffel, contact@noise-planet.org"

ENV ONOMAP_GID=8978
ENV ONOMAP_UID=8978
ENV APP_DIR=/onomap
ENV APP_FILE=bin/onomap

RUN <<EOF
groupadd -g $ONOMAP_GID onomap && \
useradd -g $ONOMAP_GID -m -u $ONOMAP_UID -s /bin/bash onomap
EOF

WORKDIR $APP_DIR

COPY --from=builder /onomap/build/distributions $APP_DIR
COPY --from=builder /onomap/deploy/scripts/launch.sh $APP_DIR/launch.sh

RUN <<EOF
mv onomap-*.tar onomap.tar
tar -xvf onomap.tar --strip-components=1
rm onomap.tar
find . -type d -exec chmod 775 {} \;
chmod +x "launch.sh" "$APP_FILE"
EOF

EXPOSE 8888

ENTRYPOINT ["./launch.sh"]
