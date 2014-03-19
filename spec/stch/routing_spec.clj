(ns stch.routing-spec
  (:use [speclj.core]
        [stch routing response]
        [stch.routing.parse]
        [stch.schema :only [with-fn-validation]]
        [stch.util :only [with-private-fns]])
  (:require [stch.zipper :as z]))

(defn req
  [uri method & {:keys [domain scheme]
                 :or {domain "example.org"
                      scheme :http}}]
  {:uri uri
   :request-method method
   :server-name domain
   :scheme scheme
   :headers {}})

(defn api-req [uri method]
  (req uri method
       :domain "api.example.org"
       :scheme :https))

(def faqs
  {"how-to-post-comment" "How to post a comment"
   "how-to-remove-comment" "How to remove a comment"})

(def old-or-new #{"old-path" "new-path"})

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
  (path "admin"
    (path "user"
      (guard false "You shall not pass!"))
    (path "super-secret"
      (guard false))
    "Welcome to the admin portal!")
  (not-traversed
    (path "admin"
      (path "posts")))
  (path "faq"
    (pred faqs [faq]
      (str "FAQ: " faq)))
  (pred old-or-new [choice]
    (str "You chose: " choice))
  (path "about"
    (method :ANY "This is my blog."))
  (path "black-hole"
    (terminate "You got sucked in."))
  (path "zipcode"
    (param [#"[0-9]{5}" identity] [zip]
      (str "Your zipcode is: " zip)))
  (path "req" (request))
  (path "url" (url))
  (path "params" (params))
  (path "lookup-param" (lookup-param :type))
  (path "headers" (headers))
  (path "lookup-header" (lookup-header "Accept-Language"))
  (path "body" (body))
  (path "nil-resp" nil))

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

(def uuid "64dbe8a0-4cd7-11e3-8f96-0800200c9a66")

(describe "Routing"
  (around [it]
    (with-fn-validation (it)))
  (it "index"
    (should= (ok "hello world")
             (handler (req "/" :get))))
  (context "path"
    (it "single segment"
      (should= (ok "Here's my blog.")
               (handler (req "/blog" :get))))
    (context "param"
      (it "int"
        (should= (ok "Post #1")
                 (handler (req "/blog/1" :get))))
      (context "nested path"
        (context "method: GET"
          (it "all segments consumed"
            (should= (ok "Get comments for post with id: 1")
                     (handler (req "/blog/1/comments" :get))))
          (it "truncate unconsumed segments"
            (should= (ok "Get comments for post with id: 1")
                     (handler (req "/blog/1/comments/35" :get)))))
        (context "method: POST"
          (it "all segments consumed"
            (should= (ok "Saved comment for post: 1")
                     (handler (req "/blog/1/comments" :post))))
          (it "unconsumed segments: not found"
            (should= (not-found)
                     (handler (req "/blog/1/comments/35" :post))))))
      (it "date"
        (should= (ok "Get blog post by date: #inst \"2013-01-01T00:00:00.000-00:00\"")
                 (handler (req "/blog/2013-01-01" :get))))
      (it "uuid"
        (should= (ok (str "Get blog post by uuid: " uuid))
                 (handler
                   (req (str "/blog/" uuid) :get))))
      (it "slug"
        (should= (ok "Get blog post by slug: clojure-101")
                 (handler (req "/blog/clojure-101" :get))))))
  (it "not found"
    (should= (not-found)
             (handler (req "/foo" :get))))
  (it "not-traversed"
    (should= (not-found)
             (handler (req "/admin/posts" :get))))
  (context "pred"
    (it "map"
      (should= (ok "FAQ: How to post a comment")
               (handler (req "/faq/how-to-post-comment" :get))))
    (it "set"
      (should= (ok "You chose: old-path")
               (handler (req "/old-path" :get)))))
  (context "guard"
    (it "default message"
      (should= (forbidden)
               (handler (req "/admin/super-secret" :get))))
    (it "custom message"
      (should= (text-response 403 "You shall not pass!")
               (handler (req "/admin/user" :get)))))
  (context "domain"
    (it "index"
      (should= (forbidden)
               (handler (api-req "/" :get))))
    (it "json"
      (should= (->json posts)
               (handler (api-req "/blog" :get)))))
  (context "method: ANY"
    (it "GET"
      (should= (ok "This is my blog.")
               (handler (req "/about" :get))))
    (it "POST"
      (should= (ok "This is my blog.")
               (handler (req "/about" :post))))
    (it "PUT"
      (should= (ok "This is my blog.")
               (handler (req "/about" :put))))
    (it "DELETE"
      (should= (ok "This is my blog.")
               (handler (req "/about" :delete))))
    (it "HEAD"
      (should= (ok "This is my blog.")
               (handler (req "/about" :head)))))
  (it "terminate"
    (should= (ok "You got sucked in.")
             (handler (req "/black-hole/billy" :get))))
  (it "custom parse/formatter"
    (should= (ok "Your zipcode is: 90210")
             (handler (req "/zipcode/90210" :get))))
  (it "nil response"
    (should= (text-response 204 "")
             (handler (req "/nil-resp" :get))))
  (context "routes"
    (it "frontend"
      (should= (ok "Here are some useful links.")
               (mysite
                 (req "/links" :get
                      :domain "mysite.org"))))
    (it "backend"
      (should= (->json links)
               (mysite
                 (req "/links" :get
                      :domain "admin.mysite.org"))))))

(defn test-pf [parsers p-type segment]
  (let [[parser formatter]
        (p-type parsers)
        matches (re-matches parser segment)]
    (when matches
      (formatter matches))))

(describe "parsers"
  (around [it]
    (with-fn-validation (it)))
  (it "int"
    (should= 1234
             (test-pf parsers :int "1234")))
  (it "date"
    (should= #inst "2014-01-01T00:00:00.000-00:00"
             (test-pf parsers :date "2014-01-01")))
  (it "uuid"
    (should= (java.util.UUID/fromString uuid)
             (test-pf parsers :uuid uuid)))
  (it "slug"
    (should= "some-article"
             (test-pf parsers :slug "some-article"))))

(describe "Request"
  (around [it]
    (with-fn-validation (it)))
  (it "request"
    (should= (->json (req "/req" :get))
             (handler (req "/req" :get))))
  (it "url"
    (should= (ok "/url")
             (handler (req "/url" :get))))
  (it "params"
    (should= (->json {:type "json"})
             (handler
               (-> (req "/params" :get)
                   (assoc :params {:type "json"})))))
  (it "lookup-param"
    (should= (ok "json")
             (handler
               (-> (req "/lookup-param" :get)
                   (assoc :params {:type "json"})))))
  (it "headers"
    (should= (->json {"Accept-Language" "en-US"})
             (handler
               (-> (req "/headers" :get)
                   (assoc :headers {"Accept-Language" "en-US"})))))
  (it "lookup-header"
    (should= (ok "en-US")
             (handler
               (-> (req "/lookup-header" :get)
                   (assoc :headers {"Accept-Language" "en-US"})))))
  (it "body"
    (should= (ok "name=Billy")
             (handler
               (-> (req "/body" :get)
                   (assoc :body "name=Billy"))))))

(describe "Routing internals"
  (around [it]
    (with-fn-validation (it)))
  (context "split-path"
    (with-private-fns [stch.routing [split-path]]
      (list
        (it "/"
          (should= '()
                   (split-path "/")))
        (it "/blog"
          (should= '("blog")
                   (split-path "/blog")))
        (it "/blog/"
          (should= '("blog")
                   (split-path "/blog/")))
        (it "/blog/1234"
          (should= '("blog" "1234")
                   (split-path "/blog/1234")))))))

(describe "respond"
  (it "EmptyResponse"
    (should= (not-found)
             (respond (->EmptyResponse))))
  (it "Response"
    (should= (->Response 200 {} "OK")
             (respond (->Response 200 {} "OK"))))
  (it "nil"
    (should= (text-response 204 "")
             (respond nil)))
  (it "String"
    (should= (ok "OK")
             (respond "OK")))
  (context "APersistentMap"
    (it "JSON"
      (should= (->json {:name "Billy" :age 35})
               (respond {:name "Billy" :age 35})))
    (it "EDN"
      (should= (->edn {:name "Billy" :age 35})
               (with-edn-formatting
                 (respond {:name "Billy" :age 35})))))
  (context "Sequential"
    (it "JSON"
      (should= (->json [:Billy "Bobby"])
               (respond [:Billy "Bobby"])))
    (it "EDN"
      (should= (->edn [:Billy "Bobby"])
               (with-edn-formatting
                 (respond [:Billy "Bobby"])))))
  (context "IPersistentSet"
    (it "JSON"
      (should= (->json #{:Billy "Bobby"})
               (respond #{:Billy "Bobby"})))
    (it "EDN"
      (should= (->edn #{:Billy "Bobby"})
               (with-edn-formatting
                 (respond #{:Billy "Bobby"})))))
  (it "IDeref"
    (should= (ok "Billy")
             (respond (atom "Billy"))))
  (it "Object"
    (should= (ok "3")
             (respond 3))))


















