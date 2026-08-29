# Kestra Zoom Plugin

## What

- Provides plugin components under `io.kestra.plugin.zoom`.
- Includes classes such as `SendMessage`, `AbstractZoomConnection`.

## Why

- What user problem does this solve? Teams need to send notifications to Zoom channels or directly to individual users from orchestrated workflows instead of relying on manual messages, ad hoc scripts, or disconnected tools.
- Why would a team adopt this plugin in a workflow? It keeps Zoom messages in the same Kestra flow as upstream processing, approvals, retries, and downstream systems.
- What operational/business outcome does it enable? It reduces manual handoffs and fragmented tooling while improving reliability, traceability, and delivery speed for processes that depend on Zoom notifications.

## How

### Architecture

Single-module plugin. Source packages under `io.kestra.plugin`:

- `zoom`

Infrastructure dependencies (Docker Compose services):

- `app`

### Key Plugin Classes


- `io.kestra.plugin.zoom.SendMessage`
- `io.kestra.plugin.zoom.AbstractZoomConnection`

### Project Structure

```
plugin-zoom/
├── src/main/java/io/kestra/plugin/zoom/
├── src/test/java/io/kestra/plugin/zoom/
├── build.gradle
└── README.md
```

## References

- https://kestra.io/docs/plugin-developer-guide
- https://kestra.io/docs/plugin-developer-guide/contribution-guidelines
