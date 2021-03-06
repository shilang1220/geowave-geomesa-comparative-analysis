package com.azavea.ca.server

import com.azavea.ca.core._
import com.azavea.ca.server.results._
import com.azavea.ca.server.geomesa.connection.GeoMesaConnection
import com.azavea.ca.server.geowave.connection.GeoWaveConnection
import com.azavea.ca.server.geowave.GeoWaveQuerier

import akka.http.scaladsl.server.Directives._
import de.heikoseeberger.akkahttpcirce._
import io.circe.generic.auto._
import geotrellis.vector._
import org.geotools.data._
import org.geotools.filter.text.ecql.ECQL
import org.opengis.filter.Filter

import scala.concurrent.Future

/**
  *  @param tableName Table container feature, without namesapce
  *  @param featureName Feature name to query
  */
object CitiesQueries
    extends BaseService
    with CAQueryUtils
    with CirceSupport
    with AkkaSystem.LoggerExecutor {

  val gwTableName = "geowave.gdelt"
  val gwFeatureTypeName = "gdelt-event"

  val gmTableName = "geomesa.gdelt"
  val gmFeatureTypeName = "gdelt-event"

  val geomField = "the_geom"
  val timeField = "day"

  def routes =
  pathPrefix("cities") {
    pathPrefix("ping") {
      pathEndOrSingleSlash {
        get {
          complete { Future { "pong39" } } }
      }
    } ~
    pathPrefix("reset") {
      pathEndOrSingleSlash {
        get {
          complete { Future { resetDataStores() ; "done" } } }
      }
    } ~
    pathPrefix("spatiotemporal" / Segment) { testContext =>
      val context = testContext.toUpperCase
      pathPrefix("in-france-region-bbox-7-days") {
        val queryName = s"CITIES-$context-IN-FRANCE-REGION-BBOX-7-DAYS"

        pathEndOrSingleSlash {
          get {
            parameters('test ?, 'loose ?, 'wOrm ? "wm") { (isTestOpt, isLooseOpt, waveOrMesa) =>
              complete {
                Future {
                  val tq = TimeQuery("2001-01-01T00:00:00", "2001-01-07T00:00:00")
                  val query = ECQL.toFilter(CQLUtils.toBBOXquery(geomField, France.regions.head.envelope) + " AND " + tq.toCQL(timeField))
                  captureAndSave(queryName, isTestOpt, isLooseOpt, waveOrMesa, query)
                }
              }
            }
          }
        }
      } ~
      pathPrefix("in-france-bbox-six-months") {
        val queryName = s"CITIES-$context-IN-FRANCE-BBOX-SIX-MONTHS"

        pathEndOrSingleSlash {
          get {
            parameters('year ? "all", 'test ?, 'loose ?, 'wOrm ? "wm") { (year, isTestOpt, isLooseOpt, waveOrMesa) =>
              complete {
                Future {
                  val timeQueries = {
                    val years: Seq[Int] =
                      if(year != "all") {
                        Seq(year.toInt)
                      } else {
                        (1980 to 2015)
                      }
                    (for(y <- years) yield {
                      Seq(
                        (s"$y-firsthalf", TimeQuery(s"$y-01-01T00:00:00", s"$y-06-01T00:00:00")),
                        (s"$y-lasthalf", TimeQuery(s"$y-06-01T00:00:00", s"${y+1}-01-01T00:00:00"))
                      )
                    }).flatten
                  }

                  (for((suffix, tq) <- timeQueries) yield {
                    val query = ECQL.toFilter(France.CQL.inBoundingBox + " AND " + tq.toCQL(timeField))
                    captureAndSave(s"${queryName}-${suffix}", isTestOpt, isLooseOpt, waveOrMesa, query)
                  }).toArray
                }
              }
            }
          }
        }
      } ~
      pathPrefix("in-france-six-months") {
        val queryName = s"CITIES-$context-IN-FRANCE-SIX-MONTHS"

        pathEndOrSingleSlash {
          get {
            parameters('year ? "all", 'test ?, 'loose ?, 'wOrm ? "wm") { (year, isTestOpt, isLooseOpt, waveOrMesa) =>
              complete {
                Future {
                  val timeQueries = {
                    val years: Seq[Int] =
                      if(year != "all") {
                        Seq(year.toInt)
                      } else {
                        (1980 to 2015)
                      }
                    (for(y <- years) yield {
                      Seq(
                        (s"$y-firsthalf", TimeQuery(s"$y-01-01T00:00:00", s"$y-06-01T00:00:00")),
                        (s"$y-lasthalf", TimeQuery(s"$y-06-01T00:00:00", s"${y+1}-01-01T00:00:00"))
                      )
                    }).flatten
                  }

                  (for((suffix, tq) <- timeQueries) yield {
                    val query = ECQL.toFilter(France.CQL.inFrance + " AND " + tq.toCQL(timeField))
                    captureAndSave(s"${queryName}-${suffix}", isTestOpt, isLooseOpt, waveOrMesa, query)
                  }).toArray
                }
              }
            }
          }
        }
      } ~
      pathPrefix("in-france-bbox-one-month") {
        val queryName = s"CITIES-$context-IN-FRANCE-BBOX-ONE-MONTH"

        pathEndOrSingleSlash {
          get {
            parameters('year ? "all", 'test ?, 'loose ?, 'wOrm ? "wm") { (year, isTestOpt, isLooseOpt, waveOrMesa) =>
              complete {
                Future {
                  val timeQueries = {
                    val years: Seq[Int] =
                      if(year != "all") {
                        Seq(year.toInt)
                      } else {
                        (1980 to 2015)
                      }
                    (for(y <- years) yield {
                      Seq(
                        (s"$y-JAN", TimeQuery(s"$y-01-01T00:00:00", s"$y-02-01T00:00:00")),
                        (s"$y-FEB", TimeQuery(s"$y-02-01T00:00:00", s"$y-03-01T00:00:00")),
                        (s"$y-MAR", TimeQuery(s"$y-03-01T00:00:00", s"$y-04-01T00:00:00")),
                        (s"$y-APR", TimeQuery(s"$y-04-01T00:00:00", s"$y-05-01T00:00:00")),
                        (s"$y-MAY", TimeQuery(s"$y-05-01T00:00:00", s"$y-06-01T00:00:00")),
                        (s"$y-JUN", TimeQuery(s"$y-06-01T00:00:00", s"$y-07-01T00:00:00")),
                        (s"$y-JUL", TimeQuery(s"$y-07-01T00:00:00", s"$y-08-01T00:00:00")),
                        (s"$y-AUG", TimeQuery(s"$y-08-01T00:00:00", s"$y-09-01T00:00:00")),
                        (s"$y-SEP", TimeQuery(s"$y-09-01T00:00:00", s"$y-10-01T00:00:00")),
                        (s"$y-OCT", TimeQuery(s"$y-10-01T00:00:00", s"$y-11-01T00:00:00")),
                        (s"$y-NOV", TimeQuery(s"$y-11-01T00:00:00", s"$y-12-01T00:00:00")),
                        (s"$y-DEC", TimeQuery(s"$y-12-01T00:00:00", s"${y+1}-01-01T00:00:00"))
                      )
                    }).flatten
                  }

                  (for((suffix, tq) <- timeQueries) yield {
                    val query = ECQL.toFilter(France.CQL.inBoundingBox + " AND " + tq.toCQL(timeField))
                    captureAndSave(s"${queryName}-${suffix}", isTestOpt, isLooseOpt, waveOrMesa, query)
                  }).toArray
                }
              }
            }
          }
        }
      } ~
      pathPrefix("in-france-one-month") {
        val queryName = s"CITIES-$context-IN-FRANCE-ONE-MONTH"

        pathEndOrSingleSlash {
          get {
            parameters('year ? "all", 'test ?, 'loose ?, 'wOrm ? "wm") { (year, isTestOpt, isLooseOpt, waveOrMesa) =>
              val isTest = checkIfIsTest(isTestOpt)
              complete {
                Future {
                  val timeQueries = {
                    val years: Seq[Int] =
                      if(year != "all") {
                        Seq(year.toInt)
                      } else {
                        (1980 to 2015)
                      }
                    (for(y <- years) yield {
                      Seq(
                        (s"$y-JAN", TimeQuery(s"$y-01-01T00:00:00", s"$y-02-01T00:00:00")),
                        (s"$y-FEB", TimeQuery(s"$y-02-01T00:00:00", s"$y-03-01T00:00:00")),
                        (s"$y-MAR", TimeQuery(s"$y-03-01T00:00:00", s"$y-04-01T00:00:00")),
                        (s"$y-APR", TimeQuery(s"$y-04-01T00:00:00", s"$y-05-01T00:00:00")),
                        (s"$y-MAY", TimeQuery(s"$y-05-01T00:00:00", s"$y-06-01T00:00:00")),
                        (s"$y-JUN", TimeQuery(s"$y-06-01T00:00:00", s"$y-07-01T00:00:00")),
                        (s"$y-JUL", TimeQuery(s"$y-07-01T00:00:00", s"$y-08-01T00:00:00")),
                        (s"$y-AUG", TimeQuery(s"$y-08-01T00:00:00", s"$y-09-01T00:00:00")),
                        (s"$y-SEP", TimeQuery(s"$y-09-01T00:00:00", s"$y-10-01T00:00:00")),
                        (s"$y-OCT", TimeQuery(s"$y-10-01T00:00:00", s"$y-11-01T00:00:00")),
                        (s"$y-NOV", TimeQuery(s"$y-11-01T00:00:00", s"$y-12-01T00:00:00")),
                        (s"$y-DEC", TimeQuery(s"$y-12-01T00:00:00", s"${y+1}-01-01T00:00:00"))
                      )
                    }).flatten
                  }

                  (for((suffix, tq) <- timeQueries) yield {
                    val query = ECQL.toFilter(France.CQL.inFrance + " AND " + tq.toCQL(timeField))
                    captureAndSave(s"${queryName}-${suffix}", isTestOpt, isLooseOpt, waveOrMesa, query)
                  }).toArray
                }
              }
            }
          }
        }
      } ~
      pathPrefix("in-france-regions-two-years") {
        val queryName = s"CITIES-$context-IN-FRANCE-REGIONS-TWO-YEARS"

        pathEndOrSingleSlash {
          get {
            parameters('year, 'region ? "all", 'test ?, 'loose ?, 'wOrm ? "wm") { (year, region, isTestOpt, isLooseOpt, waveOrMesa) =>
              complete {
                Future {
                  val timeQueries = {
                    Seq(
                      (year, TimeQuery(s"${year}-01-04T00:00:00", s"${year.toInt+2}-01-01T00:00:00"))
                    )
                  }

                  val regions: Seq[(String, MultiPolygon)] =
                    if(region != "all") {
                      Seq((region, France.regionsByName(region)))
                    } else {
                      France.regionsByName.toSeq
                    }

                  (for((timeSuffix, tq) <- timeQueries;
                        (regionName, geom) <- regions) yield {
                    val suffix = s"$timeSuffix-$regionName"
                    val query = ECQL.toFilter(CQLUtils.intersects(geomField, geom) + " AND " + tq.toCQL(timeField))

                   captureAndSave(s"${queryName}-${suffix}", isTestOpt, isLooseOpt, waveOrMesa, query)

                   }).toArray
                }
              }
            }
          }
        }
      } ~
      pathPrefix("in-france-regions-one-year") {
        val queryName = s"CITIES-$context-IN-FRANCE-REGIONS-ONE-YEAR"

        pathEndOrSingleSlash {
          get {
            parameters('year, 'region ? "all", 'test ?, 'loose ?, 'wOrm ? "wm") { (year, region, isTestOpt, isLooseOpt, waveOrMesa) =>
              complete {
                Future {
                  val timeQueries =
                    Seq(
                      (s"$year", TimeQuery(s"${year}-01-04T00:00:00", s"${year.toInt+1}-01-01T00:00:00"))
                    )

                  val regions: Seq[(String, MultiPolygon)] =
                    if(region != "all") {
                      Seq((region, France.regionsByName(region)))
                    } else {
                      France.regionsByName.toSeq
                    }

                  (for((timeSuffix, tq) <- timeQueries;
                        (regionName, geom) <- regions) yield {
                    val suffix = s"$timeSuffix-$regionName"
                    val query = ECQL.toFilter(CQLUtils.intersects(geomField, geom) + " AND " + tq.toCQL(timeField))
                    captureAndSave(s"${queryName}-${suffix}", isTestOpt, isLooseOpt, waveOrMesa, query)
                  }).toArray
                }
              }
            }
          }
        }
      } ~
      pathPrefix("in-france-regions-ten-months") {
        val queryName = s"CITIES-$context-IN-FRANCE-REGIONS-TEN-MONTHS"

        pathEndOrSingleSlash {
          get {
            parameters('year, 'region ? "all", 'test ?, 'loose ?, 'wOrm ? "wm") { (year, region, isTestOpt, isLooseOpt, waveOrMesa) =>
              complete {
                Future {
                  val timeQueries =
                    Seq(
                      (s"${year}", TimeQuery(s"${year}-01-04T00:00:00", s"${year}-11-01T00:00:00"))
                    )

                  val regions: Seq[(String, MultiPolygon)] =
                    if(region != "all") {
                      Seq((region, France.regionsByName(region)))
                    } else {
                      France.regionsByName.toSeq :+ ("France", France.geom)
                    }

                  (for((timeSuffix, tq) <- timeQueries;
                        (regionName, geom) <- regions) yield {
                    val suffix = s"$timeSuffix-$regionName"
                    val query = ECQL.toFilter(CQLUtils.intersects(geomField, geom) + " AND " + tq.toCQL(timeField))
                    captureAndSave(s"${queryName}-${suffix}", isTestOpt, isLooseOpt, waveOrMesa, query)
                  }).toArray

                }
              }
            }
          }
        }
      } ~
      pathPrefix("in-france-regions-six-months") {
        val queryName = s"CITIES-$context-IN-FRANCE-REGIONS-SIX-MONTHS"

        pathEndOrSingleSlash {
          get {
            parameters('year, 'region ? "all", 'test ?, 'loose ?, 'wOrm ? "wm") { (year, region, isTestOpt, isLooseOpt, waveOrMesa) =>
              complete {
                Future {
                  val timeQueries =
                    Seq(
                      (s"${year}-summerhalf", TimeQuery(s"${year}-01-04T00:00:00", s"${year}-10-01T00:00:00")),
                      (s"${year}-winterhalf", TimeQuery(s"${year}-10-01T00:00:00", s"${year.toInt+1}-04-01T00:00:00"))
                    )

                  val regions: Seq[(String, MultiPolygon)] =
                    if(region != "all") {
                      Seq((region, France.regionsByName(region)))
                    } else {
                      France.regionsByName.toSeq
                    }

                  (for((timeSuffix, tq) <- timeQueries;
                        (regionName, geom) <- regions) yield {
                    val suffix = s"$timeSuffix-$regionName"
                    val query = ECQL.toFilter(CQLUtils.intersects(geomField, geom) + " AND " + tq.toCQL(timeField))
                    captureAndSave(s"${queryName}-${suffix}", isTestOpt, isLooseOpt, waveOrMesa, query)
                  }).toArray
                }
              }
            }
          }
        }
      } ~
      pathPrefix("in-france-regions-three-months") {
        val queryName = s"CITIES-$context-IN-FRANCE-REGIONS-THREE-MONTHS"

        pathEndOrSingleSlash {
          get {
            parameters('year, 'region ? "all", 'test ?, 'loose ?, 'wOrm ? "wm") { (year, region, isTestOpt, isLooseOpt, waveOrMesa) =>
              complete {
                Future {
                  val timeQueries =
                    Seq(
                      (s"${year}-quarter1", TimeQuery(s"${year}-01-01T00:00:00", s"${year}-04-01T00:00:00")),
                      (s"${year}-quarter2", TimeQuery(s"${year}-04-01T00:00:00", s"${year}-07-01T00:00:00")),
                      (s"${year}-quarter3", TimeQuery(s"${year}-07-01T00:00:00", s"${year}-10-01T00:00:00"))// ,
                      // (s"${year}-quarter4", TimeQuery(s"${year}-10-01T00:00:00", s"${year.toInt+1}-01-01T00:00:00")) // Taking out because it's catastrophic to geowave
                    )

                  val regions: Seq[(String, MultiPolygon)] =
                    if(region != "all") {
                      Seq((region, France.regionsByName(region)))
                    } else {
                      France.regionsByName.toSeq
                    }

                  (for((timeSuffix, tq) <- timeQueries;
                        (regionName, geom) <- regions) yield {
                    val suffix = s"$timeSuffix-$regionName"
                    val query = ECQL.toFilter(CQLUtils.intersects(geomField, geom) + " AND " + tq.toCQL(timeField))
                    captureAndSave(s"${queryName}-${suffix}", isTestOpt, isLooseOpt, waveOrMesa, query)
                  }).toArray
                }
              }
            }
          }
        }
      } ~
      pathPrefix("in-city-buffers-fourteen-months") {
        val queryName = s"CITIES-$context-IN-CITY-BUFFERS-FOURTEEN-MONTHS"

        pathEndOrSingleSlash {
          get {
            parameters('year, 'city, 'test ?, 'loose ?, 'wOrm ? "wm") { (year, city, isTestOpt, isLooseOpt, waveOrMesa) =>
              complete {
                Future {
                  val timeQueries =
                    Seq(
                      (s"${year}", TimeQuery(s"${year}-01-01T00:00:00", s"${year.toInt+1}-03-01T00:00:00"))
                    )

                  val regions: Seq[(String, Polygon)] =
                    Cities.cityBuffers(city).toSeq

                  (for((timeSuffix, tq) <- timeQueries;
                        (regionName, geom) <- regions) yield {
                    val suffix = s"$timeSuffix-$city-$regionName"
                    val query = ECQL.toFilter(CQLUtils.intersects(geomField, geom) + " AND " + tq.toCQL(timeField))
                    captureAndSave(s"${queryName}-${suffix}", isTestOpt, isLooseOpt, waveOrMesa, query)
                  }).toArray

                }
              }
            }
          }
        }
      } ~
      pathPrefix("in-city-buffers-ten-months") {
        val queryName = s"CITIES-$context-IN-CITY-BUFFERS-TEN-MONTHS"

        pathEndOrSingleSlash {
          get {
            parameters('year, 'city, 'test ?, 'loose ?, 'wOrm ? "wm") { (year, city, isTestOpt, isLooseOpt, waveOrMesa) =>
              complete {
                Future {
                  val timeQueries =
                    Seq(
                      (s"${year}", TimeQuery(s"${year}-01-01T00:00:00", s"${year}-11-01T00:00:00"))
                    )

                  val regions: Seq[(String, Polygon)] =
                    Cities.cityBuffers(city).toSeq

                  (for((timeSuffix, tq) <- timeQueries;
                        (regionName, geom) <- regions) yield {
                    val suffix = s"$timeSuffix-$city-$regionName"
                    val query = ECQL.toFilter(CQLUtils.intersects(geomField, geom) + " AND " + tq.toCQL(timeField))
                    captureAndSave(s"${queryName}-${suffix}", isTestOpt, isLooseOpt, waveOrMesa, query)
                  }).toArray
                }
              }
            }
          }
        }
      } ~
      pathPrefix("in-city-buffers-six-months") {
        val queryName = s"CITIES-$context-IN-CITY-BUFFERS-six-MONTHS"

        pathEndOrSingleSlash {
          get {
            parameters('year, 'city, 'test ?, 'loose ?, 'wOrm ? "wm") { (year, city, isTestOpt, isLooseOpt, waveOrMesa) =>
              complete {
                Future {
                  val timeQueries =
                    Seq(
                      (s"${year}", TimeQuery(s"${year}-01-01T00:00:00", s"${year}-07-01T00:00:00"))
                    )

                  val regions: Seq[(String, Polygon)] =
                    Cities.cityBuffers(city).toSeq

                  (for((timeSuffix, tq) <- timeQueries;
                        (regionName, geom) <- regions) yield {
                    val suffix = s"$timeSuffix-$city-$regionName"
                    val query = ECQL.toFilter(CQLUtils.intersects(geomField, geom) + " AND " + tq.toCQL(timeField))
                    captureAndSave(s"${queryName}-${suffix}", isTestOpt, isLooseOpt, waveOrMesa, query)
                  }).toArray

                }
              }
            }
          }
        }
      } ~
      pathPrefix("in-city-buffers-two-months") {
        val queryName = s"CITIES-$context-IN-CITY-BUFFERS-TWO-MONTHS"

        pathEndOrSingleSlash {
          get {
            parameters('year, 'city, 'test ?, 'loose ?, 'wOrm ? "wm") { (year, city, isTestOpt, isLooseOpt, waveOrMesa) =>
              complete {
                Future {
                  val timeQueries =
                    Seq(
                      (s"${year}", TimeQuery(s"${year}-01-01T00:00:00", s"${year}-3-01T00:00:00"))
                    )

                  val regions: Seq[(String, Polygon)] =
                    Cities.cityBuffers(city).toSeq

                  (for((timeSuffix, tq) <- timeQueries;
                        (regionName, geom) <- regions) yield {
                    val suffix = s"$timeSuffix-$city-$regionName"
                    val query = ECQL.toFilter(CQLUtils.intersects(geomField, geom) + " AND " + tq.toCQL(timeField))
                    captureAndSave(s"${queryName}-${suffix}", isTestOpt, isLooseOpt, waveOrMesa, query)
                  }).toArray

                }
              }
            }
          }
        }
      } ~
      pathPrefix("in-city-buffers-two-weeks") {
        val queryName = s"CITIES-$context-IN-CITY-BUFFERS-TWO-WEEKS"

        def checkIfIsTest(q: Option[String]) = q.map { x => if(x == "false") false else true }.getOrElse(true)
        pathEndOrSingleSlash {
          get {
            parameters('year, 'city, 'test ?, 'loose ?, 'wOrm ? "wm") { (year, city, isTestOpt, isLooseOpt, waveOrMesa) =>
              val isTest = checkIfIsTest(isTestOpt)
              complete {
                Future {
                  val timeQueries =
                    Seq(
                      (s"${year}", TimeQuery(s"${year}-05-14T00:00:00", s"${year}-5-29T00:00:00"))
                    )

                  val regions: Seq[(String, Polygon)] =
                    Cities.cityBuffers(city).toSeq

                  (for((timeSuffix, tq) <- timeQueries;
                        (regionName, geom) <- regions) yield {
                    val suffix = s"$timeSuffix-$city-$regionName"
                    val query = ECQL.toFilter(CQLUtils.intersects(geomField, geom) + " AND " + tq.toCQL(timeField))
                    captureAndSave(s"${queryName}-${suffix}", isTestOpt, isLooseOpt, waveOrMesa, query)
                  }).toArray

                }
              }
            }
          }
        }
      } ~
      pathPrefix("in-city-buffers-six-days") {
        val queryName = s"CITIES-$context-IN-CITY-BUFFERS-SIX-DAYS"

        pathEndOrSingleSlash {
          get {
            parameters('year, 'city, 'test ?, 'loose ?, 'wOrm ? "wm") { (year, city, isTestOpt, isLooseOpt, waveOrMesa) =>
              complete {
                Future {
                  val timeQueries =
                    Seq(
                      (s"${year}", TimeQuery(s"${year}-05-01T00:00:00", s"${year}-5-07T00:00:00"))
                    )

                  val regions: Seq[(String, Polygon)] =
                    Cities.cityBuffers(city).toSeq

                  (for((timeSuffix, tq) <- timeQueries;
                        (regionName, geom) <- regions) yield {
                    val suffix = s"$timeSuffix-$city-$regionName"
                    val query = ECQL.toFilter(CQLUtils.intersects(geomField, geom) + " AND " + tq.toCQL(timeField))
                    captureAndSave(s"${queryName}-${suffix}", isTestOpt, isLooseOpt, waveOrMesa, query)
                  }).toArray
                }
              }
            }
          }
        }
      } ~
      pathPrefix("in-south-america-countries-three-weeks") {
        val queryName = s"CITIES-$context-IN-SOUTH-AMERICA-COUNTRIES-THREE-WEEKS"

        pathEndOrSingleSlash {
          get {
            parameters('year, 'country ? "all", 'test ?, 'loose ?, 'wOrm ? "wm") { (year, country, isTestOpt, isLooseOpt, waveOrMesa) =>
              complete {
                Future {
                    val timeQueries =
                      Seq(
                        (s"${year}-JAN", TimeQuery(s"${year}-01-01T00:00:00", s"${year}-01-22T00:00:00")),
                        (s"${year}-FEB", TimeQuery(s"${year}-02-01T00:00:00", s"${year}-02-22T00:00:00")),
                        (s"${year}-MAR", TimeQuery(s"${year}-03-01T00:00:00", s"${year}-03-22T00:00:00")),
                        (s"${year}-APR", TimeQuery(s"${year}-04-01T00:00:00", s"${year}-04-22T00:00:00")),
                        (s"${year}-MAY", TimeQuery(s"${year}-05-01T00:00:00", s"${year}-05-22T00:00:00")),
                        (s"${year}-JUN", TimeQuery(s"${year}-06-01T00:00:00", s"${year}-06-22T00:00:00")),
                        (s"${year}-JUL", TimeQuery(s"${year}-07-01T00:00:00", s"${year}-07-22T00:00:00")),
                        (s"${year}-AUG", TimeQuery(s"${year}-08-01T00:00:00", s"${year}-08-22T00:00:00")),
                        (s"${year}-SEP", TimeQuery(s"${year}-09-01T00:00:00", s"${year}-09-22T00:00:00")),
                        (s"${year}-OCT", TimeQuery(s"${year}-10-01T00:00:00", s"${year}-10-22T00:00:00")),
                        (s"${year}-NOV", TimeQuery(s"${year}-11-01T00:00:00", s"${year}-11-22T00:00:00")),
                        (s"${year}-DEC", TimeQuery(s"${year}-12-01T00:00:00", s"${year}-12-22T00:00:00"))
                      )

                    val countries: Seq[(String, MultiPolygon)] =
                      if(country != "all") {
                        Seq((country, SouthAmerica.countriesByName(country)))
                      } else {
                        SouthAmerica.countriesByName.toSeq
                      }

                    (for((timeSuffix, tq) <- timeQueries;
                        (countryName, geom) <- countries) yield {
                      val suffix = s"$timeSuffix-$countryName"
                      val query = ECQL.toFilter(CQLUtils.intersects(geomField, geom) + " AND " + tq.toCQL(timeField))
                      captureAndSave(s"${queryName}-${suffix}", isTestOpt, isLooseOpt, waveOrMesa, query)
                    }).toArray
                  }
                }
              }
            }
          }
      }
    }
  }
}
