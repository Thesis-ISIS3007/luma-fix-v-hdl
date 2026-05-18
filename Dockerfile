# syntax=docker/dockerfile:1

FROM fedora:44

RUN dnf install -y \
    ca-certificates \
    curl \
    wget \
    make \
    gcc \
    gcc-c++ \
    which \
    verilator && \
    dnf clean all && \
    rm -rf /var/cache/dnf

ARG USER=dev
ARG UID=1001
ARG GID=1001

RUN groupadd -g $GID $USER \
    && useradd -m -u $UID -g $GID -s /bin/bash $USER

COPY --from=ghcr.io/astral-sh/uv:latest /uv /uvx /bin/

USER $USER
