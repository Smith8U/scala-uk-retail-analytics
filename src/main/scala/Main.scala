// UK Retail Analytics — Scala 3 collection-based analysis

import java.io.InputStreamReader
import scala.util.Using                   // for safe, auto-closing resource management
import com.github.tototoshi.csv.CSVReader // third-party CSV parser (scala-csv library)

// Represents a single row from the sales/transactions CSV
final case class Transaction(
                              invoiceNo: String,
                              stockCode: String,
                              description: String,
                              quantity: Int,
                              invoiceDate: String,
                              unitPrice: Double,
                              customerId: Option[String], // None if the row had no customer ID
                              country: String
                            ):

  //Computes the revenue by price x quantity
  def lineRevenue: Double = quantity * unitPrice

  //True only for genuine sales (excludes returns/cancellations, which have negative quantity)
  def isPurchase: Boolean = quantity > 0 && unitPrice > 0.0

object Transaction:

  // Attempts to build a Transaction from one CSV row's raw string fields.
  // Returns None if the row is malformed or has the wrong number of columns.
  def fromFields(fields: Seq[String]): Option[Transaction] =
    fields match
      // Only accept rows with exactly 8 fields, in the expected order
      case Seq(invoice, stock, desc, qty, date, price, customer, country) =>
        // Safely parse numeric fields; short-circuits to None on bad data
        for
          quantity  <- qty.trim.toIntOption         //.toIntOption/ .toDoubleOption return None if the string isn't a valid number.
          unitPrice <- price.trim.toDoubleOption
        yield Transaction(
          invoice.trim,
          stock.trim,
          desc.trim,
          quantity,
          date.trim,
          unitPrice,
          //Turns an empty/blank customer field into None, otherwise keeps it as Some(id).
          Some(customer.trim).filter(_.nonEmpty),
          country.trim
        )
      case _ => None // wrong column count, skip this row

// Loads the dataset file with the tototoshi scala-csv library (S1-4)
// every IO failure surfaces as an Either instead of an exception reaching the user (S1-5).
object CsvLoader:

  // Loads and parses a CSV file from the classpath (e.g. src/main/resources).
  // Returns Left(errorMessage) on failure, or Right(parsedTransactions) on success.
  def load(resourceName: String): Either[String, Vector[Transaction]] = {
    Option(getClass.getResourceAsStream(s"/$resourceName"))                    // returns null if the file isn't found on the classpath
      .toRight(s"resource '$resourceName' not found under src/main/resources") //.toRight(...) converts Option into Either: None becomes Left(errorMessage), Some(stream) becomes Right(stream)
      // If we got a valid stream, open a CSVReader over it
      // decoding bytes as ISO-8859-1 The file ships as ISO-8859-1 (it contains accented product names),
      // so decoding it as UTF-8 would fail partway through
      // Using(...) ensures the reader (and underlying stream) is closed automatically once the block finishes
      .flatMap { stream =>
        Using(CSVReader.open(new InputStreamReader(stream, "ISO-8859-1"))) { reader =>
          // and flatMap automatically discards the Nones and keeps only valid transactions, collecting them into a Vector.
          reader
            .iterator
            .drop(1)
            .flatMap(Transaction.fromFields) //converts each row via Transaction.fromFields (which returns Option), flatMap automatically discards the Nones and keeps only valid transactions, collecting them into a Vector.
            .toVector
          //Using(...) returns a Try then .toEither converts that to Either[Throwable, Vector[Transaction]]
        }.toEither.left.map(err => s"could not read '$resourceName': ${err.getMessage}") //.left.map(...) transforms a failure Throwable into a String error message, so the final return type lines up with Either[String, Vector[Transaction]]
      }
  }

// ---------------------------------------------------------------------------
// The three Section-1 analytical questions
// ---------------------------------------------------------------------------

// Q1 row: a country's position in the revenue leaderboard
// A simple data holder for one row of output: a country's rank, its name, and its total revenue.
final case class TopCountryResult(rank: Int, country: String, revenue: Double)

// Q2 row: invoice month (yyyy-MM) and its total revenue.
final case class MonthlyRevenueResult(month: String, revenue: Double)

//Q3 row: a repeat customer with the metrics derived for the filter.
final case class CustomerStatsResult(customerId: String, orderCount: Int, totalSpend: Double, avgOrderValue: Double)

