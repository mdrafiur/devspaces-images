# Copyright (c) 2023 Red Hat, Inc.
# This program and the accompanying materials are made
# available under the terms of the Eclipse Public License 2.0
# which is available at https://www.eclipse.org/legal/epl-2.0/
#
# SPDX-License-Identifier: EPL-2.0
#
# Contributors:
#   Red Hat, Inc. - initial API and implementation
#
# https://registry.access.redhat.com/rhel8/go-toolset
FROM rhel8/go-toolset:1.18.9-13 as builder
ENV GOPATH=/go/ \
    GO111MODULE=on

ARG BOOTSTRAP=false
ENV BOOTSTRAP=${BOOTSTRAP}

USER root

COPY $REMOTE_SOURCES $REMOTE_SOURCES_DIR
RUN source $REMOTE_SOURCES_DIR/devspaces-images-imagepuller/cachito.env
WORKDIR $REMOTE_SOURCES_DIR/devspaces-images-imagepuller/app/devspaces-imagepuller

RUN adduser appuser && \
    make build 

# https://registry.access.redhat.com/ubi8-minimal
FROM ubi8-minimal:8.7-1085
USER root
RUN microdnf -y update && microdnf clean all && rm -rf /var/cache/yum && echo "Installed Packages" && rpm -qa | sort -V && echo "End Of Installed Packages"

# CRW-528 copy actual cert
COPY --from=builder /etc/pki/ca-trust/extracted/pem/tls-ca-bundle.pem /etc/pki/ca-trust/extracted/pem/
RUN ls /etc/pki/ca-trust/extracted/pem/

# CRW-528 copy symlink to the above cert
COPY --from=builder /etc/pki/tls/certs/ca-bundle.crt /etc/pki/tls/certs/
COPY --from=builder /etc/passwd /etc/passwd
RUN ls /etc/pki/tls/certs
RUN ls /etc/passwd

USER appuser
COPY --from=builder $REMOTE_SOURCES_DIR/devspaces-images-imagepuller/app/devspaces-imagepuller/bin/kubernetes-image-puller /
COPY --from=builder $REMOTE_SOURCES_DIR/devspaces-images-imagepuller/app/devspaces-imagepuller/bin/sleep /bin/sleep
RUN ls /
RUN ls /bin | grep sleep

# NOTE: To use this container, need a configmap. See example at ./deploy/openshift/configmap.yaml
# See also https://github.com/che-incubator/kubernetes-image-puller#configuration
CMD ["/kubernetes-image-puller"]

# append Brew metadata here

ENV SUMMARY="Red Hat OpenShift Dev Spaces imagepuller container" \
    DESCRIPTION="Red Hat OpenShift Dev Spaces imagepuller container" \
    PRODNAME="devspaces" \
    COMPNAME="imagepuller-rhel8"
LABEL summary="$SUMMARY" \
      description="$DESCRIPTION" \
      io.k8s.description="$DESCRIPTION" \
      io.k8s.display-name="$DESCRIPTION" \
      io.openshift.tags="$PRODNAME,$COMPNAME" \
      com.redhat.component="$PRODNAME-$COMPNAME-container" \
      name="$PRODNAME/$COMPNAME" \
      version="3.6" \
      license="EPLv2" \
      maintainer="Ilya Buziuk <ibuziuk@redhat.com>, Nick Boldt <nboldt@redhat.com>" \
      io.openshift.expose-services="" \
      usage=""
