# MODL API V2 Migration Guide

This document describes the differences between V1 and V2 APIs and lists any features that are not available in V2.

## Overview

The MODL Minecraft plugin supports two API versions:

- **V1 API**: Panel-based API at `{panel-url}/api` - Full feature set for Minecraft server integration
- **V2 API**: Centralized API at `api.modl.gg/v1` (or `api.modl.top/v1` for testing) - Newer architecture with some Minecraft-specific features pending implementation

## Automatic Version Detection

The plugin automatically detects which API version to use:

1. On startup, the plugin sends a request to `{panel-url}/api`
2. If the response status is **410 (Gone)**, the plugin uses V2 API
3. Otherwise, the plugin uses V1 API

## Configuration

```yaml
api:
  key: "your-api-key-here"
  url: "https://yourserver.modl.gg"
  debug: false
  # Use testing API (api.modl.top) instead of production (api.modl.gg)
  testing-api: false
```

## Endpoint Mapping

### Fully Supported Endpoints (V1 → V2)

| V1 Endpoint | V2 Endpoint | Notes |
|-------------|-------------|-------|
| `POST /api/public/tickets` | `POST /v1/public/tickets` | Ticket creation |
| `POST /api/public/tickets/unfinished` | `POST /v1/public/tickets/unfinished` | Unfinished ticket creation |
| `POST /api/minecraft/player/login` | `POST /v1/minecraft/players/login` | Player login (simplified response) |
| `GET /api/minecraft/player/{uuid}` | `GET /v1/panel/players/{uuid}` | Player profile |
| `GET /api/minecraft/player/{uuid}/linked-accounts` | `GET /v1/panel/players/{uuid}/linked` | Linked accounts |
| `POST /api/minecraft/player/{uuid}/notes` | `POST /v1/panel/players/{uuid}/notes` | Create player note |
| `POST /api/minecraft/punishment/create` | `POST /v1/panel/players/{uuid}/punishments` | Create punishment |
| `POST /api/minecraft/punishment/dynamic` | `POST /v1/panel/players/{uuid}/punishments` | Create punishment with response |
| `GET /api/minecraft/punishment-types` | `GET /v1/panel/settings/punishment-types` | Get punishment types |
| `GET /api/minecraft/staff-permissions` | `GET /v1/panel/auth/permissions` | Get staff permissions |

### Partially Supported Endpoints

| V1 Endpoint | V2 Status | Notes |
|-------------|-----------|-------|
| `POST /api/minecraft/player-lookup` | Partial | Uses search endpoint, limited data |
| `POST /api/minecraft/punishment/{id}/pardon` | Partial | V2 requires player UUID in path |
| `GET /api/minecraft/player?minecraftUuid={uuid}` | Supported | Maps to `/v1/panel/players/{uuid}` |
| `GET /api/minecraft/player-name?username={name}` | Partial | Uses search endpoint |

## Missing V2 Routes

The following V1 endpoints are **NOT available** in the V2 API and require backend implementation:

### Critical for Plugin Operation

| V1 Endpoint | Purpose | Impact |
|-------------|---------|--------|
| `POST /api/minecraft/sync` | Periodic sync of online players, pending punishments, notifications, staff updates | **HIGH** - Core feature for real-time moderation |
| `POST /api/minecraft/punishment/acknowledge` | Mark punishment as received/enforced by server | **HIGH** - Prevents duplicate punishment execution |
| `POST /api/minecraft/notification/acknowledge` | Mark notification as delivered to player | **MEDIUM** - May cause duplicate notifications |

### Moderate Priority

| V1 Endpoint | Purpose | Impact |
|-------------|---------|--------|
| `POST /api/minecraft/player/disconnect` | Log player disconnect event | **LOW** - Used for session tracking |
| `POST /api/minecraft/player/pardon` | Pardon all active punishments for a player | **MEDIUM** - Bulk pardon feature |

## V2 Response Format Differences

### Player Login Response

**V1 Response:**
```json
{
  "status": 200,
  "activePunishments": [
    {
      "id": "...",
      "type": "BAN",
      "reason": "...",
      "expiresAt": "..."
    }
  ],
  "pendingNotifications": [...]
}
```

**V2 Response:**
```json
{
  "success": true,
  "message": "Login successful"
}
```

> **Note**: V2 login does NOT return active punishments or pending notifications. This data must be fetched separately or handled through an alternative mechanism.

### Ticket Creation Response

**V1 Response:**
```json
{
  "success": true,
  "ticketId": "abc123",
  "message": "Ticket created",
  "ticket": {
    "id": "abc123",
    "type": "REPORT",
    "subject": "...",
    "status": "OPEN",
    "created": "2024-01-01T00:00:00Z"
  }
}
```

**V2 Response:**
```json
{
  "success": true,
  "ticketId": "abc123",
  "message": "Ticket created successfully",
  "ticket": {
    "id": "abc123",
    "type": "REPORT",
    "subject": "...",
    "status": "open",
    "created": "2024-01-01T00:00:00.000Z"
  }
}
```

## Recommended Backend Implementation

To fully support the Minecraft plugin with V2 API, the following endpoints should be implemented:

### 1. Minecraft Sync Endpoint

```
POST /v1/minecraft/sync
```

**Request:**
```json
{
  "lastSyncTimestamp": "2024-01-01T00:00:00Z",
  "onlinePlayers": [
    { "uuid": "...", "username": "...", "ipAddress": "..." }
  ],
  "serverStatus": {
    "onlinePlayerCount": 50,
    "maxPlayers": 100,
    "serverVersion": "1.20.4",
    "timestamp": "2024-01-01T00:00:00Z"
  }
}
```

**Response:**
```json
{
  "timestamp": "2024-01-01T00:00:01Z",
  "data": {
    "pendingPunishments": [...],
    "recentlyStartedPunishments": [...],
    "recentlyModifiedPunishments": [...],
    "playerNotifications": [...],
    "activeStaffMembers": [...]
  }
}
```

### 2. Punishment Acknowledge Endpoint

```
POST /v1/minecraft/punishments/acknowledge
```

**Request:**
```json
{
  "punishmentIds": ["id1", "id2"]
}
```

### 3. Notification Acknowledge Endpoint

```
POST /v1/minecraft/notifications/acknowledge
```

**Request:**
```json
{
  "notificationIds": ["id1", "id2"]
}
```

### 4. Player Disconnect Endpoint

```
POST /v1/minecraft/players/disconnect
```

**Request:**
```json
{
  "minecraftUuid": "...",
  "serverName": "..."
}
```

### 5. Bulk Player Pardon Endpoint

```
POST /v1/minecraft/players/{uuid}/pardon-all
```

**Request:**
```json
{
  "reason": "...",
  "issuerName": "..."
}
```

## Migration Checklist

When migrating servers from V1 to V2:

- [ ] Update backend to return 410 from old `/api` endpoint
- [ ] Implement missing V2 endpoints (see above)
- [ ] Test ticket creation
- [ ] Test punishment creation
- [ ] Test player login
- [ ] Test sync functionality (requires new endpoint)
- [ ] Verify notification delivery

## Troubleshooting

### Plugin uses V1 when V2 expected

Ensure your panel's `/api` endpoint returns HTTP 410 (Gone) status code.

### Missing punishments on login

V2 login response doesn't include active punishments. Check if sync endpoint is implemented.

### Duplicate punishments

Ensure the punishment acknowledge endpoint is implemented in V2.

## Version History

- **1.0**: Initial V1/V2 dual support implementation