object Analytics:

  //Q1 (Top-N): the top n countries ranked by total revenue, descending.
  def topCountriesByRevenue(txs: Vector[Transaction], n: Int): List[TopCountryResult] =
    txs.filter(_.isPurchase)                                          // exclude return/cancellations
      //.groupBy(_.country) : bucket transactions per country, Map[String, Vector[Transaction]]
      //.view               : make the next step lazy so no intermediate Map is built
      //.mapValues(...sum)  : sum revenue per country, Map[String, Double] (country → total revenue)
      //.toList             : forces the lazy view into a concrete List[(String, Double)] of (country, totalRevenue) pairs
      //.sortBy(-revenue)   : sort descending (negate) by revenue
      .groupBy(_.country).view.mapValues(_.map(_.lineRevenue).sum).toList.sortBy((_, revenue) => -revenue)                               // sort descending (negate) by revenue
      .take(n)                                                        // keep only the top n
      .zipWithIndex                                                   // attach a 0-based position ((country, revenue), idx)
      .map { case ((country, revenue), idx) =>
        TopCountryResult(idx + 1, country, revenue)                   // convert 0-based idx to 1-based rank
      }

  // Derives a sortable "yyyy-MM" key from a raw "M/d/yyyy H:mm" date string.
  // S1-11: Option[String], Returns None when either numeric part fails to parse.
  private def monthKey(rawDate: String): Option[String] =
    rawDate.split("[/ ]").toList match     //Splits the raw date on either "/" or space, then converts the resulting array to a List so it can be pattern matched
      case month :: _ :: year :: _ =>      //expects month, day (ignored), year , time (ignored)
        for
          m <- month.toIntOption           //both month and year must be parse as valid integers, or the whole thing yields None
          y <- year.toIntOption
        yield f"$y%04d-$m%02d"             //formatted string: 2011 and 3 -> "2011-03"
      case _ => None

  //Q2 (S1-10: Aggregation by group): total revenue per invoice month, in chronological order.
  def revenueByMonth(txs: Vector[Transaction]): List[MonthlyRevenueResult] =
    txs.filter(_.isPurchase)                                                            //excludes returns/cancellations
      .flatMap(t => monthKey(t.invoiceDate).map(month => month -> t.lineRevenue))       //For each transaction, tries to compute its month key. If successful (Some(month)),
                                                                                        //produces a (month, lineRevenue) tuple. If the date failed to parse (None), the transaction is dropped entirely (that's what flatMap does with the Option).
                                                                                        //Result: a flat Vector[(String, Double)] of (month, revenue-for-that-transaction) pairs.
                                                                                        //.groupBy((month, _) => month): bucket the (month, revenue) pairs by month key
      .groupBy((month, _) => month).view.mapValues(_.map(_._2).sum)                     //.view.mapValues(...)         : lazily collapse each bucket: pull out each pair's sum revenue within each bucket, then materialize as List[(month, totalRevenue)]
      .toList                                                                           //.toList — forces the lazy view into a concrete List[(String, Double)] of (month, totalRevenue) pairs.
      .sortBy((month, _) => month)                                                      //sort "YYYY-MM" strings chronologically
      .map((month, revenue) => MonthlyRevenueResult(month, revenue))                    //convert to final result rows

  //Q3 (S1-13 S1-14: .filter.toList.sortBy + AND pipeline): customers who placed at least
  // (minOrders) distinct invoices AND average at least (minAvgOrder) per invoice, sorted by descending total spend.
  def loyalHighSpenders(txs: Vector[Transaction], minOrders: Int, minAvgOrder: Double): List[CustomerStatsResult] =
    txs.filter(t => t.isPurchase && t.customerId.isDefined)                      //keeps only real purchases and rows that actually have a customerID
      .groupBy(_.customerId.getOrElse("unknown"))                                //buckets Transaction by customerID, Map[String, Vector[Transaction]]
      .map { case (id, rows) =>                                                  //Transforms each bucket into single CustomerStatsResult
        val orders = rows.map(_.invoiceNo).distinct.size                         //Counts distinct orders, many items(rows) can have the same invoice, .distinct remove duplicates
        val spend = rows.map(_.lineRevenue).sum                                  //Counts the revenue of every row and sum it
        CustomerStatsResult(id, orders, spend, spend / orders)
      }
      .filter(c => c.orderCount >= minOrders && c.avgOrderValue >= minAvgOrder).toList.sortBy(c => -c.totalSpend) //Keeps customer who meets both thresholds, enough orders and enough high average order value.
                                                                                                                  //converts to list to be sorted descendingly
// Renders analysis results as plain-text blocks for the console and docs/output.txt.
object ReportFormatter:

  //Generic helper for rendering any report section. [A] means it works on any row type.
  // first(...): takes the title and rows, Second (...): takes a reader function that knows how to turn one row into a display string
  def section[A](title: String, rows: List[A])(render: A => String): String =
    val banner = s"=== $title ==="                        //prints a header "=== Q1: ... ==="
    (banner :: rows.map(render)).mkString("\n")           //rows.map(reader) turns every row into its formatted string, banner :: prepends the header onto the list
  
  //formats a number currency. (,) uses thousands separator and (2f) is two decimal places. 
  def money(amount: Double): String = f"$amount%,.2f GBP"

//Load dataset 2 (UK Online Retail) and prints the three Questions
@main def run(): Unit =
  CsvLoader.load("data.csv") match                                                       //CsvLoader returns Either[String, Vector[Transaction]]
    case Left(error) =>
      println(s"Failed to load dataset: $error")                                         // loading failed -> report and stop
    case Right(txs) =>
      println(s"Loaded ${txs.size} transactions from src/main/resources/data.csv")       //prints how many transactions was loaded
      println()

      //Q1: top 5 countries by revenue
      val q1 = Analytics.topCountriesByRevenue(txs, 5)
      println(ReportFormatter.section("Q1: Top 5 countries by total revenue", q1)(r =>
        f"${r.rank}%2d. ${r.country}%-16s ${ReportFormatter.money(r.revenue)}%18s"))     //%2d: right aligned in 2-char field, %-16s: left aligned in 16-char field, %18s: right aligned in 18-char field
      println()

      //Q2: Total revenue per invoice month
      val q2 = Analytics.revenueByMonth(txs)
      println(ReportFormatter.section("Q2: Total revenue per invoice month", q2)(r =>
        f"${r.month}  ${ReportFormatter.money(r.revenue)}%18s"))                        //%18s: right aligned in 18-char field
      println()

      //Q3: loyal high customers (order count >=20 AND order avg >= 750)
      val q3 = Analytics.loyalHighSpenders(txs, minOrders = 20, minAvgOrder = 750.0)
      println(ReportFormatter.section("Q3: Customers with >= 20 orders AND avg order value >= 750 GBP", q3)(r =>
        f"customer ${r.customerId}%-7s orders=${r.orderCount}%3d  total=${ReportFormatter.money(r.totalSpend)}%15s  avg/order=${ReportFormatter.money(r.avgOrderValue)}%14s"))
