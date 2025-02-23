# Copyright 2022 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

FROM redhat/ubi8-minimal:8.8

LABEL name="harness/delegate" \
      vendor="Harness" \
      maintainer="Harness"

RUN microdnf update --nodocs --setopt=install_weak_deps=0 \
  && microdnf install --nodocs \
    procps \
    hostname \
    lsof \
    findutils \
    tar \
    gzip \
    git \
    shadow-utils \
    glibc-langpack-en \
  && useradd -u 1001 -g 0 harness \
  && microdnf remove shadow-utils \
  && microdnf clean all \
  && rm -rf /var/cache/yum \
  && mkdir -p /opt/harness-delegate/

COPY immutable-scripts /opt/harness-delegate/

WORKDIR /opt/harness-delegate

RUN mkdir -p /opt/harness-delegate/additional_certs_pem_split

COPY --from=eclipse-temurin:17.0.7_7-jre-ubi9-minimal /opt/java/openjdk/ /opt/java/openjdk/

ENV LANG=en_US.UTF-8
ENV HOME=/opt/harness-delegate
ENV CLIENT_TOOLS_DOWNLOAD_DISABLED=true
ENV INSTALL_CLIENT_TOOLS_IN_BACKGROUND=true
ENV JAVA_HOME=/opt/java/openjdk/
ENV PATH="$JAVA_HOME/bin:${PATH}"

ARG TARGETARCH
ARG BASEURL=https://app.harness.io/public/shared/delegates
ARG DELEGATEVERSION

RUN mkdir -m 777 -p client-tools/kubectl/v1.24.3 \
  && curl -s -L -o client-tools/kubectl/v1.24.3/kubectl https://app.harness.io/public/shared/tools/kubectl/release/v1.24.3/bin/linux/$TARGETARCH/kubectl \
  && mkdir -m 777 -p client-tools/helm/v3.12.0 \
  && curl -s -L -o client-tools/helm/v3.12.0/helm https://app.harness.io/public/shared/tools/helm/release/v3.12.0/bin/linux/$TARGETARCH/helm \
  && mkdir -m 777 -p client-tools/go-template/v0.4.5 \
  && curl -s -L -o client-tools/go-template/v0.4.5/go-template https://app.harness.io/public/shared/tools/go-template/release/v0.4.5/bin/linux/$TARGETARCH/go-template \
  && mkdir -m 777 -p client-tools/harness-pywinrm/v0.4-dev \
  && curl -s -L -o client-tools/harness-pywinrm/v0.4-dev/harness-pywinrm https://app.harness.io/public/shared/tools/harness-pywinrm/release/v0.4-dev/bin/linux/$TARGETARCH/harness-pywinrm \
  && mkdir -m 777 -p client-tools/chartmuseum/v0.15.0 \
  && curl -s -L -o client-tools/chartmuseum/v0.15.0/chartmuseum https://app.harness.io/public/shared/tools/chartmuseum/release/v0.15.0/bin/linux/$TARGETARCH/chartmuseum \
  && mkdir -m 777 -p client-tools/tf-config-inspect/v1.2 \
  && curl -s -L -o client-tools/tf-config-inspect/v1.2/terraform-config-inspect https://app.harness.io/public/shared/tools/terraform-config-inspect/v1.2/linux/$TARGETARCH/terraform-config-inspect \
  && mkdir -m 777 -p client-tools/oc/v4.13.4 \
  && curl -s -L -o client-tools/oc/v4.13.4/oc https://app.harness.io/public/shared/tools/oc/release/v4.13.4/bin/linux/$TARGETARCH/oc \
  && mkdir -m 777 -p client-tools/scm/80f7e024 \
  && curl -s -L -o client-tools/scm/80f7e024/scm https://app.harness.io/public/shared/tools/scm/release/80f7e024/bin/linux/$TARGETARCH/scm \
  && chmod -R 775 /opt/harness-delegate \
  && chgrp -R 0 /opt/harness-delegate  \
  && chown -R 1001 /opt/harness-delegate \
  && chown -R 1001 $JAVA_HOME/lib/security/cacerts

ENV PATH=/opt/harness-delegate/client-tools/kubectl/v1.24.3/:$PATH
ENV PATH=/opt/harness-delegate/client-tools/go-template/v0.4.5/:$PATH
ENV PATH=/opt/harness-delegate/client-tools/chartmuseum/v0.15.0/:$PATH
ENV PATH=/opt/harness-delegate/client-tools/tf-config-inspect/v1.2/:$PATH
ENV PATH=/opt/harness-delegate/client-tools/helm/v3.12.0/:$PATH
ENV PATH=/opt/harness-delegate/client-tools/oc/v4.13.4/:$PATH
ENV SHARED_CA_CERTS_PATH=/opt/harness-delegate/additional_certs_pem_split

RUN curl -s -L -o delegate.jar $BASEURL/$DELEGATEVERSION/delegate.jar

USER 1001

CMD [ "./start.sh" ]