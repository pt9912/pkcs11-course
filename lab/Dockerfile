FROM debian:13 AS pkcs11-base

RUN apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y \
    bash \
    ca-certificates \
    findutils \
    libengine-pkcs11-openssl \
    locales \
    make \
    opensc \
    openssl \
    p11-kit \
    softhsm2 \
    && sed -i 's/^# *\(en_US.UTF-8 UTF-8\)/\1/' /etc/locale.gen \
    && locale-gen en_US.UTF-8 \
    && rm -rf /var/lib/apt/lists/*

ENV LANG=en_US.UTF-8
ENV LANGUAGE=en_US:en
ENV LC_ALL=en_US.UTF-8

RUN groupadd --gid 1000 course \
    && useradd --uid 1000 --gid 1000 --create-home --shell /bin/bash course \
    && usermod --append --groups softhsm course \
    && mkdir -p /workspace/lab/work/tokens \
    && chmod 0777 /workspace/lab/work /workspace/lab/work/tokens

COPY softhsm2.conf /etc/softhsm/softhsm2.conf

ENV SOFTHSM2_CONF=/etc/softhsm/softhsm2.conf
ENV PKCS11_MODULE=/usr/lib/softhsm/libsofthsm2.so
ENV PKCS11_TOKEN_LABEL=dev-token
ENV PKCS11_USER_PIN=987654
ENV PKCS11_SO_PIN=1234

WORKDIR /workspace
CMD ["bash"]

FROM pkcs11-base AS pkcs11-gradle

ARG GRADLE_VERSION=8.12.1
RUN apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y \
    curl \
    openjdk-21-jdk-headless \
    unzip \
    && curl -fsSL -o /tmp/gradle.zip \
        https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip \
    && unzip -q /tmp/gradle.zip -d /opt \
    && ln -s /opt/gradle-${GRADLE_VERSION}/bin/gradle /usr/local/bin/gradle \
    && rm /tmp/gradle.zip \
    && rm -rf /var/lib/apt/lists/*
ENV GRADLE_USER_HOME=/workspace/.gradle

FROM pkcs11-gradle AS pkcs11-java

FROM pkcs11-gradle AS pkcs11-kotlin

FROM pkcs11-base AS pkcs11-go

ARG GO_VERSION=1.26.3
RUN apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y \
    curl \
    gcc \
    libc6-dev \
    tar \
    && curl -fsSL -o /tmp/go.tgz \
        https://go.dev/dl/go${GO_VERSION}.linux-amd64.tar.gz \
    && tar -C /usr/local -xzf /tmp/go.tgz \
    && ln -sf /usr/local/go/bin/go /usr/local/bin/go \
    && ln -sf /usr/local/go/bin/gofmt /usr/local/bin/gofmt \
    && rm /tmp/go.tgz \
    && rm -rf /var/lib/apt/lists/*
ENV PATH="/usr/local/go/bin:${PATH}"

FROM pkcs11-base AS pkcs11-csharp

RUN apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y \
    libc6-dev \
    curl \
    && curl -fsSL -o /tmp/packages-microsoft-prod.deb \
        https://packages.microsoft.com/config/debian/13/packages-microsoft-prod.deb \
    && dpkg -i /tmp/packages-microsoft-prod.deb \
    && rm /tmp/packages-microsoft-prod.deb \
    && apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y dotnet-sdk-10.0 \
    && ln -sf /usr/lib/x86_64-linux-gnu/libdl.so.2 /usr/lib/x86_64-linux-gnu/libdl.so \
    && rm -rf /var/lib/apt/lists/*
ENV DOTNET_CLI_TELEMETRY_OPTOUT=1
ENV DOTNET_NOLOGO=1
ENV NUGET_PACKAGES=/workspace/.nuget/packages
ENV DOTNET_CLI_HOME=/workspace/.dotnet

FROM pkcs11-csharp AS pkcs11-dev

ARG GRADLE_VERSION=8.12.1
RUN apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y \
    gcc \
    libc6-dev \
    openjdk-21-jdk-headless \
    && rm -rf /var/lib/apt/lists/*

# Gradle und Go aus den vorgelagerten Stages uebernehmen, damit der Layer
# nicht ein zweites Mal heruntergeladen/installiert wird.
COPY --from=pkcs11-gradle /opt/gradle-${GRADLE_VERSION} /opt/gradle-${GRADLE_VERSION}
COPY --from=pkcs11-go /usr/local/go /usr/local/go
RUN ln -s /opt/gradle-${GRADLE_VERSION}/bin/gradle /usr/local/bin/gradle \
    && ln -sf /usr/local/go/bin/go /usr/local/bin/go \
    && ln -sf /usr/local/go/bin/gofmt /usr/local/bin/gofmt

ENV GRADLE_USER_HOME=/workspace/.gradle
ENV PATH="/usr/local/go/bin:${PATH}"
