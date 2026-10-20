# Messaging API

Source: **Osporo API v1.docx**, Messaging Domain. Converted and checked against the repository on 2026-10-19.

**Status:** Planned API; no controller implements these routes.

Read [API standards](../API_STANDARDS.md) for the current wire contract and shared rules, and the [domain index](README.md) for source interpretation. All routes remain under `/v1`. The endpoint descriptions and examples in **Source API design** document the original design, not proof of availability.

## Implementation notes and open decisions

No messaging controller, thread service, or pre-send filter implements these routes. Participant isolation, privacy, unread counts, and filtering below remain planned.

Open details from the source:

- Thread creation may return an existing thread, but only creation status 201 is documented. Whether the submitted message is appended to an existing thread and how retries behave need definition.
- Fetching a thread does not specify whether it marks messages read; do not add a GET side effect implicitly.
- Define ordering of paginated messages, concurrent thread creation, recipient/listing relationships, and whether a listing is required for every conversation.
- Moderation approval later delivering a blocked message must be reconciled with [ADR-010](../adrs/ADR-010-pre-send-message-filtering.md), which describes blocked content as not persisted to the conversation and requiring the sender to rewrite it.

Editorial correction: the source's `404 Found` is corrected to `404 Not Found`. Related design: [moderation](moderation.md).

## Endpoint index

| Endpoint | Operation |
| --- | --- |
| `GET /v1/messages/threads` | [1) Get inbox](#1-get-inbox) |
| `GET /v1/messages/threads/{threadId}` | [2) Get thread with messages](#2-get-thread-with-messages) |
| `POST /v1/messages/threads` | [3) Start a new thread](#3-start-a-new-thread) |
| `POST /v1/messages/threads/{threadId}` | [4) Send message to existing thread](#4-send-message-to-existing-thread) |

## Source API design

## Purpose

- Allow participants to communicate within the context of a marketplace
- Protect participant privacy — contact details are never exposed
- Pre-send filtering blocks messages that violate tenant moderation rules

## Endpoints

### 1) Get inbox

`GET /v1/messages/threads`

#### Parameters

| Parameter | Default | Constraints | Description |
| --- | --- | --- | --- |
| page | 1 | ≥ 1 | Page number |
| per_page | 20 | 1 – 100 | Results per page |
| order | desc | asc, desc | Sort by last activity |

#### Permissions

MESSAGE_READ_OWN

#### Request

No body

#### Response

```json
{
  "data": [
    {
      "id": "uuid",
      "listing": {
        "id": "uuid",
        "title": "Sony WH-1000XM5",
        "primary_image_url": "https://..."
      },
      "participant": {
        "id": "uuid",
        "display_name": "John Doe",
        "avatar_url": "https://..."
      },
      "last_message": {
        "body": "Is this still available?",
        "sent_at": "2026-03-18T11:30:00Z",
        "is_mine": false
      },
      "unread_count": 1,
      "updated_at": "2026-03-18T11:30:00Z"
    }
  ],
  "meta": {
    "request_id": "uuid",
    "pagination": {
      "page": 1,
      "per_page": 20,
      "total": 5,
      "total_pages": 1
    }
  }
}
```

#### Success

200 OK

#### Errors

401 Unauthorized

Error: INVALID_TOKEN

### 2) Get thread with messages

`GET /v1/messages/threads/{threadId}`

| Parameter | Default | Constraints | Description |
| --- | --- | --- | --- |
| page | 1 | ≥ 1 | Page number |
| per_page | 50 | 1 – 100 | Results per page |

#### Permissions

MESSAGE_READ_OWN — participants only. Non-participants receive 404.

#### Request

No body

#### Response

```json
{
  "data": {
    "id": "uuid",
    "listing": {
      "id": "uuid",
      "title": "Sony WH-1000XM5",
      "primary_image_url": "https://..."
    },
    "participant": {
      "id": "uuid",
      "display_name": "John Doe",
      "avatar_url": "https://..."
    },
    "messages": [
      {
        "id": "uuid",
        "body": "Is this still available?",
        "is_mine": false,
        "sent_at": "2026-03-18T11:30:00Z"
      },
      {
        "id": "uuid",
        "body": "Yes it is, still in great condition.",
        "is_mine": true,
        "sent_at": "2026-03-18T11:35:00Z"
      }
    ]
  },
  "meta": {
    "request_id": "uuid",
    "pagination": {
      "page": 1,
      "per_page": 50,
      "total": 2,
      "total_pages": 1
    }
  }
}
```

#### Success

200 OK

#### Errors

404 Not Found

Error: THREAD_NOT_FOUND

### 3) Start a new thread

`POST /v1/messages/threads`

#### Permissions

MESSAGE_CREATE

Creates a new thread or returns an existing thread if one already exists between the caller and the recipient for the same listing. Prevents duplicate conversations.

#### Request

```json
{
  "listing_id": "uuid",
  "recipient_id": "uuid",
  "body": "Is this still available?"
}
```

#### Response

```json
{
  "data": {
    "id": "uuid",
    "listing": {
      "id": "uuid",
      "title": "Sony WH-1000XM5"
    },
    "participant": {
      "id": "uuid",
      "display_name": "John Doe",
      "avatar_url": "https://..."
    },
    "messages": [
      {
        "id": "uuid",
        "body": "Is this still available?",
        "is_mine": true,
        "sent_at": "2026-03-18T11:30:00Z"
      }
    ]
  },
  "meta": {
    "request_id": "uuid"
  }
}
```

#### Success

201 Created

#### Errors

403 Forbidden

Error: INSUFFICIENT_PERMISSIONS

404 Not Found

Error: LISTING_NOT_FOUND

404 Not Found

Error: PROFILE_NOT_FOUND           ←  recipient does not exist

422 Unprocessable Entity

Error: CANNOT_MESSAGE_SELF         ←  caller and recipient are the same user

422 Unprocessable Entity

Error: MESSAGE_BLOCKED             ←  first message triggered a filter rule

422 Unprocessable Entity

Error: PARTICIPANT_SUSPENDED       ←  caller or recipient is suspended

### 4) Send message to existing thread

`POST /v1/messages/threads/{threadId}`

#### Permissions

MESSAGE_CREATE — participants only

#### Request

```json
{
  "body": "Yes, still available. Pick up in Pretoria."
}
```

#### Response

```json
{
  "data": {
    "id": "uuid",
    "thread_id": "uuid",
    "body": "Yes, still available. Pick up in Pretoria.",
    "is_mine": true,
    "sent_at": "2026-03-18T11:35:00Z"
  },
  "meta": {
    "request_id": "uuid"
  }
}
```

#### Success

201 Created

#### Errors

403 Forbidden

Error: INSUFFICIENT_PERMISSIONS

404 Not Found

Error: THREAD_NOT_FOUND

422 Unprocessable Entity

Error: MESSAGE_BLOCKED

422 Unprocessable Entity

Error: PARTICIPANT_SUSPENDED
