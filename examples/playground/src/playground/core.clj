(ns playground.core
  "Main entrypoint."
  (:use [org.httpkit.server]
        [ring.middleware params]
        [stch routing response])
  (:gen-class))

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
  (path "api"
    (path "blog" posts))
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
        (:content post)))
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

(defn site []
  (-> handler
      wrap-params))

(defn -main [port]
  (println (str "Starting server on port " port))
  (run-server (site) {:port (Integer/parseInt port)}))











