(ns stch.routing
  "DSL for routing Ring requests."
  (:use [stch.response]
        [stch.schema]
        [ring.util.response :only [file-response]])
  (:require [clojure.string :as string]
            [stch.zipper :as z])
  (:import java.text.SimpleDateFormat
           java.util.TimeZone))

(def Request {:uri String
              :request-method Keyword
              Keyword Any})

(def ^:dynamic *router*)

(defn' parse-int :- Int [n :- String]
  (Integer/parseInt n))

(defn' parse-date :- Date
  "Parse a date with format yyyy-MM-dd. Returns
  a Date instant with timezone set to GMT."
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

(defprotocol IDispatch
  "Dispatch a request."
  (-pred [this p f]
    "If (pred path-segment) returns truthy then,
    call f passing the result of the above.")
  (-domain [this d f]
    "If the server-name key matches the domain,
    call f.")
  (-index [this f]
    "If there are no path segments, call f.")
  (-path [this segment f]
    "If the next path segment matches segment,
    call f.")
  (-method [this meth f]
    "If the request-method key matches meth,
    call f.")
  (-param [this parser f]
    "If the next path segment can be matched according
    to regex pattern, use the corresponding parser and
    pass the result to f.")
  (-static [this segments opts]
    "If the next path segment matches a value in the
    segments set and the request method is get,
    return a file response corresponding to the
    requested file.")
  (-not-traversed [this f]
    "If the current path level has not been traversed
    previously, call f.")
  (-truncate [this f]
    "Truncate any remaining path segments and call f.
    If a response is not created inside truncate
    (for example, by means of a call to method),
    revert the zipper to it's previous state.")
  (-terminate [this resp]
    "Terminate and respond with resp immediately.")
  (-guard [this check] [this check msg]
    "If check is falsey return immediately with a
    forbidden error, and optional message.")
  (-zipper [this]
    "Inspect the current state of the zipper. Useful
    for debugging."))

(defmacro pred [p bindings & body]
  `(-pred *router* ~p (fn ~bindings ~@body)))

(defmacro domain [d & body]
  `(-domain *router* ~d (fn [] ~@body)))

(defmacro index [& body]
  `(-index *router* (fn [] ~@body)))

(defmacro path [segment & body]
  `(-path *router* ~segment (fn [] ~@body)))

(defmacro method [method & body]
  `(-method *router* ~method (fn [] ~@body)))

(defmacro param [parser bindings & body]
  `(-param *router* ~parser (fn ~bindings ~@body)))

(defmacro static [segments opts]
  `(-static *router* ~segments ~opts))

(defmacro not-traversed [& body]
  `(-not-traversed *router* (fn [] ~@body)))

(defmacro truncate [& body]
  `(-truncate *router* (fn [] ~@body)))

(defmacro terminate [resp]
  `(-terminate *router* ~resp))

(defmacro guard
  ([check]
   `(-guard *router* ~check))
  ([check msg]
   `(-guard *router* ~check ~msg)))

(defmacro zipper []
  `(-zipper *router*))

(defprotocol IRequest
  (-request [this]
    "Returns the request map.")
  (-url [this]
    "Returns the request url.")
  (-params [this]
    "Returns the params map (if present).")
  (-lookup-param [this param]
    "Returns the specified param.")
  (-headers [this]
    "Returns a map of the request headers.")
  (-lookup-header [this header]
    "Returns the specified header.")
  (-body [this]
    "Returns the request body."))

(defmacro request []
  `(-request *router*))

(defmacro url []
  `(-url *router*))

(defmacro params []
  `(-params *router*))

(defmacro lookup-param [param]
  `(-lookup-param *router* ~param))

(defmacro headers []
  `(-headers *router*))

(defmacro lookup-header [header]
  `(-lookup-header *router* ~header))

(defmacro body []
  `(-body *router*))

(defrecord' Router
  [req :- Map
   req-path :- Atom
   req-method :- String
   resp :- Atom]

  IDispatch
  (-pred [this p f]
    (when (and (z/not-end? @req-path)
               (empty-resp? @resp))
      (let [segment (z/preview @req-path)
            pred-result (p segment)]
        (when pred-result
          (swap! req-path z/next)
          (let [v (f pred-result)]
            (if (and (z/end? @req-path)
                     (empty-resp? @resp))
              (reset! resp v)
              (do
                (swap! req-path z/prev)
                v)))))))
  (-domain [this d f]
    (when (= (:server-name req) d)
      (f)))
  (-index [this f]
    (when (and (z/empty? @req-path)
               (empty-resp? @resp))
      (let [v (f)]
        (if (empty-resp? @resp)
          (reset! resp v)
          v))))
  (-path [this segment f]
    (when (and (z/not-end? @req-path)
               (empty-resp? @resp)
               (= (z/preview @req-path) segment))
      (swap! req-path z/next)
      (let [v (f)]
        (if (and (z/end? @req-path) (empty-resp? @resp))
          (reset! resp v)
          (do
            (swap! req-path z/prev)
            v)))))
  (-method [this meth f]
    (when (and (z/end? @req-path) (empty-resp? @resp))
      (let [meth (string/lower-case (name meth))]
        (when (or (= meth "any")
                  (= meth req-method))
          (let [v (f)]
            (if (empty-resp? @resp)
              (reset! resp v)
              v))))))
  (-param [this p-type f]
    (when (and (z/not-end? @req-path) (empty-resp? @resp))
      (let [[pattern parser]
            (if (keyword? p-type)
              (parsers p-type)
              p-type)
            segment (z/preview @req-path)
            matches (re-matches pattern segment)]
        (when matches
          (swap! req-path z/next)
          (let [v (f (parser matches))]
            (if (and (z/end? @req-path) (empty-resp? @resp))
              (reset! resp v)
              (do
                (swap! req-path z/prev)
                v)))))))
  (-static [this segments opts]
    (when (and (z/not-end? @req-path)
               (empty-resp? @resp)
               (= req-method "get")
               (segments (z/preview @req-path)))
      (when-let [r (file-response (:uri req) opts)]
        (swap! req-path z/end)
        (reset! resp (map->Response r)))))
  (-not-traversed [this f]
    (when-not (z/next-traversed? @req-path)
      (f)))
  (-truncate [this f]
    (let [prev-req-path @req-path]
      (swap! req-path z/truncate)
      (let [v (f)]
        (when (empty-resp? @resp)
          (reset! req-path prev-req-path))
        v)))
  (-terminate [this response]
    (reset! resp response))
  (-guard [this check]
    (when-not check
      (reset! resp (forbidden))))
  (-guard [this check msg]
    (when-not check
      (reset! resp (text-response 403 msg))))
  (-zipper [this] @req-path)

  IRequest
  (-request [this] req)
  (-url [this] (:uri req))
  (-params [this] (:params req))
  (-lookup-param [this param]
    (get-in req [:params param]))
  (-headers [this] (:headers req))
  (-lookup-header [this header]
    (get-in req [:headers header]))
  (-body [this] (:body req)))

(defn' ^:private split-path :- [String]
  "Split the request uri on /, removing any nils."
  [uri :- String]
  (->> (clojure.string/split uri #"/")
       (remove clojure.string/blank?)))

(defn' init-router :- Router
  [req :- Request]
  (Router. req
           (-> req :uri split-path z/->zip atom)
           (-> req :request-method name)
           (atom (->EmptyResponse))))

(defmacro route
  [& body]
  "Entrypoint for routing. Always returns a response."
  `(fn [req#]
     (binding [*router* (init-router req#)]
       ~@body
       (-> *router* :resp deref respond))))

(defmacro defroute
  "Define a route."
  [name & body]
  `(def ~name (route ~@body)))

(defmacro route'
  [& body]
  "Alternate entrypoint for routing.
  Use with routes."
  `(fn [req#]
     (binding [*router* (init-router req#)]
       ~@body
       (-> *router* :resp deref))))

(defmacro defroute'
  "Define a route using route'."
  [name & body]
  `(def ~name (route' ~@body)))

(defn' routes :- (Fn Any [Map])
  "Compose multiple routes."
  [& handlers :- [(Fn Any [Map])]]
  (fn [req]
    (-> (some (fn [handler]
                (let [resp (handler req)]
                  (when-not (empty-resp? resp)
                    resp)))
              handlers)
        respond)))





