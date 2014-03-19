(ns stch.routing
  "DSL for routing Ring requests."
  (:use [stch schema response]
        [stch.routing.parse]
        [ring.util.response :only [file-response]])
  (:require [clojure.string :as string]
            [stch.zipper :as z]))

(def RequestMethods
  "Request methods type annotation."
  (Enumerate :get :head :options :put :post :delete))

(def Request
  "Request type annotation."
  {:uri String
   :request-method RequestMethods
   :server-name String
   :scheme (Enumerate :http :https)
   :headers {String String}
   Keyword Any})

(def ^:dynamic *router*)

(defprotocol IDispatch
  "Dispatch a request."
  (-scheme [this s f]
    "If the scheme key matches s, call f.")
  (-domain [this d f]
    "If the server-name key matches d, call f.")
  (-pred [this p f]
    "If (p path-segment) returns truthy then,
    call f passing the result of the above.")
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

(defmacro scheme
  "If the scheme key matches s, evaluate body.

  Use with route or route'."
  [s & body]
  `(-scheme *router* ~s (fn [] ~@body)))

(defmacro domain
  "If the server-name key matches d, evaluate body.

  Use with route or route'."
  [d & body]
  `(-domain *router* ~d (fn [] ~@body)))

(defmacro pred
  "If (p path-segment) returns truthy then,
  evaluate body with bindings set to the value
  returned by (p path-segment).

  Use with route or route'."
  [p bindings & body]
  `(-pred *router* ~p (fn ~bindings ~@body)))

(defmacro index
  "If there are no path segments, evaluate body.

  Use with route or route'."
  [& body]
  `(-index *router* (fn [] ~@body)))

(defmacro path
  "If the next path segment matches segment,
  evaluate body.

  Use with route or route'."
  [segment & body]
  `(-path *router* ~segment (fn [] ~@body)))

(defmacro method
  "If the request-method key matches meth,
   evaluate body.

  Use with route or route'."
  [method & body]
  `(-method *router* ~method (fn [] ~@body)))

(defmacro param
  "If the next path segment can be matched according
  to regex pattern, use the corresponding parser and
  evaluate body with bindings set to parsed value.

  Use with route or route'."
  [parser bindings & body]
  `(-param *router* ~parser (fn ~bindings ~@body)))

(defmacro static
  "If the next path segment matches a value in the
  segments set and the request method is get,
  return a file response corresponding to the
  requested file.

  Use with route or route'."
  [segments opts]
  `(-static *router* ~segments ~opts))

(defmacro not-traversed
  "If the current path level has not been traversed
  previously, evaluate body.

  Use with route or route'."
  [& body]
  `(-not-traversed *router* (fn [] ~@body)))

(defmacro truncate
  "Truncate any remaining path segments and evaluate body.
  If a response is not created inside truncate
  (for example, by means of a call to method),
  revert the zipper to it's previous state.

  Use with route or route'."
  [& body]
  `(-truncate *router* (fn [] ~@body)))

(defmacro terminate
  "Terminate and respond with resp immediately.

  Use with route or route'."
  [resp]
  `(-terminate *router* ~resp))

(defmacro guard
  "If check is falsey return immediately with a
  forbidden error, and optional message.

  Use with route or route'."
  ([check]
   `(-guard *router* ~check))
  ([check msg]
   `(-guard *router* ~check ~msg)))

(defmacro zipper
  "Inspect the current state of the zipper. Useful
  for debugging.

  Use with route or route'."
  []
  `(-zipper *router*))

(defprotocol IRequest
  "Common request accessors."
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

(defmacro request
  "Returns the request map.

  Use with route or route'."
  []
  `(-request *router*))

(defmacro url
  "Returns the request url.

  Use with route or route'."
  []
  `(-url *router*))

(defmacro params
  "Returns the params map (if present).

  Use with route or route'."
  []
  `(-params *router*))

(defmacro lookup-param
  "Returns the specified param.

  Use with route or route'."
  [param]
  `(-lookup-param *router* ~param))

(defmacro headers
  "Returns a map of the request headers.

  Use with route or route'."
  []
  `(-headers *router*))

(defmacro lookup-header
  "Returns the specified header.

  Use with route or route'."
  [header]
  `(-lookup-header *router* ~header))

(defmacro body
  "Returns the request body (if present).

  Use with route or route'."
  []
  `(-body *router*))

(defrecord' Router
  [req :- Map
   req-path :- Atom
   req-method :- String
   resp :- Atom]

  IDispatch
  (-scheme [this s f]
    (when (= (name (:scheme req)) (name s))
      (f)))
  (-domain [this d f]
    (when (= (:server-name req) d)
      (f)))
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
  "Initialize the router."
  [req :- Request]
  (Router. req
           (-> req :uri split-path z/->zip atom)
           (-> req :request-method name)
           (atom (->EmptyResponse))))

(defmacro route
  "Entrypoint for routing. Returns a Ring handler.
  Calls respond."
  [& body]
  `(fn [req#]
     (binding [*router* (init-router req#)]
       ~@body
       (-> *router* :resp deref respond))))

(defmacro defroute
  "Define a routing Ring handler."
  [name & body]
  `(def ~name (route ~@body)))

(defmacro route'
  "Alternate entrypoint for routing. Returns a Ring
  handler. Does not call respond.

  Use with routes."
  [& body]
  `(fn [req#]
     (binding [*router* (init-router req#)]
       ~@body
       (-> *router* :resp deref))))

(defmacro defroute'
  "Define a routing Ring handler.

  Use with routes."
  [name & body]
  `(def ~name (route' ~@body)))

(defn' routes :- (Fn Any [Request])
  "Compose multiple routes. Returns a Ring handler.
  Calls respond."
  [& handlers :- [(Fn Any [Request])]]
  (fn [req]
    (-> (some (fn [handler]
                (let [resp (handler req)]
                  (when-not (empty-resp? resp)
                    resp)))
              handlers)
        respond)))





