(defproject om-next-tests "0.1.0-SNAPSHOT"
  :description "FIXME: write this!"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :min-lein-version "2.5.3"
  
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.170"]
                 [devcards "0.2.1-5"]
                 [sablono "0.6.2"]
                 ;; need to specify this for sablono
                 ;; when not using devcards
                 #_[cljsjs/react "0.14.3-0"]
                 #_[cljsjs/react-dom "0.14.3-1"]
                 #_[cljsjs/react-dom-server "0.14.3-0"]

                 [org.omcljs/om "1.0.0-alpha31"]]
  
  :plugins [[lein-figwheel "0.5.0-6"]
            [lein-cljsbuild "1.1.2" :exclusions [org.clojure/clojure]]]

  :clean-targets ^{:protect false} ["resources/public/js/compiled"
                                    "target"]
  
  :source-paths ["src"]

  :cljsbuild {
              :builds [{:id "devcards"
                        :source-paths ["src" "checkouts/om/src/main"]
                        :figwheel { :devcards true } ;; <- note this
                        :compiler { :main       "om-next-tests.core"
                                    :asset-path "js/compiled/devcards_out"
                                    :output-to  "resources/public/js/compiled/om_next_tests_devcards.js"
                                    :output-dir "resources/public/js/compiled/devcards_out"
                                    :source-map-timestamp true }}
                       {:id "dev"
                        :source-paths ["src" "checkouts/om/src/main"]
                        :figwheel true
                        :compiler {:main       "om-next-tests.core"
                                   :asset-path "js/compiled/out"
                                   :output-to  "resources/public/js/compiled/om_next_tests.js"
                                   :output-dir "resources/public/js/compiled/out"
                                   :source-map-timestamp true }}
                       {:id "prod"
                        :source-paths ["src"]
                        :compiler {:main       "om-next-tests.core"
                                   :asset-path "js/compiled/out"
                                   :output-to  "resources/public/js/compiled/om_next_tests.js"
                                   :optimizations :advanced}}
                       {:id "hosted"
                        :source-paths ["src"]
                        :compiler {:main       "om-next-tests.core"
                                   :devcards true
                                   :asset-path "."
                                   :output-to  "om_next_tests.js"
                                   :optimizations :advanced}}]}

  :figwheel {:css-dirs ["resources/public/css"]
             :nrepl-port 7888
             :repl true
             :nrepl-middleware  ["cider.nrepl/cider-middleware"
                                 "cemerick.piggieback/wrap-cljs-repl"]
             })
