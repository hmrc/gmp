GMP
============

Guaranteed Minimum Pension micro service

[![Build Status](https://travis-ci.org/hmrc/gmp.svg?branch=master)](https://travis-ci.org/hmrc/gmp) [ ![Download](https://api.bintray.com/packages/hmrc/releases/gmp/images/download.svg) ](https://bintray.com/hmrc/releases/gmp/_latestVersion)

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

## gmp/validateScon

An API method to validate whether a user's SCON exists or not.

__POST fields__

| Field | Description |
| --- | --- |
| scon | The user's SCON to validate with the DES service |

Example JSON response:

```json
{
   "sconExists": true
}
```

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").

    
