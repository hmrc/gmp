# GMP Service

Guaranteed Minimum Pension microservice with HIP (Hosted Integration Platform) and DES (Data Exchange Service) integration.

[![Build Status](https://travis-ci.org/hmrc/gmp.svg?branch=master)](https://travis-ci.org/hmrc/gmp) [![Download](https://api.bintray.com/packages/hmrc/releases/gmp/images/download.svg)](https://bintray.com/hmrc/releases/gmp/_latestVersion)

## Features

- SCON validation via HIP (Hosted Integration Platform)
- Fallback to DES (Data Exchange Service) when HIP is unavailable
- Secure logging with sensitive data redaction
- Caching for improved performance
- Comprehensive test coverage (98.68% statement, 91.43% branch)

## SCON Structure(^S[0124568]\d{6}(?![GIOSUVZ])[A-Z]$)
- An SCON always begins with S (mandatory).
- It’s followed by a digit indicating the scheme type (e.g. 0,1,2,4,5,6,8 depending on the scheme rules).
- Then 6 more digits.
- Suffix letter, must avoid certain letters [GIOSUVZ].

API
----

| PATH | Supported Methods | Description |
|------|-------------------|-------------|
| ```/:userId/gmp/calculate``` | POST | Requests a GMP calculation for a specific user |
|```/:userId/gmp/validateScon``` | POST | Validates the SCON for the specified user |

## gmp/calculate

Calculates GMP for a specific user. This method calls into a connected DES service to perform the actual calculation, and returns the result.

__Request__

| Field | Description |
|-------|-------------|
| scon | The person's scheme contracted out number |
| nino | The national insurance number |
| surname | The person's surname |
| firstForename | The person's forename |
| calcType | _Optional_ The calculation type |
| revaluationDate | _Optional_  The date on which the GMP should be revalued |
| revaluationRate | _Optional_  The rate at which the GMP should be revalued |
| requestEarnings | _Optional_  Whether constants and earnings values should be returned in the response |
| dualCalc | _Optional_  Whether opposite gender calculation should be performed |
| terminationDate | _Optional_  The date on which the member left the scheme |

Example JSON response:

```json
{
   "name": "J Bloggs",
   "nino": "<user national insurance number>",
   "scon": "<user scon>",
   "revaluationRate": "HMRC",
   "revaluationDate": "2016-08-27",
   "calculationPeriods": [],
   "globalErrorCode": 0,
   "spaDate": "2010-02-25",
   "payableAgeDate": "2016-03-01",
   "dateOfDeath": "1999-05-01",
   "dualCalc": false,
   "calcType": 1
}
```

### HIP 422 handling

HIP returns 422 for validation failures with a body shaped as:

```json
{
  "failures": [
    { "reason": "No Match for person details provided", "code": "63119" }
  ]
}
```

This service converts that into a normal 200 OK response with:
- `globalErrorCode` set to the numeric failure `code` (e.g. 63119),
- `calculationPeriods` empty,

Example mapped response:

```json
{
  "name": "J Bloggs",
  "nino": "AB123456C",
  "scon": "S1401234Q",
  "revaluationRate": null,
  "revaluationDate": null,
  "calculationPeriods": [],
  "globalErrorCode": 63119,
  "spaDate": null,
  "payableAgeDate": null,
  "dateOfDeath": null,
  "dualCalc": false,
  "calcType": 1
}
```

### HIP 200 handling (success)

On HIP success (HTTP 200), we return a 200 OK with a populated payload transformed from HIP’s response:
- globalErrorCode: always 0 on HIP success.
- calculationPeriods: derived from HIP’s GuaranteedMinimumPensionDetailsList with field mapping and value formatting.
- revaluationRate (per period): mapped from HIP strings → ints (S148 → 1, FIXED → 2, LIMITED → 3, unknown → 0).
- errorCode (per period): mapped from HIP’s gmpErrorCode string to service-specific codes.
- Contributions and earnings are formatted consistently (e.g., integer grouping for >= 1987).

Example mapped response (truncated):

```json
{
  "name": "J Bloggs",
  "nino": "AB123456C",
  "scon": "S1401234Q",
  "revaluationRate": "FIXED",
  "revaluationDate": "2022-06-27",
  "calculationPeriods": [
    {
      "startDate": "1978-04-06",
      "endDate": "2006-04-05",
      "gmpTotal": "10.56",
      "post88GMPTotal": "1.23",
      "revaluationRate": 2,
      "errorCode": 0,
      "revalued": 1,
      "dualCalcPost90TrueTotal": "10.56",
      "dualCalcPost90OppositeTotal": "10.56",
      "inflationProofBeyondDod": 1,
      "contsAndEarnings": []
    }
  ],
  "globalErrorCode": 0,
  "spaDate": "2022-06-27",
  "payableAgeDate": "2022-06-27",
  "dateOfDeath": null,
  "dualCalc": false,
  "calcType": 1
}
```

## gmp/validateScon

An API method to validate whether a user's SCON exists or not. The service will attempt to use HIP (Hosted Integration Platform) by default, with a fallback to DES (Data Exchange Service) if configured.

### Request

```http
POST /:userId/gmp/validateScon
Content-Type: application/json

{
  "scon": "S1401234Q"
}
```

#### Request Fields

| Field | Required | Description |
|-------|----------|-------------|
| scon  | Yes      | The user's SCON to validate (3-10 alphanumeric characters) |

### Response

#### Success (200 OK)
```json
{
  "sconExists": true
}
```

#### Error Responses

| Status | Description | Response Body |
|--------|-------------|----------------|
| 400 Bad Request | Invalid SCON format | `{"error": "Invalid SCON format"}` |
| 500 Internal Server Error | Service unavailable | `{"error": "Service unavailable"}` |

### Features

- **HIP Integration**: Validates SCONs using the Hosted Integration Platform
- **Caching**: Responses are cached to improve performance
- **Secure Logging**: All logs are automatically redacted to protect sensitive information
- **Fallback Mechanism**: Automatically falls back to DES/IF if HIP is unavailable (configurable)
- **HIP 422 Mapping**: Upstream HIP 422 failures are returned as 200 responses with `globalErrorCode` set to the HIP failure `code`.

## Local Development

### Prerequisites

- Java 8 or later
- sbt 1.5.0 or later
- Service Manager (for dependent services)

### Running the Application

1. Start required services using Service Manager:
   ```bash
   sm2 --start GMP_ALL
   ```

2. Run the application:
   ```bash
   sbt run 
   ```

### Configuration

The following configuration options are available in `application.conf`:

| Setting | Default | Description |
|---------|---------|-------------|
| `app.hip.enabled` | `true` | Enable/disable HIP integration |
| `app.hip.url` | - | Base URL for HIP service |
| `app.hip.authorisationToken` | - | Authorization token for HIP |
| `app.cache.enabled` | `true` | Enable/disable response caching |

### Testing

Run all tests:
```bash
sbt test
```

Generate coverage report:
```bash
sbt clean coverage test coverageReport
```

### Logging

The application uses SLF4J with Logback for logging. Sensitive data is automatically redacted from logs.

- **Log Levels**:
  - `ERROR`: Application errors and exceptions
  - `WARN`: Non-critical issues
  - `INFO`: Important application events
  - `DEBUG`: Detailed debug information (sensitive data is redacted)

Example log output:
```
[INFO] [ValidateSconController] HIP validation successful for SCON: S14******
[WARN] [HipConnector] HIP service returned 400 for SCON: S14******
```

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").



    
