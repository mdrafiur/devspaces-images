/*
 * Copyright (c) 2012-2023 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.api.factory.server.bitbucket;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.Optional;
import org.eclipse.che.api.auth.shared.dto.OAuthToken;
import org.eclipse.che.api.core.BadRequestException;
import org.eclipse.che.api.core.ConflictException;
import org.eclipse.che.api.core.ForbiddenException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.UnauthorizedException;
import org.eclipse.che.api.factory.server.bitbucket.server.BitbucketPersonalAccessToken;
import org.eclipse.che.api.factory.server.bitbucket.server.BitbucketServerApiClient;
import org.eclipse.che.api.factory.server.bitbucket.server.BitbucketUser;
import org.eclipse.che.api.factory.server.scm.PersonalAccessToken;
import org.eclipse.che.api.factory.server.scm.exception.ScmBadRequestException;
import org.eclipse.che.api.factory.server.scm.exception.ScmCommunicationException;
import org.eclipse.che.api.factory.server.scm.exception.ScmItemNotFoundException;
import org.eclipse.che.api.factory.server.scm.exception.ScmUnauthorizedException;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.commons.subject.SubjectImpl;
import org.eclipse.che.security.oauth.OAuthAPI;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class BitbucketServerPersonalAccessTokenFetcherTest {
  String someNotBitbucketURL = "https://notabitbucket.com";
  String someBitbucketURL = "https://some.bitbucketserver.com";
  Subject subject;
  @Mock BitbucketServerApiClient bitbucketServerApiClient;
  @Mock PersonalAccessToken personalAccessToken;
  @Mock OAuthAPI oAuthAPI;
  BitbucketUser bitbucketUser;
  BitbucketServerPersonalAccessTokenFetcher fetcher;
  BitbucketPersonalAccessToken bitbucketPersonalAccessToken;
  BitbucketPersonalAccessToken bitbucketPersonalAccessToken2;
  BitbucketPersonalAccessToken bitbucketPersonalAccessToken3;

  @BeforeMethod
  public void setup() throws MalformedURLException {
    URL apiEndpoint = new URL("https://che.server.com");
    subject = new SubjectImpl("another_user", "user987", "token111", false);
    bitbucketUser =
        new BitbucketUser("User", "user", 32423523, "NORMAL", true, "user", "user@users.com");
    bitbucketPersonalAccessToken =
        new BitbucketPersonalAccessToken(
            234234,
            234345345,
            23534534,
            90,
            "che-token-<user987>-<che.server.com>",
            "2340590skdf3<0>945i0923i4jasoidfj934ui50",
            bitbucketUser,
            ImmutableSet.of("PROJECT_WRITE", "REPO_WRITE"));
    bitbucketPersonalAccessToken2 =
        new BitbucketPersonalAccessToken(
            3647456,
            234345345,
            23534534,
            90,
            "che-token-<user987>-<che.server.com>",
            "34545<0>945i0923i4jasoidfj934ui50",
            bitbucketUser,
            ImmutableSet.of("REPO_READ"));
    bitbucketPersonalAccessToken3 =
        new BitbucketPersonalAccessToken(
            132423,
            234345345,
            23534534,
            90,
            "che-token-<user987>-<che.server.com>",
            "3456\\<0>945//i0923i4jasoidfj934ui50",
            bitbucketUser,
            ImmutableSet.of("PROJECT_READ", "REPO_READ"));
    fetcher =
        new BitbucketServerPersonalAccessTokenFetcher(
            bitbucketServerApiClient, apiEndpoint, oAuthAPI);
    EnvironmentContext context = new EnvironmentContext();
    context.setSubject(subject);
    EnvironmentContext.setCurrent(context);
  }

  @Test
  public void shouldSkipToFetchUnknownUrls()
      throws ScmUnauthorizedException, ScmCommunicationException {
    // given
    when(bitbucketServerApiClient.isConnected(eq(someNotBitbucketURL))).thenReturn(false);
    // when
    PersonalAccessToken result = fetcher.fetchPersonalAccessToken(subject, someNotBitbucketURL);
    // then
    assertNull(result);
  }

  @Test(
      dataProvider = "expectedExceptions",
      expectedExceptions = {ScmUnauthorizedException.class, ScmCommunicationException.class})
  public void shouldRethrowBasicExceptionsOnGetUserStep(Class<? extends Throwable> exception)
      throws ScmUnauthorizedException, ScmCommunicationException, ScmItemNotFoundException {
    // given
    when(bitbucketServerApiClient.isConnected(eq(someNotBitbucketURL))).thenReturn(true);
    doThrow(exception).when(bitbucketServerApiClient).getUser(eq(subject));
    // when
    fetcher.fetchPersonalAccessToken(subject, someNotBitbucketURL);
  }

  @Test
  public void shouldBeAbleToFetchPersonalAccessToken()
      throws ScmUnauthorizedException, ScmCommunicationException, ScmItemNotFoundException,
          ScmBadRequestException {
    // given
    when(bitbucketServerApiClient.isConnected(eq(someBitbucketURL))).thenReturn(true);
    when(bitbucketServerApiClient.getUser(eq(subject))).thenReturn(bitbucketUser);
    when(bitbucketServerApiClient.getPersonalAccessTokens(eq(bitbucketUser.getSlug())))
        .thenReturn(Collections.emptyList());

    when(bitbucketServerApiClient.createPersonalAccessTokens(
            eq(bitbucketUser.getSlug()),
            eq("che-token-<user987>-<che.server.com>"),
            eq(ImmutableSet.of("PROJECT_WRITE", "REPO_WRITE"))))
        .thenReturn(bitbucketPersonalAccessToken);
    // when
    PersonalAccessToken result = fetcher.fetchPersonalAccessToken(subject, someBitbucketURL);
    // then
    assertNotNull(result);
  }

  @Test
  public void shouldDeleteExistedCheTokenBeforeCreatingNew()
      throws ScmUnauthorizedException, ScmCommunicationException, ScmItemNotFoundException,
          ScmBadRequestException {
    when(bitbucketServerApiClient.isConnected(eq(someBitbucketURL))).thenReturn(true);
    when(bitbucketServerApiClient.getUser(eq(subject))).thenReturn(bitbucketUser);
    when(bitbucketServerApiClient.getPersonalAccessTokens(eq(bitbucketUser.getSlug())))
        .thenReturn(ImmutableList.of(bitbucketPersonalAccessToken, bitbucketPersonalAccessToken2));
    when(bitbucketServerApiClient.createPersonalAccessTokens(
            eq(bitbucketUser.getSlug()),
            eq("che-token-<user987>-<che.server.com>"),
            eq(ImmutableSet.of("PROJECT_WRITE", "REPO_WRITE"))))
        .thenReturn(bitbucketPersonalAccessToken3);
    // when
    PersonalAccessToken result = fetcher.fetchPersonalAccessToken(subject, someBitbucketURL);
    // then
    assertNotNull(result);
    verify(bitbucketServerApiClient)
        .deletePersonalAccessTokens(
            eq(bitbucketUser.getSlug()), eq(bitbucketPersonalAccessToken.getId()));
    verify(bitbucketServerApiClient)
        .deletePersonalAccessTokens(
            eq(bitbucketUser.getSlug()), eq(bitbucketPersonalAccessToken2.getId()));
  }

  @Test(expectedExceptions = {ScmCommunicationException.class})
  public void shouldRethrowUnExceptionsOnCreatePersonalAccessTokens()
      throws ScmUnauthorizedException, ScmCommunicationException, ScmItemNotFoundException,
          ScmBadRequestException {
    // given
    when(bitbucketServerApiClient.isConnected(eq(someBitbucketURL))).thenReturn(true);
    when(bitbucketServerApiClient.getUser(eq(subject))).thenReturn(bitbucketUser);
    when(bitbucketServerApiClient.getPersonalAccessTokens(eq(bitbucketUser.getSlug())))
        .thenReturn(Collections.emptyList());
    doThrow(ScmBadRequestException.class)
        .when(bitbucketServerApiClient)
        .createPersonalAccessTokens(
            eq(bitbucketUser.getSlug()),
            eq("che-token-<user987>-<che.server.com>"),
            eq(ImmutableSet.of("PROJECT_WRITE", "REPO_WRITE")));
    // when

    fetcher.fetchPersonalAccessToken(subject, someBitbucketURL);
  }

  @Test
  public void shouldSkipToValidateTokensWithUnknownUrls()
      throws ScmUnauthorizedException, ScmCommunicationException, ForbiddenException,
          ServerException, ConflictException, UnauthorizedException, NotFoundException,
          BadRequestException {
    // given
    when(oAuthAPI.getToken(eq("bitbucket"))).thenReturn(mock(OAuthToken.class));
    when(personalAccessToken.getScmProviderUrl()).thenReturn(someNotBitbucketURL);
    when(bitbucketServerApiClient.isConnected(eq(someNotBitbucketURL))).thenReturn(false);
    // when
    Optional<Boolean> result = fetcher.isValid(personalAccessToken);
    // then
    assertTrue(result.isEmpty());
  }

  @Test
  public void shouldBeAbleToValidateToken()
      throws ScmUnauthorizedException, ScmCommunicationException, ScmItemNotFoundException {
    // given
    when(personalAccessToken.getScmProviderUrl()).thenReturn(someBitbucketURL);
    when(personalAccessToken.getScmTokenId())
        .thenReturn(Long.toString(bitbucketPersonalAccessToken.getId()));
    when(personalAccessToken.getScmUserName())
        .thenReturn((bitbucketPersonalAccessToken.getUser().getSlug()));
    when(bitbucketServerApiClient.isConnected(eq(someBitbucketURL))).thenReturn(true);
    when(bitbucketServerApiClient.getPersonalAccessToken(
            eq(bitbucketPersonalAccessToken.getUser().getSlug()),
            eq(bitbucketPersonalAccessToken.getId())))
        .thenReturn(bitbucketPersonalAccessToken);
    // when
    Optional<Boolean> result = fetcher.isValid(personalAccessToken);
    // then
    assertFalse(result.isEmpty());
    assertTrue(result.get());
  }

  @DataProvider
  public static Object[][] expectedExceptions() {
    return new Object[][] {{ScmUnauthorizedException.class}, {ScmCommunicationException.class}};
  }

  @DataProvider
  public static Object[][] unExpectedExceptions() {
    return new Object[][] {{ScmBadRequestException.class}, {ScmItemNotFoundException.class}};
  }
}
