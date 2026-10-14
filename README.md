# UK Retail Analytics with Scala

A Scala 3 command-line analytics project that processes the UK Online Retail dataset and answers three business questions: which countries generate the most revenue, how revenue changes by invoice month, and which customers combine repeat purchasing with high average order value. It demonstrates immutable Scala collections, case classes, safe parsing, and error-aware resource handling.

## Tools used

- Scala 3.3.4
- SBT 1.9.9+
- `scala-csv` 2.0.0 for CSV parsing
- MUnit 1.0.0 is configured for tests
- Java 17+ recommended

## My contribution

Gavin Goh Shau Han — Architect. I designed the `Transaction` and result case classes, the classpath-based data-loading flow, safe parsing and resource handling, and the overall analytics pipeline.

This was originally completed as a three-person coursework project. The other contributors were Lee How Seet (Coder) and Jeremy Tey Jie Ming (Documenter). Confirm each teammate's consent and GitHub profile before publishing their names or profile links.

## What it analyses

1. Top five countries by total revenue, excluding returns and invalid purchases.
2. Total revenue grouped by invoice month.
3. Customers with at least 20 distinct invoices and an average order value of at least £750.

## How to run

The repository includes the dataset at `src/main/resources/data.csv`, so no manual download is needed for a local run.

```bash
sbt clean compile
sbt run
```

The program loads the CSV from the classpath and prints all three reports. If you use a fresh machine, install Scala/SBT or use an SBT distribution that provides them.

## Sample output

The full captured output is in [`docs/output.txt`](docs/output.txt). The run begins with:

```text
Loaded 541909 transactions from src/main/resources/data.csv

=== Q1: Top 5 countries by total revenue ===
 1. United Kingdom     9,025,222.08 GBP
 2. Netherlands          285,446.34 GBP
 3. EIRE                 283,453.96 GBP
 4. Germany              228,867.14 GBP
 5. France               209,715.11 GBP
```

## Demo link

This is a command-line project and does not currently have a deployed web demo.

## Dataset and attribution

The project uses the UK Online Retail dataset, originally sourced from [Kaggle](https://www.kaggle.com/datasets/carrie1/ecommerce-data). Before making this repository public, verify the dataset's current redistribution terms and retain the required attribution.

## License

No open-source license is included. This project originated as university coursework, so confirm the university/course rules and dataset terms before adding a license.

## Repository topics and description

Suggested GitHub description: `Scala 3 analytics pipeline for UK Online Retail revenue, monthly trends, and loyal high-spending customers.`

Suggested topics: `scala`, `scala3`, `sbt`, `data-analysis`, `data-pipeline`, `ecommerce`, `retail-analytics`, `functional-programming`, `collections`, `csv`.
