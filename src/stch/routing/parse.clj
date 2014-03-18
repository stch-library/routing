(ns stch.routing.parse
  (:use stch.schema)
  (:import java.text.SimpleDateFormat
           java.util.TimeZone))

(defn' parse-int :- Int [n :- String]
  (Integer/parseInt n))

(defn' parse-date :- Date
  "Parse a date with format yyyy-MM-dd. Returns
  a Date instant with the timezone set to GMT."
  [date :- String]
  (let [tz (TimeZone/getTimeZone "GMT")
        sdf (SimpleDateFormat. "yyyy-MM-dd")]
    (.setTimeZone sdf tz)
    (.parse sdf date)))

(defn' parse-uuid :- UUID [uuid :- String]
  (java.util.UUID/fromString uuid))

(def parsers
  {:int [#"\d+" parse-int]
   :slug [#"[a-zA-Z0-9_-]+" identity]
   :date [#"[0-9]{4}-[0-9]{2}-[0-9]{2}" parse-date]
   :uuid [#"[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"
           parse-uuid]})
