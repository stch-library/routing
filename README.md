# stch.routing

Ring-compatible HTTP routing library based on ideas from:

1. http://bulletphp.com
2. http://happstack.com

The request path is split into segments, and each segment is matched until either the entire path has been consumed or all segment handlers have been exhausted.  An unconsumed path returns a 404 response, while a fully consumed path with a return value of nil returns a 204 response, "No Content".

A clojure hash-map or sequential collection type (lists, vectors, etc.) returned by a route will automatically be JSON encoded.  The encoding for these types can be changed to EDN, if desired, by wrapping routes with the with-edn-formatting macro.

To return a full response (status, headers, body), it is suggested you use one of the built-in response functions (e.g., ok, not-found).  If you need more control, you can create a Response record.  You cannot return a hash-map with keys: status, headers, and body, since hash-maps are automatically JSON encoded.

## How to use

Add the following to your project dependencies:

```Clojure
[stch-library/routing "0.1.0"]
```

## API Documentation

http://stch-library.github.io/routing

## Examples

```Clojure
(use 'stch.routing)

(def faqs
  {"how-to-post-comment" "How to post a comment"
   "how-to-remove-comment" "How to remove a comment"})

(def posts [{:id 1 :content "Post #1"}
            {:id 2 :content "Post #2"}])

(defn lookup-post [id]
  (some #(when (= (:id %) id) %) posts))

(defroute handler
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
  (path "admin"
    (path "user"
      (guard false "You shall not pass!"))
    "Welcome to the admin portal!"))
```


















