/*
 * Copyright (c) 2018-2023 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */

/* eslint-disable @typescript-eslint/no-unused-vars */

import {
  V1alpha2DevWorkspace,
  V1alpha2DevWorkspaceTemplate,
  V221DevfileComponents,
} from '@devfile/api';
import { api } from '@eclipse-che/common';
import { IncomingHttpHeaders } from 'http';
import {
  DevWorkspaceClient,
  IDevWorkspaceApi,
  IDevWorkspaceTemplateApi,
  IDockerConfigApi,
  IKubeConfigApi,
  INamespaceApi,
  IServerConfigApi,
} from '../../../../devworkspaceClient';
import { getDevWorkspaceClient as helper } from '../getDevWorkspaceClient';

export const stubContainerBuild = {
  disableContainerBuildCapabilities: true,
};
export const stubDashboardWarning = 'Dashboard warning';
export const stubDefaultComponents: V221DevfileComponents[] = [];
export const stubDefaultEditor = undefined;
export const stubDefaultPlugins: api.IWorkspacesDefaultPlugins[] = [];
export const stubOpenVSXURL = 'openvsx-url';
export const stubPvcStrategy = '';
export const stubRunningWorkspacesLimit = 2;
export const stubAllWorkspacesLimit = 1;
export const stubWorkspaceInactivityTimeout = 0;
export const stubWorkspaceRunTimeout = 0;
export const stubWorkspaceStartupTimeout = 0;

export const stubDevWorkspacesList: api.IDevWorkspaceList = {
  apiVersion: 'workspace.devfile.io/v1alpha2',
  kind: 'DevWorkspaceList',
  metadata: {
    resourceVersion: '123456789',
  },
  items: [],
};
export const stubDevWorkspace: V1alpha2DevWorkspace = {
  apiVersion: 'workspace.devfile.io/v1alpha2',
  kind: 'DevWorkspace',
};
export const stubHeaders: IncomingHttpHeaders = {};

export const stubDevWorkspaceTemplatesList = [
  {
    apiVersion: 'workspace.devfile.io/v1alpha2',
    kind: 'DevWorkspaceTemplate',
  },
];
export const stubDevWorkspaceTemplate: V1alpha2DevWorkspaceTemplate = {
  apiVersion: 'workspace.devfile.io/v1alpha2',
  kind: 'DevWorkspaceTemplate',
};

export const stubDockerConfig = {};

export const stubNamespaces = ['user-che'];

export const stubUserProfile: api.IUserProfile = {
  email: 'user1@che',
  username: 'user1',
};

export function getDevWorkspaceClient(_args: Parameters<typeof helper>): ReturnType<typeof helper> {
  return {
    serverConfigApi: {
      fetchCheCustomResource: () => ({}),
      getDashboardWarning: _cheCustomResource => stubDashboardWarning,
      getContainerBuild: _cheCustomResource => stubContainerBuild,
      getDefaultComponents: _cheCustomResource => stubDefaultComponents,
      getDefaultEditor: _cheCustomResource => stubDefaultEditor,
      getDefaultPlugins: _cheCustomResource => stubDefaultPlugins,
      getOpenVSXURL: _cheCustomResource => stubOpenVSXURL,
      getPvcStrategy: _cheCustomResource => stubPvcStrategy,
      getRunningWorkspacesLimit: _cheCustomResource => stubRunningWorkspacesLimit,
      getAllWorkspacesLimit: _cheCustomResource => stubAllWorkspacesLimit,
      getWorkspaceInactivityTimeout: _cheCustomResource => stubWorkspaceInactivityTimeout,
      getWorkspaceRunTimeout: _cheCustomResource => stubWorkspaceRunTimeout,
      getWorkspaceStartTimeout: _cheCustomResource => stubWorkspaceStartupTimeout,
    } as IServerConfigApi,
    devworkspaceApi: {
      create: (_devworkspace, _namespace) =>
        Promise.resolve({ devWorkspace: stubDevWorkspace, headers: stubHeaders }),
      delete: (_namespace, _name) => Promise.resolve(undefined),
      getByName: (_namespace, _name) => Promise.resolve(stubDevWorkspace),
      listInNamespace: _namespace => Promise.resolve(stubDevWorkspacesList),
      patch: (_namespace, _name, _patches) =>
        Promise.resolve({ devWorkspace: stubDevWorkspace, headers: stubHeaders }),
    } as IDevWorkspaceApi,
    dockerConfigApi: {
      read: _namespace => Promise.resolve(stubDockerConfig),
      update: (_namespace, _dockerCfg) => Promise.resolve(stubDockerConfig),
    } as IDockerConfigApi,
    kubeConfigApi: {
      injectKubeConfig: (_namespace, _devworkspaceId) => Promise.resolve(undefined),
    } as IKubeConfigApi,
    namespaceApi: {
      getNamespaces: _token => Promise.resolve(stubNamespaces),
    } as INamespaceApi,
    devWorkspaceTemplateApi: {
      create: _template => Promise.resolve(stubDevWorkspaceTemplate),
      listInNamespace: _namespace => Promise.resolve(stubDevWorkspaceTemplatesList),
      patch: (_namespace, _name, _patches) => Promise.resolve(stubDevWorkspaceTemplate),
    } as IDevWorkspaceTemplateApi,
    userProfileApi: {
      getUserProfile: _namespace => Promise.resolve(stubUserProfile),
    },
  } as DevWorkspaceClient;
}
