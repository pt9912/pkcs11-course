FROM debian:12 AS pkcs11-base

RUN apt-get update && apt-get install -y \
    bash \
    ca-certificates \
    findutils \
    libengine-pkcs11-openssl \
    make \
    opensc \
    openssl \
    p11-kit \
    softhsm2 \
    && rm -rf /var/lib/apt/lists/*

RUN mkdir -p /var/lib/softhsm/tokens /workspace/lab/work

COPY softhsm2.conf /etc/softhsm/softhsm2.conf

ENV SOFTHSM2_CONF=/etc/softhsm/softhsm2.conf
ENV PKCS11_MODULE=/usr/lib/softhsm/libsofthsm2.so
ENV PKCS11_TOKEN_LABEL=dev-token
ENV PKCS11_USER_PIN=987654
ENV PKCS11_SO_PIN=1234

WORKDIR /workspace
CMD ["bash"]

FROM pkcs11-base AS pkcs11-java

RUN apt-get update && apt-get install -y \
    maven \
    openjdk-17-jdk-headless \
    && rm -rf /var/lib/apt/lists/*

FROM pkcs11-java AS pkcs11-kotlin

ARG KOTLIN_VERSION=2.1.0
RUN apt-get update && apt-get install -y \
    curl \
    unzip \
    && curl -fsSL -o /tmp/kotlin.zip \
        https://github.com/JetBrains/kotlin/releases/download/v${KOTLIN_VERSION}/kotlin-compiler-${KOTLIN_VERSION}.zip \
    && unzip -q /tmp/kotlin.zip -d /opt \
    && rm /tmp/kotlin.zip \
    && rm -rf /var/lib/apt/lists/*
ENV PATH="/opt/kotlinc/bin:${PATH}"

FROM pkcs11-base AS pkcs11-go

RUN apt-get update && apt-get install -y \
    golang-go \
    && rm -rf /var/lib/apt/lists/*

FROM mcr.microsoft.com/dotnet/sdk:8.0-bookworm-slim AS pkcs11-csharp

RUN apt-get update && apt-get install -y \
    bash \
    ca-certificates \
    findutils \
    libc6-dev \
    libengine-pkcs11-openssl \
    make \
    opensc \
    openssl \
    p11-kit \
    softhsm2 \
    && ln -sf /usr/lib/x86_64-linux-gnu/libdl.so.2 /usr/lib/x86_64-linux-gnu/libdl.so \
    && rm -rf /var/lib/apt/lists/*

RUN mkdir -p /var/lib/softhsm/tokens /workspace/lab/work

COPY softhsm2.conf /etc/softhsm/softhsm2.conf

ENV SOFTHSM2_CONF=/etc/softhsm/softhsm2.conf
ENV PKCS11_MODULE=/usr/lib/softhsm/libsofthsm2.so
ENV PKCS11_TOKEN_LABEL=dev-token
ENV PKCS11_USER_PIN=987654
ENV PKCS11_SO_PIN=1234
ENV DOTNET_CLI_TELEMETRY_OPTOUT=1
ENV DOTNET_NOLOGO=1

WORKDIR /workspace
CMD ["bash"]

FROM pkcs11-csharp AS pkcs11-dev

ARG KOTLIN_VERSION=2.1.0
RUN apt-get update && apt-get install -y \
    curl \
    golang-go \
    maven \
    openjdk-17-jdk-headless \
    unzip \
    && curl -fsSL -o /tmp/kotlin.zip \
        https://github.com/JetBrains/kotlin/releases/download/v${KOTLIN_VERSION}/kotlin-compiler-${KOTLIN_VERSION}.zip \
    && unzip -q /tmp/kotlin.zip -d /opt \
    && rm /tmp/kotlin.zip \
    && rm -rf /var/lib/apt/lists/*
ENV PATH="/opt/kotlinc/bin:${PATH}"
