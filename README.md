# stch.routing

Ring-compatible HTTP routing library based on ideas from:

1. [Bullet PHP](http://bulletphp.com)
2. [Happstack](http://happstack.com)

The request path is split into segments, and each segment is matched until either the entire path has been consumed or all segment handlers have been exhausted.  An unconsumed path returns a 404 response, while a fully consumed path with a return value of nil returns a 204 response, "No Content".

Clojure collection types (map, vector, set, list, seq) returned by a route will automatically be JSON encoded.  The encoding for these types can be changed to EDN, if desired, by wrapping handler calls with the with-edn-formatting macro.  See the examples section below for more information.

To return a full response (status, headers, body), it is suggested you use one of the built-in response functions (e.g., ok, not-found).  If you need more control, you can create a Response record.  You cannot return a hash-map with keys: status, headers, and body, since hash-maps are automatically JSON encoded.

[Chesire](https://github.com/dakrone/cheshire) is used for JSON encoding.

## Installation

Add the following to your project dependencies:

```clojure
[stch-library/routing "0.1.2"]
```

## API Documentation

http://stch-library.github.io/routing

Note: This library uses [stch.schema](https://github.com/stch-library/schema). Please refer to that project page for more information regarding type annotations and their meaning.

## Example site

Check out a working example in examples/playground.

## How to use

```clojure
(use 'stch.routing 'stch.response)

(def faqs
  {"how-to-post-comment" "How to post a comment"
   "how-to-remove-comment" "How to remove a comment"})

(def old-or-new #{"old-path" "new-path"})

(def posts [{:id 1 :content "Post #1"}
            {:id 2 :content "Post #2"}])

(defn lookup-post [id]
  (some #(when (= (:id %) id) %) posts))

(def public-dir "resources/public")

(defroute handler
  (static #{"images" "css" "js"} {:root public-dir})
  (scheme :https
    (domain "api.example.org"
      (index (forbidden))
      (path "blog" posts)))
  (index "hello world")
  (path "blog"
    (method :GET "Here's my blog.")
    (param :int [id]
      (let [post (lookup-post id)]
        (path "comments"
          (truncate
            (method :GET
              (str "Get comments for post with id: " id)))
          (method :POST (str "Saved comment for post: " id)))
        (str (:content post))))
    (param :date [date]
      (str "Get blog post by date: " (pr-str date)))
    (param :uuid [uuid]
      (str "Get blog post by uuid: " uuid))
    (param :slug [slug]
      (str "Get blog post by slug: " slug)))
  (path "faq"
    (pred faqs [faq]
      (str "FAQ: " faq)))
  (pred old-or-new [choice]
    (str "You chose: " choice))
  (path "admin"
    (path "user"
      (guard false "You shall not pass!"))
    "Welcome to the admin portal!")
  (path "black-hole"
    (terminate "You got sucked in."))
  (path "nil-resp" nil))

(defn req
  [uri method & {:keys [domain scheme]
                 :or {domain "example.org"
                      scheme "http"}}]
  {:uri uri
   :request-method method
   :server-name domain
   :scheme scheme
   :headers {}})

(handler (req "/images/clojure-icon.gif" :get))
; Will return gif file if resources/public/images/clojure-icon.gif exists

(handler (req "/css/style.css" :get))
; Will return css file if resources/public/css/style.css exists

(handler (req "/js/script.js" :get))
; Will return js file if resources/public/js/script.js exists

(handler (req "/" :get))
; #stch.response.Response{:status 200, :headers {"Content-Type" "text/html"}, :body "hello world"}

(handler (req "/blog" :get))
; #stch.response.Response{:status 200, :headers {"Content-Type" "text/html"}, :body "Here's my blog."}

(handler (req "/blog/1/comments" :post))
; #stch.response.Response{:status 200, :headers {"Content-Type" "text/html"}, :body "Saved comment for post: 1"}

(handler (req "/blog/1" :get))
; #stch.response.Response{:status 200, :headers {"Content-Type" "text/html"}, :body "Post #1"}

(handler (req "/blog/1/comments" :get))
; #stch.response.Response{:status 200, :headers {"Content-Type" "text/html"}, :body "Get comments for post with id: 1"}

(handler (req "/blog/2013-01-01" :get))
; #stch.response.Response{:status 200, :headers {"Content-Type" "text/html"}, :body "Get blog post by date: #inst \"2013-01-01T00:00:00.000-00:00\""}

(handler (req "/blog/clojure-101" :get))
; #stch.response.Response{:status 200, :headers {"Content-Type" "text/html"}, :body "Get blog post by slug: clojure-101"}

(handler (req "/blog/64dbe8a0-4cd7-11e3-8f96-0800200c9a66" :get))
; #stch.response.Response{:status 200, :headers {"Content-Type" "text/html"}, :body "Get blog post by uuid: 64dbe8a0-4cd7-11e3-8f96-0800200c9a66"}

(handler (req "/faq/how-to-post-comment" :get))
; #stch.response.Response{:status 200, :headers {"Content-Type" "text/html"}, :body "FAQ: How to post a comment"}

(handler (req "/old-path" :get))
; #stch.response.Response{:status 200, :headers {"Content-Type" "text/html"}, :body "You chose: old-path"}

(handler (req "/new-path" :get))
; #stch.response.Response{:status 200, :headers {"Content-Type" "text/html"}, :body "You chose: new-path"}

(handler (req "/admin" :get))
; #stch.response.Response{:status 200, :headers {"Content-Type" "text/html"}, :body "Welcome to the admin portal!"}

(handler (req "/admin/user" :get))
; #stch.response.Response{:status 403, :headers {"Content-Type" "text/plain"}, :body "You shall not pass!"}

(handler (req "/blog" :get :scheme :https :domain "api.example.org"))
; #stch.response.Response{:status 200, :headers {"Content-Type" "application/json"}, :body "[{\"content\":\"Post #1\",\"id\":1},{\"content\":\"Post #2\",\"id\":2}]"}

(handler (req "/black-hole/just-visiting" :get))
; #stch.response.Response{:status 200, :headers {"Content-Type" "text/html"}, :body "You got sucked in."}

(handler (req "/nil-resp" :get))
; #stch.response.Response{:status 204, :headers {"Content-Type" "text/plain"}, :body ""}

(handler (req "/blogz" :get))
; #stch.response.Response{:status 404, :headers {"Content-Type" "text/plain"}, :body "Not Found"}
```

Routes can be composed as well.  In this scenario we define our routes with route' or defroute' (notice the single quote), and combine them with routes.

```clojure
(defroute' frontend
  (domain "mysite.org"
    (path "links"
      "Here are some useful links.")))

(def links [{:id 1 :href "clojure.org"}
            {:id 2 :href "clojuredocs.org"}])

(defroute' backend
  (domain "admin.mysite.org"
    (path "links"
      (method :GET links)
      (method :POST "Link saved."))))

(def mysite
  (routes frontend backend))

(mysite (req "/links" :get :domain "mysite.org"))
; #stch.response.Response{:status 200, :headers {"Content-Type" "text/html"}, :body "Here are some useful links."}

(mysite (req "/links" :get :domain "admin.mysite.org"))
; #stch.response.Response{:status 200, :headers {"Content-Type" "application/json"}, :body "[{\"href\":\"clojure.org\",\"id\":1},{\"href\":\"clojuredocs.org\",\"id\":2}]"}
```

You can create your own param handlers. Pass a regex pattern, parser pair instead of a keyword as the first arg to param.  If the regex pattern matches the current path segment, the parser fn will be called on the segment. You can use the parser to convert the segment from a string to a specific type (e.g., Double).

```clojure
(defroute custom
  (path "zipcode"
    (param [#"[0-9]{5}" identity] [zip]
      (str "Your zipcode is: " zip)))
  (path "amount"
    (param [#"[0-9]+\.[0-9]{2}" #(Double/valueOf %)] [amount]
      (str "The amount is: $" amount))))

(custom (req "/zipcode/90210" :get))
; #stch.response.Response{:status 200, :headers {"Content-Type" "text/html"}, :body "Your zipcode is: 90210"}

(custom (req "/amount/201.35" :get))
; #stch.response.Response{:status 200, :headers {"Content-Type" "text/html"}, :body "The amount is: $201.35"}
```

There are a few built-in response functions in stch.response for the most common response types.

```clojure
(ok "Success")
; #stch.response.Response{:status 200, :headers {"Content-Type" "text/html"}, :body "Success"}

(redirect "/homepage")
; #stch.response.Response{:status 302, :headers {"Location" "/homepage"}, :body ""}

(bad-request)
; #stch.response.Response{:status 400, :headers {"Content-Type" "text/plain"}, :body "Bad Request"}

(bad-request "What were you thinking?")
; #stch.response.Response{:status 400, :headers {"Content-Type" "text/plain"}, :body "What were you thinking?"}

(forbidden)
; #stch.response.Response{:status 403, :headers {"Content-Type" "text/plain"}, :body "Forbidden"}

(forbidden "Access denied")
; #stch.response.Response{:status 403, :headers {"Content-Type" "text/plain"}, :body "Access denied"}

(not-found)
; #stch.response.Response{:status 404, :headers {"Content-Type" "text/plain"}, :body "Not Found"}

(not-found "It just isn't here")
; #stch.response.Response{:status 404, :headers {"Content-Type" "text/plain"}, :body "It just isn't here"}

(method-not-allowed)
; #stch.response.Response{:status 405, :headers {"Content-Type" "text/plain"}, :body "Method Not Allowed"}

(method-not-allowed "Not sure what to do with that.")
; #stch.response.Response{:status 405, :headers {"Content-Type" "text/plain"}, :body "Not sure what to do with that."}

(def users
  [{:id 1 :name "Billy"}
   {:id 2 :name "Joey"}])

(->json users)
; #stch.response.Response{:status 200, :headers {"Content-Type" "application/json"}, :body "[{\"name\":\"Billy\",\"id\":1},{\"name\":\"Joey\",\"id\":2}]"}

(->edn users)
; #stch.response.Response{:status 200, :headers {"Content-Type" "application/edn"}, :body "[{:name \"Billy\", :id 1} {:name \"Joey\", :id 2}]"}
```

To automatically convert hash-maps and sequential types to EDN, instead of JSON, use the with-edn-formatting macro.

```clojure
(defroute edn-api
  (path "users" users))

(with-edn-formatting
  (edn-api (req "/users" :get)))

; #stch.response.Response{:status 200, :headers {"Content-Type" "application/edn"}, :body "[{:name \"Billy\", :id 1} {:name \"Joey\", :id 2}]"}
```

Some convenience fns exist for getting at the underlining request.

```clojure
(defroute helpers
  (path "req" (request))
  (path "url" (url))
  (path "params" (params))
  (path "lookup-param" (lookup-param :type))
  (path "headers" (headers))
  (path "lookup-header" (lookup-header "Accept-Language"))
  (path "body" (body)))

(helpers (req "/req" :get))
; #stch.response.Response{:status 200, :headers {"Content-Type" "application/json"}, :body "{\"uri\":\"/req\",\"request-method\":\"get\",\"server-name\":\"example.org\",\"scheme\":\"http\",\"headers\":{}}"}

(helpers (req "/url" :get))
; #stch.response.Response{:status 200, :headers {"Content-Type" "text/html"}, :body "/url"}

(helpers (-> (req "/params" :get)
             (assoc :params {:type "json"})))
; #stch.response.Response{:status 200, :headers {"Content-Type" "application/json"}, :body "{\"type\":\"json\"}"}

(helpers (-> (req "/lookup-param" :get)
             (assoc :params {:type "json"})))
; #stch.response.Response{:status 200, :headers {"Content-Type" "text/html"}, :body "json"}

(helpers (-> (req "/headers" :get)
             (assoc :headers {"Accept-Language" "en-US"})))
; #stch.response.Response{:status 200, :headers {"Content-Type" "application/json"}, :body "{\"Accept-Language\":\"en-US\"}"}

(helpers (-> (req "/lookup-header" :get)
             (assoc :headers {"Accept-Language" "en-US"})))
; #stch.response.Response{:status 200, :headers {"Content-Type" "text/html"}, :body "en-US"}
```

The following types are handled specially when passed to the respond protocol method (see [stch.response](https://github.com/stch-library/routing/blob/master/src/stch/response.clj#L145)):

1. EmptyResponse
2. Response
3. nil
4. String
5. APersistentMap
6. Sequential
7. IPersistentSet
8. IDeref
9. File
10. InputStream

For all other types (Number, Boolean, etc.), the object is passed to pr-str, and that value is passed to the ok fn.

## Unit-tests

Run "lein spec"











