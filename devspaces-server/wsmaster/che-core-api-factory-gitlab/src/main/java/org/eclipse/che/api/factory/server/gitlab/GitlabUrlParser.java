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
package org.eclipse.che.api.factory.server.gitlab;

import static java.lang.String.format;
import static java.net.HttpURLConnection.HTTP_UNAUTHORIZED;
import static java.util.regex.Pattern.compile;
import static org.eclipse.che.commons.lang.StringUtils.trimEnd;

import com.google.common.base.Splitter;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Named;
import org.eclipse.che.api.factory.server.scm.PersonalAccessToken;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.factory.server.scm.exception.ScmCommunicationException;
import org.eclipse.che.api.factory.server.scm.exception.ScmConfigurationPersistenceException;
import org.eclipse.che.api.factory.server.scm.exception.ScmItemNotFoundException;
import org.eclipse.che.api.factory.server.scm.exception.ScmUnauthorizedException;
import org.eclipse.che.api.factory.server.urlfactory.DevfileFilenamesProvider;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.commons.env.EnvironmentContext;

/**
 * Parser of String Gitlab URLs and provide {@link GitlabUrl} objects.
 *
 * @author Max Shaposhnyk
 */
public class GitlabUrlParser {

  private final DevfileFilenamesProvider devfileFilenamesProvider;
  private final PersonalAccessTokenManager personalAccessTokenManager;
  private static final List<String> gitlabUrlPatternTemplates =
      List.of(
          "^(?<host>%s)/(?<subgroups>([^/]++/?)+)/-/tree/(?<branch>[^/]++)(/)?(?<subfolder>[^/]++)?",
          "^(?<host>%s)/(?<subgroups>.*)"); // a wider one, should be the last in the
  // list
  private final List<Pattern> gitlabUrlPatterns = new ArrayList<>();
  private static final String OAUTH_PROVIDER_NAME = "gitlab";

  @Inject
  public GitlabUrlParser(
      @Nullable @Named("che.integration.gitlab.server_endpoints") String gitlabEndpoints,
      DevfileFilenamesProvider devfileFilenamesProvider,
      PersonalAccessTokenManager personalAccessTokenManager) {
    this.devfileFilenamesProvider = devfileFilenamesProvider;
    this.personalAccessTokenManager = personalAccessTokenManager;
    if (gitlabEndpoints != null) {
      for (String gitlabEndpoint : Splitter.on(",").split(gitlabEndpoints)) {
        String trimmedEndpoint = trimEnd(gitlabEndpoint, '/');
        for (String gitlabUrlPatternTemplate : gitlabUrlPatternTemplates) {
          gitlabUrlPatterns.add(compile(format(gitlabUrlPatternTemplate, trimmedEndpoint)));
        }
      }
    } else {
      gitlabUrlPatternTemplates.forEach(
          t -> gitlabUrlPatterns.add(compile(format(t, "https://gitlab.com"))));
    }
  }

  private boolean isUserTokenPresent(String repositoryUrl) {
    Optional<String> serverUrlOptional = getServerUrl(repositoryUrl);
    if (serverUrlOptional.isPresent()) {
      String serverUrl = serverUrlOptional.get();
      try {
        Optional<PersonalAccessToken> token =
            personalAccessTokenManager.get(EnvironmentContext.getCurrent().getSubject(), serverUrl);
        if (token.isPresent()) {
          PersonalAccessToken accessToken = token.get();
          return accessToken.getScmTokenName().equals(OAUTH_PROVIDER_NAME);
        }
      } catch (ScmConfigurationPersistenceException
          | ScmUnauthorizedException
          | ScmCommunicationException exception) {
        return false;
      }
    }
    return false;
  }

  public boolean isValid(@NotNull String url) {
    return gitlabUrlPatterns.stream().anyMatch(pattern -> pattern.matcher(url).matches())
        // If the Gitlab URL is not configured, try to find it in a manually added user namespace
        // token.
        || isUserTokenPresent(url)
        // Try to call an API request to see if the URL matches Gitlab.
        || isApiRequestRelevant(url);
  }

  private boolean isApiRequestRelevant(String repositoryUrl) {
    Optional<String> serverUrlOptional = getServerUrl(repositoryUrl);
    if (serverUrlOptional.isPresent()) {
      GitlabApiClient gitlabApiClient = new GitlabApiClient(serverUrlOptional.get());
      try {
        // If the token request catches the unauthorised error, it means that the provided url
        // belongs to Gitlab.
        gitlabApiClient.getTokenInfo("");
      } catch (ScmCommunicationException e) {
        return e.getStatusCode() == HTTP_UNAUTHORIZED;
      } catch (ScmItemNotFoundException e) {
        return false;
      }
    }
    return false;
  }

  private Optional<Matcher> getPatternMatcherByUrl(String url) {
    Optional<String> serverUrlOptional = getServerUrl(url);
    if (serverUrlOptional.isPresent()) {
      String serverUrl = serverUrlOptional.get();
      return gitlabUrlPatternTemplates.stream()
          .map(t -> compile(format(t, serverUrl)).matcher(url))
          .filter(Matcher::matches)
          .findAny();
    }
    return Optional.empty();
  }

  private Optional<String> getServerUrl(String repositoryUrl) {
    Matcher serverUrlMatcher = compile("[^/|:]/").matcher(repositoryUrl);
    if (serverUrlMatcher.find()) {
      return Optional.of(
          repositoryUrl.substring(0, repositoryUrl.indexOf(serverUrlMatcher.group()) + 1));
    }
    return Optional.empty();
  }

  /**
   * Parses url-s like https://gitlab.apps.cluster-327a.327a.example.opentlc.com/root/proj1.git into
   * {@link GitlabUrl} objects.
   */
  public GitlabUrl parse(String url) {

    Optional<Matcher> matcherOptional =
        gitlabUrlPatterns.stream()
            .map(pattern -> pattern.matcher(url))
            .filter(Matcher::matches)
            .findFirst()
            .or(() -> getPatternMatcherByUrl(url));
    if (matcherOptional.isPresent()) {
      return parse(matcherOptional.get()).withUrl(url);
    } else {
      throw new UnsupportedOperationException(
          "The gitlab integration is not configured properly and cannot be used at this moment."
              + "Please refer to docs to check the Gitlab integration instructions");
    }
  }

  private GitlabUrl parse(Matcher matcher) {
    String host = matcher.group("host");
    String subGroups = trimEnd(matcher.group("subgroups"), '/');
    if (subGroups.endsWith(".git")) {
      subGroups = subGroups.substring(0, subGroups.length() - 4);
    }

    String branch = null;
    String subfolder = null;
    try {
      branch = matcher.group("branch");
    } catch (IllegalArgumentException e) {
      // ok no such group
    }
    try {
      subfolder = matcher.group("subfolder");
    } catch (IllegalArgumentException e) {
      // ok no such group
    }

    return new GitlabUrl()
        .withHostName(host)
        .withSubGroups(subGroups)
        .withBranch(branch)
        .withSubfolder(subfolder)
        .withDevfileFilenames(devfileFilenamesProvider.getConfiguredDevfileFilenames());
  }
}
