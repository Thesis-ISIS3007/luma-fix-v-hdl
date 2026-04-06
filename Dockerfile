# syntax=docker/dockerfile:1

FROM ubuntu:24.04

ENV DEBIAN_FRONTEND=noninteractive
ENV LANG=C.UTF-8

RUN apt update && \
apt install -y --no-install-recommends \
ca-certificates \
curl \
wget \
make \
verilator && \
rm -rf /var/lib/apt/lists/*

WORKDIR /workspace