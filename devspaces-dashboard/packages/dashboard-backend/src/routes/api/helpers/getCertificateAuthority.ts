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

import * as fs from 'fs-extra';
import path from 'path';
import * as axios from 'axios';
import https from 'https';

const DEFAULT_CHE_SELF_SIGNED_MOUNT_PATH = '/public-certs';
const CHE_SELF_SIGNED_MOUNT_PATH = process.env.CHE_SELF_SIGNED_MOUNT_PATH;

const certificateAuthority = getCertificateAuthority(
  CHE_SELF_SIGNED_MOUNT_PATH ? CHE_SELF_SIGNED_MOUNT_PATH : DEFAULT_CHE_SELF_SIGNED_MOUNT_PATH,
);

export const axiosInstance = certificateAuthority
  ? axios.default.create({
      httpsAgent: new https.Agent({
        ca: certificateAuthority,
      }),
    })
  : axios.default;

function searchCertificate(
  certPath: string,
  certificateAuthority: Buffer[],
  subdirLevel = 1,
): void {
  const maxSubdirQuantity = 10;
  const maxSubdirLevel = 5;

  const tmpPaths: string[] = [];
  try {
    const publicCertificates = fs.readdirSync(certPath);
    for (const publicCertificate of publicCertificates) {
      const newPath = path.join(certPath, publicCertificate);
      if (fs.lstatSync(newPath).isDirectory()) {
        if (tmpPaths.length < maxSubdirQuantity) {
          tmpPaths.push(newPath);
        }
      } else {
        const fullPath = path.join(certPath, publicCertificate);
        certificateAuthority.push(fs.readFileSync(fullPath));
      }
    }
  } catch (e) {
    // no-op
  }

  if (subdirLevel < maxSubdirLevel) {
    for (const path of tmpPaths) {
      searchCertificate(path, certificateAuthority, ++subdirLevel);
    }
  }
}

function getCertificateAuthority(certPath: string): Buffer[] | undefined {
  if (!fs.existsSync(certPath)) {
    return undefined;
  }

  const certificateAuthority: Buffer[] = [];
  searchCertificate(certPath, certificateAuthority);

  return certificateAuthority.length > 0 ? certificateAuthority : undefined;
}
