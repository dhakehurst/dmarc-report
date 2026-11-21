# dmarc-report

Reads a folder of DMARC aggregate reports, as received by email (`.zip`, `.tar.gz`, `.gz` or `.xml`),
and prints a table of the records where DKIM or SPF failed: one row per record, with the information
a sender needs to find the message (reporter, date, source IP with its host name, owner, country and location, count, header/envelope from,
envelope to, DKIM and SPF results, disposition).

## Install

Build the distribution, or download it from a release:

```
cd net.akehurst.dmarc.report
./gradlew :dmarc-report-cli:jvmDistZip
```

The zip is written to `net.akehurst.dmarc.report/.gradle-build/dmarc-report-cli/distributions/`.
Unzip it anywhere; it needs Java 17 or later.

## Run

```
dmarc-report-<version>-jvm/bin/dmarc-report [options] <folder>
```

On Windows use `bin\dmarc-report.bat`.

| Option | |
|---|---|
| `--html <file>` | also write the table as an HTML page; open it in a browser, select the table and paste it into an email |
| `--all` | include records that passed as well |
| `--no-lookup` | do not look up the source IPs; otherwise each is looked up with reverse DNS, RDAP (owner, country) and ip-api.com (location, over plain http) |
| `--no-legend` | do not print the explanation of the columns after the table |
| `--version` | print the version |
| `--help` | print the help |

The table goes to standard output; the number of reports read and any skipped files go to standard error.

## Layout

| Folder | |
|---|---|
| `net.akehurst.dmarc.report/0_build-logic` | Gradle convention plugin |
| `net.akehurst.dmarc.report/1_Requirements` | [requirement specification](net.akehurst.dmarc.report/1_Requirements/Specification.md) |
| `net.akehurst.dmarc.report/2_Architecture` | PlantUML diagrams |
| `net.akehurst.dmarc.report/3_Code/dmarc-report-core` | multiplatform reading of archives and reports, and the table |
| `net.akehurst.dmarc.report/3_Code/dmarc-report-cli` | the command line application |
