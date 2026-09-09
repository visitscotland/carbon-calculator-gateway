# Carbon Calculator Gateway (CCG)

Carbon Calculator Gateway (CCG) is a Spring Boot microservice that acts as an integration layer between the Isla Carbon 
Calculator registration service and [BREG](https://github.com/visitscotland/breg) for user engagement and support.

## Purpose

The service allows users to register for an Isla Carbon Calculator assessment and advisory service. During the 
registration process, the submitted information is also forwarded to BloomReach Engagement to support follow-up 
communications, marketing activities, and user engagement tracking.

### Key Features

* **Data-agnostic processing** – Only the fields required to process the requests are defined. Additional fields can be 
  submitted without requiring changes to the application.
* **reCAPTCHA validation** – Validates submissions before they are processed.
* **External service integration** – Integrates with Isla (via the Trace API) and BloomReach Engagement (via BREG), with 
  built-in error handling.
* **Error consolidation** – Aggregates errors from downstream services into a consistent response.
* **Email notifications** – Sends notifications when submissions require manual intervention.

--- 

## Development

The development documentation has been separated into dedicated documents to keep this README focused on the purpose and
architecture of the application. The links below provide guidance on running, configuring, releasing, and maintaining 
the service.

- [Running the application](docs/running-the-application.md)
- [Environments & Release](docs/release.md)
- [Application Configuration](docs/configuration.md)
- [CI/CD](https://jenkinssb.visitscotland.com/job/ccg/)

---
## Architecture Overview

### Request Diagram
```
┌─────────────┐
│  Web Form   │ (browser, SPA, CMS)
└────┬────────┘
     │ HTTP POST + registration data
     ▼
┌──────────────────────────────────────────────────────────────┐
│                Carbon Calculator Gateway (CCG)               │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐  │
│  │ 1. Validate reCAPTCHA                                  │  │
│  │ 2. Submit registration to Trace API                    │  │
│  │ 3. Submit data to BREG                                 │  │
│  │ 4. Notify support if manual intervention is required   │  │
│  └────────────────────────────────────────────────────────┘  │
└────┬────────────────────┬─────────────────────┬──────────────┘
     │                    │                     │
     │ Isla               │ BloomReach          │ Notify 
     │ Registration       │ Engagement          │ on failure
     ▼                    ▼                     ▼
┌────────────────┐   ┌─────────────────┐   ┌──────────────────┐
│ Trace API      │   │ BREG            │   │ SMTP Mail Server │
└────────────────┘   └─────────────────┘   └──────────────────┘
```

#### External Dependencies

| Service   | Purpose                                                                   |
|-----------|---------------------------------------------------------------------------|
| Trace API | Registers businesses with the Isla Carbon Calculator platform.            |
| BREG      | Stores submissions and forwards engagement data to BloomReach Engagement. |
| SMTP      | Sends notification emails when manual intervention may be required.       |

---

### Request Processing Pipeline

#### 1. Receive Request

The service accepts an HTTP `POST` request containing the registration data as a JSON payload.

#### 2. Validate Request

Before any processing takes place, the service:

- Validates the reCAPTCHA token
- Generates a unique submission identifier (`vsUID`) used to correlate the submissions sent to Trace API and BREG.

#### 3. Register with Isla

The payload is transformed to match the Trace API contract.

Processing performed:

- Remove fields not required by Trace API (for example `consentList` and `recaptcha`).
- Remove empty or variant fields.*
- Add service-generated fields (for example `vsUID`).
- Obtain an authentication token.
- Submit the registration to the Trace API.
- Record any errors returned by the service.

#### 4. Submit to BREG

The payload is transformed to match the BREG contract.

Processing performed:

- Remove fields not required by BREG.
- Remove empty or variant fields.*
- Add service-generated fields (for example `vsUID`).
- Include Trace API status information, if applicable.
- Submit the payload to BREG.
- Record any errors returned by the service.

#### 5. Generate Response

The service consolidates the outcome of both submissions.

- Returns the appropriate response to the client.
- If either submission requires manual intervention, an email notification is sent (when configured).

#### Notes:
- (\*) Variant fields are dynamic form fields configurable in the application. These are normalized before submission so 
both downstream services receive a consistent payload.
- The Trace API and BREG submissions are independent. A failure in one does not prevent the other from being attempted.

## API

CCG exposes a single endpoint.

| Method | Path               | Purpose                                                                                                        |
|--------|--------------------|----------------------------------------------------------------------------------------------------------------|
| POST   | `/register`        | Registers a business with Isla Carbon Calculator and records the submission in BloomReach Engagement via BREG. |
| GET    | `/health`          | Health check endpoint used to verify that the application is running.                                          |
| GET    | `/info/properties` | Displays the active application configuration. Available only when the dev profile is active.                  |
| GET    | `/info/health`     | Displays the response times for Downstream services. Available only when the dev profile is active.            |

For request and response examples, see [API Documentation](docs/api.md).
