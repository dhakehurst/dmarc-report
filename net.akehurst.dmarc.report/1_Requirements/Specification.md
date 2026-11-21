# Requirement Specification

The purpose of this application is to read the DMARC aggregate reports that a domain owner receives by email,
and to produce a table of the messages that failed DKIM or SPF, in a form that can be pasted into an email
and that gives a sender enough information to find the message that had the problem.

Normative language
- This document uses the keywords `SHALL` and `MAY` in accordance with RFC 2119. `SHALL` indicates a requirement and `MAY` indicates an option.

## 1. Input

- [REQ-1.1] The application SHALL read every file in a given folder and, recursively, its sub-folders.
- [REQ-1.2] The application SHALL read DMARC aggregate reports as defined in RFC 7489 Appendix C.
  - [REQ-1.2.1] Elements SHALL be matched by local name, so that reports using a namespace prefix (e.g. DMARCbis) are read.
  - [REQ-1.2.2] Unknown elements SHALL be ignored.
- [REQ-1.3] The application SHALL read reports that are
  - [REQ-1.3.1] plain XML files,
  - [REQ-1.3.2] gzip files (`.gz`), including files with several gzip members,
  - [REQ-1.3.3] zip files (`.zip`),
  - [REQ-1.3.4] tar files (`.tar`) and gzipped tar files (`.tar.gz`, `.tgz`),
  - [REQ-1.3.5] any nesting of the above, up to 4 levels.
- [REQ-1.4] The format of a file SHALL be decided from its content, not from its name.
- [REQ-1.5] A file that cannot be read, or is not a DMARC aggregate report, SHALL be reported as skipped, with the reason, and SHALL NOT stop the other files being read.
- [REQ-1.6] A report that is found more than once (same reporting organisation and report id) SHALL be used once.

## 2. Table

- [REQ-2.1] The table SHALL have one row per report record.
- [REQ-2.2] By default, the table SHALL contain only records that failed, i.e. a record where
  - [REQ-2.2.1] the evaluated DMARC DKIM result is not `pass`, or
  - [REQ-2.2.2] the evaluated DMARC SPF result is not `pass`, or
  - [REQ-2.2.3] any DKIM authentication result is not `pass`, or
  - [REQ-2.2.4] any SPF authentication result is not `pass`.
- [REQ-2.3] The application MAY include records that passed, when asked to (`--all`).
- [REQ-2.4] By default the table SHALL have only the columns that help an ordinary user recognise a message they sent:
  - [REQ-2.4.1] Date: the day (UTC) the report starts,
  - [REQ-2.4.3] Owner: the organisation the sending server's IP is registered to, from RDAP,
  - [REQ-2.4.4] Country: the country of the sending server's IP from geolocation (ip-api.com), or else the country it is registered in (RDAP),
  - [REQ-2.4.5] Count: the number of messages,
  - [REQ-2.4.6] Header From and Envelope To,
  - [REQ-2.4.7] Reporter: the organisation that sent the report,
  - [REQ-2.4.8] Disposition: what the receiver did with the messages.
  - [REQ-2.4.9] The lookups of REQ-2.4.3, REQ-2.4.4 and Source Host (REQ-2.4a) SHALL be made unless asked not to (`--no-lookup`); a failed lookup SHALL leave its cells empty and SHALL NOT stop the application.
- [REQ-2.4a] When asked to (`--details`), the table SHALL also have the technical columns: Source IP, Source Host (the host name given by reverse DNS of the source IP), Envelope From, the evaluated DKIM and SPF results, each DKIM signature (domain, selector, result) and each SPF check (domain, result).
- [REQ-2.5] Columns that are empty in every row SHALL be left out, to keep the table narrow.
- [REQ-2.6] When there are two or more rows, a column with the same value in every row SHALL be taken out of the table and printed once above it, as `<column>: <value> (all rows)`.
- [REQ-2.7] Rows SHALL be sorted by report start date, then reporter, then source IP.

## 3. Output

- [REQ-3.1] The table SHALL be printed as text aligned with spaces, to paste into a plain text or monospaced email.
- [REQ-3.2] The application SHALL, when asked to (`--html <file>`), write the table as an HTML page that can be opened in a browser and pasted into an email as a table.
- [REQ-3.3] After the table, the application SHALL print what each shown column means, unless asked not to (`--no-legend`); the HTML page SHALL include the same explanation.
- [REQ-3.4] The number of reports read, the number of rows, and the skipped files SHALL be written to standard error, so that standard output holds only the table.

## 4. Running

- [REQ-4.1] The application SHALL be a command line application: `dmarc-report [options] <folder>`.
- [REQ-4.2] The application SHALL be distributed as a zip file that is unzipped and run from any folder, with start scripts for Unix-like systems and Windows.
- [REQ-4.3] The application SHALL NOT need to be run from the build folder.
- [REQ-4.4] Reading and table code SHALL be common (multiplatform) code; only the program entry point MAY be platform specific.
