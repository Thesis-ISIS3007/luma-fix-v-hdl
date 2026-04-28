# syntax=docker/dockerfile:1

FROM fedora:44

ENV LANG=C.UTF-8

RUN dnf install -y --setopt=install_weak_deps=False \
    ca-certificates \
    curl \
    wget \
    make \
    gcc \
    gcc-c++ \
    verilator && \
    dnf clean all && \
    rm -rf /var/cache/dnf

WORKDIR /workspace
