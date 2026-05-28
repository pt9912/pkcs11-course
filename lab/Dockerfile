FROM debian:12

RUN apt-get update && apt-get install -y \
    bash \
    ca-certificates \
    findutils \
    libengine-pkcs11-openssl \
    make \
    maven \
    openjdk-17-jdk \
    opensc \
    openssl \
    p11-kit \
    softhsm2 \
    tree \
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
