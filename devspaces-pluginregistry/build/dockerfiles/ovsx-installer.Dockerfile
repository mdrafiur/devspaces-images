# Copyright (c) 2022-2023 Red Hat, Inc.
# This program and the accompanying materials are made
# available under the terms of the Eclipse Public License 2.0
# which is available at https://www.eclipse.org/legal/epl-2.0/
#
# SPDX-License-Identifier: EPL-2.0
#

# https://registry.access.redhat.com/ubi8/nodejs-16
FROM registry.access.redhat.com/ubi8/nodejs-16:1-90.1679484504 as builder
USER 1001
# TODO: do we need to use a cache folder here? 
ENV npm_config_cache=/tmp/opt/cache
RUN mkdir -p /tmp/opt/cache && \
    npm install --location=global ovsx@0.5.0 --prefix /tmp/opt/ovsx --cache /tmp/opt/cache && chmod -R g+rwX /tmp/opt/ovsx && \
    tar -czf ovsx.tar.gz /tmp/opt/ovsx && \
    chmod g+rwX /opt/app-root/src/ovsx.tar.gz
