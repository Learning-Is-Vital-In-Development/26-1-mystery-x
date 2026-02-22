FROM alpine:3.21

ARG SQLITE_VERSION=3.48.0-r4

RUN apk add --no-cache sqlite=${SQLITE_VERSION}

WORKDIR /data

CMD ["sh", "-c", "tail -f /dev/null"]
