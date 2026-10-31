# syntax=docker/dockerfile:1
# Worker image: Go binary + ffmpeg/ffprobe. Build from the repository root:
#   docker build -t tvc-worker:dev worker
FROM golang:1.24-bookworm AS build
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY cmd ./cmd
COPY internal ./internal
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /out/worker ./cmd/worker

FROM debian:bookworm-slim
RUN apt-get update \
 && apt-get install -y --no-install-recommends ca-certificates ffmpeg \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --system --uid 10001 --create-home worker \
 && mkdir -p /tmp/tvc \
 && chown worker:worker /tmp/tvc
COPY --from=build /out/worker /usr/local/bin/worker

ENV TVC_TMP_DIR=/tmp/tvc
USER worker
ENTRYPOINT ["/usr/local/bin/worker"]
