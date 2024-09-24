# Checklist for releasing WireMock

- [ ] Bump version number
- [ ] Publish the release note
- [ ] Update the version on wiremock.org
- [ ] Announce on the WireMock Community Slack
- [ ] Announce on social

## Pre-release - bump version number
Make sure the version number has been updated. Update the version number and commit and push.

## Publish the release note
Release drafter should have created a draft release note called "next". Check it for sanity and edit it to add any additional information and then set the tag
to the version you've just released and publish it.  

This will trigger the [Release workflow](https://github.com/wiremock/wiremock-grpc-extension/blob/main/.github/workflows/release.yml)
and publish the new release.

## Update the version on wiremock.org
https://github.com/wiremock/wiremock.org

Publish the changes by merging to the `live-publish` branch and manually triggering the "Deploy Jekyll site to Pages" workflow.

## Post an announcement on the WireMock Community Slack
Announce in the #announcments channel then link to the message from #general.

## Shout about it on as many social media platforms as possible
You know the drill.