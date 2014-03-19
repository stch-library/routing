(ns stch.response
  "Generate Ring responses."
  (:require [cheshire.core :as json])
  (:use [ring.util.response :only [response]]
        [stch.schema])
  (:import
   [java.io File InputStream]
   [clojure.lang APersistentMap Sequential
                 IPersistentSet IDeref]))

(defrecord' Response
  [status :- Int
   headers :- Map
   body :- (U String File InputStream)])

(defrecord EmptyResponse [])

(defn' empty-resp? :- Boolean
  "Is the object an instance of EmptyResponse?"
  [x :- Any]
  (instance? EmptyResponse x))

(def code->text
  "Mapping of HTTP status codes to their default
  descriptions."
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

(def ct-text-html
  "Header map with Content-Type set to text/html."
  {"Content-Type" "text/html"})

(def ct-text-plain
  "Header map with Content-Type set to text/plain."
  {"Content-Type" "text/plain"})

(defn' text-response :- Response
  "Returns a plain text response with the given status
  code and body text."
  [code :- Int, text :- String]
  (Response. code ct-text-plain text))

(defn' trivial-response :- Response
  "Returns a plain text response with the given status
  code and the default description for the status code."
  [code :- Int]
  (Response. code ct-text-plain (code->text code)))

(defn' ok :- Response
  "Returns a 200 response."
  ([body :- String]
   (Response. 200 ct-text-html body))
  ([content-type :- String, body :- String]
   (Response. 200 {"Content-Type" content-type} body)))

(defn' redirect :- Response
  "Returns a 302 response."
  [url :- String]
  (Response. 302 {"Location" url} ""))

(defn' bad-request :- Response
  "Returns a 400 response."
  ([]
   (trivial-response 400))
  ([text :- String]
   (text-response 400 text)))

(defn' forbidden :- Response
  "Returns a 403 response."
  ([]
   (trivial-response 403))
  ([text :- String]
   (text-response 403 text)))

(defn' not-found :- Response
  "Returns a 404 response."
  ([]
   (trivial-response 404))
  ([text :- String]
   (text-response 404 text)))

(defn' method-not-allowed :- Response
  "Returns a 405 response."
  ([]
   (trivial-response 405))
  ([text :- String]
   (text-response 405 text)))

(defn' ->json :- Response
  "Returns a JSON encoded response."
  [data]
  (ok "application/json" (json/encode data)))

(defn' ->edn :- Response
  "Returns an EDN encoded response."
  [data]
  (ok "application/edn" (pr-str data)))

(def ^:dynamic *coll-formatter* ->json)

(defmacro with-edn-formatting
  "Format collection responses as EDN."
  [& body]
  `(binding [*coll-formatter* ->edn]
     ~@body))

(defprotocol IResponse
  "Generate type-specific Ring responses."
  (respond [resp] "Returns a Ring response."))

(extend-protocol IResponse
  EmptyResponse
  (respond [_] (not-found))
  Response
  (respond [resp] resp)
  nil
  (respond [_] (text-response 204 ""))
  String
  (respond [resp] (ok resp))
  APersistentMap
  (respond [resp] (*coll-formatter* resp))
  Sequential
  (respond [resp] (*coll-formatter* resp))
  IPersistentSet
  (respond [resp] (*coll-formatter* resp))
  IDeref
  (respond [ref] (respond (deref ref)))
  File
  (respond [file] (response file))
  InputStream
  (respond [stream] (response stream))
  Object
  (respond [obj] (ok (pr-str obj))))







