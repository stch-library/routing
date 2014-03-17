(ns stch.response
  (:require [cheshire.core :as json])
  (:use [ring.util.response :only [response]]
        [stch.schema])
  (:import
   [java.io File InputStream]
   [clojure.lang APersistentMap Sequential IDeref]))

(defrecord' Response
  [status :- Int, headers :- Map, body])

(defrecord EmptyResponse [])

(defn' empty-resp? :- Boolean
  [x :- Any]
  (instance? EmptyResponse x))

(def ^:private code->text
  {100 "Continue"
   101 "Switching Protocols"
   102 "Processing"

   200 "OK"
   201 "Created"
   202 "Accepted"
   203 "Non-Authoritative Information"
   204 "No Content"
   205 "Reset Content"
   206 "Partial Content"
   207 "Multi-Status"
   226 "IM Used"

   300 "Multiple Choices"
   301 "Moved Permanently"
   302 "Found"
   303 "See Other"
   304 "Not Modified"
   305 "Use Proxy"
   306 "Reserved"
   307 "Temporary Redirect"

   400 "Bad Request"
   401 "Unauthorized"
   402 "Payment Required"
   403 "Forbidden"
   404 "Not Found"
   405 "Method Not Allowed"
   406 "Not Acceptable"
   407 "Proxy Authentication Required"
   408 "Request Timeout"
   409 "Conflict"
   410 "Gone"
   411 "Length Required"
   412 "Precondition Failed"
   413 "Request Entity Too Large"
   414 "Request-URI Too Long"
   415 "Unsupported Media Type"
   416 "Requested Range Not Satisfiable"
   417 "Expectation Failed"
   422 "Unprocessable Entity"
   423 "Locked"
   424 "Failed Dependency"
   426 "Upgrade Required"
   428 "Precondition Required"
   429 "Too Many Requests"
   431 "Request Header Fields Too Large"

   500 "Internal Server Error"
   501 "Not Implemented"
   502 "Bad Gateway"
   503 "Service Unavailable"
   504 "Gateway Timeout"
   505 "HTTP Version Not Supported"
   506 "Variant Also Negotiates"
   507 "Insufficient Storage"
   510 "Not Extended"
   511 "Network Authentication Required"})

(def ^:private ct-text-html
  {"Content-Type" "text/html"})

(defn' ok :- Response
  ([body]
   (Response. 200 ct-text-html body))
  ([content-type :- String, body]
   (Response. 200 {"Content-Type" content-type} body)))

(defn' ->json :- Response [resp]
  (ok "application/json" (json/encode resp)))

(defn' ->edn :- Response [resp]
  (ok "application/edn" (pr-str resp)))

(defn' redirect :- Response
  [url :- String]
  (Response. 302 {"Location" url} ""))

(defn' text-response :- Response
  [code :- Int, text :- String]
  (Response. code ct-text-html text))

(defn' trivial-response :- Response
  [code :- Int]
  (Response. code ct-text-html (code->text code)))

(defn' bad-request :- Response []
  (trivial-response 400))

(defn' forbidden :- Response []
  (trivial-response 403))

(defn' not-found :- Response []
  (trivial-response 404))

(defn' method-not-allowed :- Response []
  (trivial-response 405))

(def ^:dynamic coll-formatter ->json)

(defmacro with-edn-formatting
  [& body]
  `(binding [coll-formatter ->edn]
     ~@body))

(defprotocol IResponse
  (respond [resp]))

(extend-protocol IResponse
  EmptyResponse
  (respond [_] (not-found))
  Response
  (respond [resp] resp)
  nil
  (respond [_] (trivial-response 204))
  String
  (respond [resp] (ok resp))
  APersistentMap
  (respond [resp] (coll-formatter resp))
  Sequential
  (respond [resp] (coll-formatter resp))
  IDeref
  (respond [ref] (respond (deref ref)))
  File
  (respond [file] (response file))
  InputStream
  (respond [stream] (response stream))
  Object
  (respond [obj] (ok (pr-str obj))))







