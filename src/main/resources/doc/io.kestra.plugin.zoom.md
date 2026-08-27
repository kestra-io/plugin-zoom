# How to use the Zoom plugin

Send messages to Zoom Team Chat channels or users via the Zoom Chat API.

## Authentication

Create a Server-to-Server OAuth app in the Zoom App Marketplace to get an
`accountId`, `clientId`, and `clientSecret`. Store `clientSecret` in a
[secret](https://kestra.io/docs/concepts/secret).

## Tasks

`SendMessage` sends a message as a step within a flow - set either `channel`
(to post to a channel) or `toContact` (to message a specific user by email),
but not both.